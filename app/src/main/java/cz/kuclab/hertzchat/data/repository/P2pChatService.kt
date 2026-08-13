package cz.kuclab.hertzchat.data.repository

import android.util.Base64
import cz.kuclab.hertzchat.crypto.EncryptedEnvelope
import cz.kuclab.hertzchat.crypto.IdentityKeyManager
import cz.kuclab.hertzchat.crypto.MessageCipher
import cz.kuclab.hertzchat.crypto.RoomSignalProtocolStore
import cz.kuclab.hertzchat.crypto.toPreKeyBundle
import cz.kuclab.hertzchat.crypto.toWire
import cz.kuclab.hertzchat.data.db.ContactDao
import cz.kuclab.hertzchat.data.db.ContactEntity
import cz.kuclab.hertzchat.data.db.DeliveryState
import cz.kuclab.hertzchat.data.db.MessageDao
import cz.kuclab.hertzchat.data.db.MessageEntity
import cz.kuclab.hertzchat.data.db.MessageType
import cz.kuclab.hertzchat.data.model.ChatPayload
import cz.kuclab.hertzchat.data.model.PayloadKind
import cz.kuclab.hertzchat.media.MediaCrypto
import cz.kuclab.hertzchat.media.MediaStorage
import cz.kuclab.hertzchat.network.tor.FriendRequestPayload
import cz.kuclab.hertzchat.network.tor.FriendResponsePayload
import cz.kuclab.hertzchat.network.tor.HertzId
import cz.kuclab.hertzchat.network.tor.TorConnection
import cz.kuclab.hertzchat.network.tor.TorTransport
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.Socket
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.briarproject.onionwrapper.TorWrapper

// Wire framing prefix byte for the length-prefixed frames sent over a TorConnection.
private const val FRAME_HELLO: Byte = 0
private const val FRAME_MESSAGE: Byte = 1
private const val FRAME_PREKEY: Byte = 2
private const val FRAME_MEDIA_CHUNK: Byte = 3
private const val FRAME_FRIEND_REQUEST: Byte = 4
private const val FRAME_FRIEND_RESPONSE: Byte = 5

private const val RETRY_INTERVAL_MS = 60_000L

data class IncomingFriendRequest(val contactId: String, val nickname: String, val request: FriendRequestPayload)

sealed interface ChatServiceEvent {
    data class FriendRequestReceived(val request: IncomingFriendRequest) : ChatServiceEvent
    data class MessageReceived(val contactId: String, val message: MessageEntity) : ChatServiceEvent
}

/**
 * Orchestrates the whole P2P pipeline: Tor onion services are the transport
 * (no server of ours or anyone's is ever involved in finding a peer or
 * carrying a message/media byte), and the Signal Protocol session per
 * contact is the end-to-end encryption. A message never exists in
 * plaintext anywhere except on the two devices party to the conversation.
 */
@Singleton
class P2pChatService @Inject constructor(
    private val identityKeyManager: IdentityKeyManager,
    private val protocolStore: RoomSignalProtocolStore,
    private val contactDao: ContactDao,
    private val messageDao: MessageDao,
    private val mediaStorage: MediaStorage,
    private val torTransport: TorTransport,
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val connections = mutableMapOf<String, TorConnection>()
    private val ciphers = mutableMapOf<String, MessageCipher>()

    val torState: StateFlow<TorWrapper.TorState> get() = torTransport.state
    val bootstrapPercent: StateFlow<Int> get() = torTransport.bootstrapPercent
    val onionAddress: StateFlow<String?> get() = torTransport.onionAddress
    val torError: StateFlow<String?> get() = torTransport.error

    /** Retries starting the Tor client after a previous failure (e.g. no internet at the time). */
    fun retryTor() {
        torTransport.retry(identityKeyManager.torPrivateKey) { newKey -> identityKeyManager.torPrivateKey = newKey }
    }

    private val _incomingRequests = MutableStateFlow<List<IncomingFriendRequest>>(emptyList())
    val incomingRequests: StateFlow<List<IncomingFriendRequest>> = _incomingRequests

    private val _events = MutableSharedFlow<ChatServiceEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ChatServiceEvent> = _events

    private var blockedContactIds: Set<String> = emptySet()
    private var started = false

    private data class IncomingTransfer(
        val contactId: String,
        val messageId: String,
        val kind: PayloadKind,
        val key: ByteArray,
        val nonceSalt: ByteArray,
        val chunkCount: Int,
        val mimeType: String,
        val durationMs: Long?,
        val outputFile: File,
        val out: BufferedOutputStream,
        var received: Int = 0,
    )

    private val incomingTransfers = mutableMapOf<String, IncomingTransfer>()

    fun start() {
        if (started) return
        started = true
        scope.launch { contactDao.observeBlocked().collect { blocked -> blockedContactIds = blocked.map { it.contactId }.toSet() } }
        torTransport.start(identityKeyManager.torPrivateKey) { newKey -> identityKeyManager.torPrivateKey = newKey }
        scope.launch {
            torTransport.onionAddress.collect { address -> if (address != null) identityKeyManager.onionAddress = address }
        }
        scope.launch {
            torTransport.incomingConnections.collect { socket -> handleIncomingSocket(socket) }
        }
        scope.launch { retryPendingLoop() }
    }

    fun stop() {
        torTransport.stop()
        connections.values.forEach { it.close() }
        connections.clear()
        started = false
    }

    private fun cipherFor(contactId: String): MessageCipher =
        ciphers.getOrPut(contactId) { MessageCipher(protocolStore, contactId) }

    // --- Identity / friend requests ---

    /** Null until Tor has published our onion service - there's no usable Hertz ID to share before that. */
    fun myHertzId(): HertzId? {
        val address = torTransport.onionAddress.value ?: return null
        return HertzId(
            contactId = identityKeyManager.contactId(),
            nickname = identityKeyManager.nickname,
            identityKeyBase64 = Base64.encodeToString(identityKeyManager.identityKeyPair().publicKey.serialize(), Base64.NO_WRAP),
            onionAddress = address,
        )
    }

    /** Result carries a human-readable reason on failure, since "nothing happened" after scanning a QR code is a bad silent failure mode. */
    suspend fun sendFriendRequest(target: HertzId): Result<Unit> = withContext(Dispatchers.IO) {
        val me = myHertzId() ?: return@withContext Result.failure(IllegalStateException("Tor síť ještě není připravená - zkus to za chvíli znovu"))
        if (target.onionAddress.isBlank()) return@withContext Result.failure(IllegalStateException("Neplatné Hertz ID (chybí adresa)"))
        runCatching {
            val payload = FriendRequestPayload(
                nickname = me.nickname,
                identityKeyBase64 = me.identityKeyBase64,
                onionAddress = me.onionAddress,
                preKeyBundle = identityKeyManager.currentPreKeyBundle().toWire(),
            )
            val connection = dialAndRegister(target.contactId, target.onionAddress)
            connection.send(frame(FRAME_FRIEND_REQUEST, json.encodeToString(payload).encodeToByteArray()))
        }
    }

    fun respondFriendRequest(request: IncomingFriendRequest, accept: Boolean) {
        _incomingRequests.value = _incomingRequests.value.filterNot { it.contactId == request.contactId }
        if (accept) {
            addTrustedContact(request.contactId, request.nickname, request.request.identityKeyBase64, request.request.onionAddress)
            cipherFor(request.contactId).establishSessionFromBundle(request.request.preKeyBundle.toPreKeyBundle())
            mediaStorage.selfAvatarFile().takeIf { it.exists() }?.let { sendAvatarTo(request.contactId, it.readBytes()) }
        }
        scope.launch {
            val me = myHertzId() ?: return@launch
            val response = FriendResponsePayload(
                accepted = accept,
                nickname = me.nickname,
                identityKeyBase64 = me.identityKeyBase64,
                onionAddress = me.onionAddress,
            )
            runCatching {
                val connection = dialAndRegister(request.contactId, request.request.onionAddress)
                connection.send(frame(FRAME_FRIEND_RESPONSE, json.encodeToString(response).encodeToByteArray()))
            }
        }
    }

    private fun addTrustedContact(contactId: String, nickname: String, identityKeyBase64: String, onionAddress: String) {
        scope.launch {
            contactDao.upsert(
                ContactEntity(
                    contactId = contactId,
                    nickname = nickname,
                    identityKeyBytes = Base64.decode(identityKeyBase64, Base64.NO_WRAP),
                    onionAddress = onionAddress,
                    addedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    // --- Messaging ---

    fun sendText(contactId: String, text: String) {
        val payload = ChatPayload(
            messageId = UUID.randomUUID().toString(),
            sentAt = System.currentTimeMillis(),
            kind = PayloadKind.TEXT,
            text = text,
        )
        scope.launch {
            messageDao.upsert(
                MessageEntity(
                    messageId = payload.messageId,
                    contactId = contactId,
                    fromMe = true,
                    type = MessageType.TEXT,
                    text = text,
                    timestamp = payload.sentAt,
                    deliveryState = DeliveryState.PENDING,
                ),
            )
            val delivered = trySendPayload(contactId, payload)
            messageDao.updateState(payload.messageId, if (delivered) DeliveryState.SENT else DeliveryState.PENDING)
        }
    }

    /** Returns true if the envelope made it onto a connection successfully - not a delivery receipt, just "left this device". */
    private suspend fun trySendPayload(contactId: String, payload: ChatPayload): Boolean {
        val contact = contactDao.find(contactId) ?: return false
        val envelope = cipherFor(contactId).encrypt(json.encodeToString(payload).encodeToByteArray())
        val frameType = if (envelope.isPreKeyMessage) FRAME_PREKEY else FRAME_MESSAGE
        return sendFrameTo(contactId, contact.onionAddress, frame(frameType, envelope.ciphertext))
    }

    private suspend fun sendFrameTo(contactId: String, onionAddress: String, bytes: ByteArray): Boolean = runCatching {
        val connection = connections[contactId]?.takeIf { runCatching { it.send(bytes) }.isSuccess }
            ?: dialAndRegister(contactId, onionAddress).also { it.send(bytes) }
        connection
    }.isSuccess

    private suspend fun dialAndRegister(contactId: String, onionAddress: String): TorConnection = withContext(Dispatchers.IO) {
        val socket: Socket = torTransport.connectTo(onionAddress)
        val connection = TorConnection(socket)
        connection.send(frame(FRAME_HELLO, identityKeyManager.contactId().encodeToByteArray()))
        registerConnection(contactId, connection)
        scope.launch { readLoop(contactId, connection) }
        connection
    }

    private fun registerConnection(contactId: String, connection: TorConnection) {
        connections.put(contactId, connection)?.let { old -> if (old !== connection) old.close() }
    }

    // --- Media ---

    fun sendMedia(contactId: String, sourceBytes: ByteArray, mimeType: String, kind: PayloadKind, fileName: String?, durationMs: Long? = null) {
        val transferId = UUID.randomUUID()
        val key = MediaCrypto.generateKey()
        val nonceSalt = MediaCrypto.generateNonceSalt()
        val extension = mediaStorage.extensionFor(mimeType)
        val localCopy = mediaStorage.newOutgoingCopy(sourceBytes, extension)
        val messageId = UUID.randomUUID().toString()
        val chunkCount = (sourceBytes.size + MediaCrypto.CHUNK_SIZE - 1) / MediaCrypto.CHUNK_SIZE

        val control = ChatPayload(
            messageId = messageId,
            sentAt = System.currentTimeMillis(),
            kind = kind,
            mediaMimeType = mimeType,
            mediaFileName = fileName,
            mediaSizeBytes = sourceBytes.size.toLong(),
            mediaDurationMs = durationMs,
            mediaTransferId = transferId.toString(),
            mediaKeyBase64 = Base64.encodeToString(key, Base64.NO_WRAP),
            mediaNonceSaltBase64 = Base64.encodeToString(nonceSalt, Base64.NO_WRAP),
            mediaChunkCount = chunkCount,
        )

        scope.launch {
            messageDao.upsert(
                MessageEntity(
                    messageId = messageId,
                    contactId = contactId,
                    fromMe = true,
                    type = kind.toMessageType(),
                    mediaPath = localCopy.absolutePath,
                    mediaMimeType = mimeType,
                    mediaDurationMs = durationMs,
                    timestamp = control.sentAt,
                    deliveryState = DeliveryState.PENDING,
                ),
            )

            val contact = contactDao.find(contactId)
            val delivered = contact != null && runCatching {
                check(trySendPayload(contactId, control))
                var offset = 0
                var index = 0
                while (offset < sourceBytes.size) {
                    val end = minOf(offset + MediaCrypto.CHUNK_SIZE, sourceBytes.size)
                    val cipherChunk = MediaCrypto.encryptChunk(key, nonceSalt, index, sourceBytes.copyOfRange(offset, end))
                    check(sendFrameTo(contactId, contact.onionAddress, frameMediaChunk(transferId, index, cipherChunk)))
                    offset = end
                    index++
                }
            }.isSuccess
            messageDao.updateState(messageId, if (delivered) DeliveryState.SENT else DeliveryState.PENDING)
        }
    }

    // --- Avatar ---

    /** Saves the new avatar locally and pushes it to every existing contact. */
    fun updateMyAvatar(jpegBytes: ByteArray) {
        mediaStorage.selfAvatarFile().writeBytes(jpegBytes)
        scope.launch {
            contactDao.observeContacts().first().forEach { contact -> sendAvatarTo(contact.contactId, jpegBytes) }
        }
    }

    private fun sendAvatarTo(contactId: String, jpegBytes: ByteArray) {
        val transferId = UUID.randomUUID()
        val key = MediaCrypto.generateKey()
        val nonceSalt = MediaCrypto.generateNonceSalt()
        val chunkCount = (jpegBytes.size + MediaCrypto.CHUNK_SIZE - 1) / MediaCrypto.CHUNK_SIZE
        val control = ChatPayload(
            messageId = UUID.randomUUID().toString(),
            sentAt = System.currentTimeMillis(),
            kind = PayloadKind.AVATAR,
            mediaMimeType = "image/jpeg",
            mediaSizeBytes = jpegBytes.size.toLong(),
            mediaTransferId = transferId.toString(),
            mediaKeyBase64 = Base64.encodeToString(key, Base64.NO_WRAP),
            mediaNonceSaltBase64 = Base64.encodeToString(nonceSalt, Base64.NO_WRAP),
            mediaChunkCount = chunkCount,
        )
        scope.launch {
            val contact = contactDao.find(contactId) ?: return@launch
            runCatching {
                check(trySendPayload(contactId, control))
                var offset = 0
                var index = 0
                while (offset < jpegBytes.size) {
                    val end = minOf(offset + MediaCrypto.CHUNK_SIZE, jpegBytes.size)
                    val cipherChunk = MediaCrypto.encryptChunk(key, nonceSalt, index, jpegBytes.copyOfRange(offset, end))
                    check(sendFrameTo(contactId, contact.onionAddress, frameMediaChunk(transferId, index, cipherChunk)))
                    offset = end
                    index++
                }
            }
        }
    }

    private fun frameMediaChunk(transferId: UUID, chunkIndex: Int, ciphertext: ByteArray): ByteArray {
        val header = java.nio.ByteBuffer.allocate(1 + 16 + 4)
            .put(FRAME_MEDIA_CHUNK)
            .putLong(transferId.mostSignificantBits)
            .putLong(transferId.leastSignificantBits)
            .putInt(chunkIndex)
        return header.array() + ciphertext
    }

    private fun beginIncomingTransfer(contactId: String, payload: ChatPayload) {
        val transferId = payload.mediaTransferId ?: return
        val key = payload.mediaKeyBase64?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return
        val nonceSalt = payload.mediaNonceSaltBase64?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return
        val chunkCount = payload.mediaChunkCount ?: return
        val mimeType = payload.mediaMimeType ?: "application/octet-stream"
        val outputFile = if (payload.kind == PayloadKind.AVATAR) {
            mediaStorage.contactAvatarFile(contactId)
        } else {
            mediaStorage.fileFor(transferId, mediaStorage.extensionFor(mimeType))
        }

        incomingTransfers[transferId] = IncomingTransfer(
            contactId = contactId,
            messageId = payload.messageId,
            kind = payload.kind,
            key = key,
            nonceSalt = nonceSalt,
            chunkCount = chunkCount,
            mimeType = mimeType,
            durationMs = payload.mediaDurationMs,
            outputFile = outputFile,
            out = BufferedOutputStream(FileOutputStream(outputFile)),
        )
    }

    private fun onMediaChunkReceived(bytes: ByteArray) {
        val buffer = java.nio.ByteBuffer.wrap(bytes, 1, bytes.size - 1)
        val transferId = UUID(buffer.long, buffer.long).toString()
        val chunkIndex = buffer.int
        val ciphertext = bytes.copyOfRange(1 + 16 + 4, bytes.size)

        val transfer = incomingTransfers[transferId] ?: return
        val plaintext = runCatching { MediaCrypto.decryptChunk(transfer.key, transfer.nonceSalt, chunkIndex, ciphertext) }.getOrNull() ?: return
        transfer.out.write(plaintext)
        transfer.received++

        if (transfer.received >= transfer.chunkCount) {
            transfer.out.flush()
            transfer.out.close()
            incomingTransfers.remove(transferId)

            if (transfer.kind == PayloadKind.AVATAR) {
                scope.launch {
                    contactDao.find(transfer.contactId)?.let {
                        contactDao.update(it.copy(avatarPath = transfer.outputFile.absolutePath))
                    }
                }
                return
            }

            val entity = MessageEntity(
                messageId = transfer.messageId,
                contactId = transfer.contactId,
                fromMe = false,
                type = transfer.kind.toMessageType(),
                mediaPath = transfer.outputFile.absolutePath,
                mediaMimeType = transfer.mimeType,
                mediaDurationMs = transfer.durationMs,
                timestamp = System.currentTimeMillis(),
                deliveryState = DeliveryState.DELIVERED,
            )
            scope.launch {
                messageDao.upsert(entity)
                _events.tryEmit(ChatServiceEvent.MessageReceived(transfer.contactId, entity))
            }
        }
    }

    private fun PayloadKind.toMessageType(): MessageType = when (this) {
        PayloadKind.IMAGE -> MessageType.IMAGE
        PayloadKind.VIDEO -> MessageType.VIDEO
        PayloadKind.VOICE -> MessageType.VOICE
        else -> MessageType.FILE
    }

    // --- Retry queue for contacts who were unreachable ---

    private suspend fun retryPendingLoop() {
        while (started) {
            delay(RETRY_INTERVAL_MS)
            runCatching { retryPendingNow() }
        }
    }

    private suspend fun retryPendingNow() {
        val pending = messageDao.findAllPending()
        for (message in pending) {
            val contact = contactDao.find(message.contactId) ?: continue
            if (contact.contactId in blockedContactIds) continue
            val delivered = if (message.type == MessageType.TEXT) {
                trySendPayload(message.contactId, ChatPayload(message.messageId, message.timestamp, PayloadKind.TEXT, text = message.text))
            } else {
                val bytes = message.mediaPath?.let { runCatching { File(it).readBytes() }.getOrNull() }
                bytes != null && resendMedia(message, bytes, contact.onionAddress)
            }
            if (delivered) messageDao.updateState(message.messageId, DeliveryState.SENT)
        }
    }

    private suspend fun resendMedia(message: MessageEntity, sourceBytes: ByteArray, onionAddress: String): Boolean {
        // A retried media message reuses a fresh transfer id/key - the original attempt may have
        // partially landed on the peer's side, and re-keying is simpler and just as cheap as
        // trying to resume a specific byte offset.
        val transferId = UUID.randomUUID()
        val key = MediaCrypto.generateKey()
        val nonceSalt = MediaCrypto.generateNonceSalt()
        val chunkCount = (sourceBytes.size + MediaCrypto.CHUNK_SIZE - 1) / MediaCrypto.CHUNK_SIZE
        val control = ChatPayload(
            messageId = message.messageId,
            sentAt = message.timestamp,
            kind = when (message.type) {
                MessageType.IMAGE -> PayloadKind.IMAGE
                MessageType.VIDEO -> PayloadKind.VIDEO
                MessageType.VOICE -> PayloadKind.VOICE
                else -> PayloadKind.FILE
            },
            mediaMimeType = message.mediaMimeType,
            mediaSizeBytes = sourceBytes.size.toLong(),
            mediaDurationMs = message.mediaDurationMs,
            mediaTransferId = transferId.toString(),
            mediaKeyBase64 = Base64.encodeToString(key, Base64.NO_WRAP),
            mediaNonceSaltBase64 = Base64.encodeToString(nonceSalt, Base64.NO_WRAP),
            mediaChunkCount = chunkCount,
        )
        return runCatching {
            check(trySendPayload(message.contactId, control))
            var offset = 0
            var index = 0
            while (offset < sourceBytes.size) {
                val end = minOf(offset + MediaCrypto.CHUNK_SIZE, sourceBytes.size)
                val cipherChunk = MediaCrypto.encryptChunk(key, nonceSalt, index, sourceBytes.copyOfRange(offset, end))
                check(sendFrameTo(message.contactId, onionAddress, frameMediaChunk(transferId, index, cipherChunk)))
                offset = end
                index++
            }
        }.isSuccess
    }

    // --- Connection handling ---

    private fun handleIncomingSocket(socket: Socket) {
        scope.launch {
            val connection = TorConnection(socket)
            var fromContactId: String? = null
            try {
                while (true) {
                    val bytes = withContext(Dispatchers.IO) { connection.receive() }
                    if (bytes.isEmpty()) continue
                    when (bytes[0]) {
                        FRAME_HELLO -> {
                            fromContactId = bytes.copyOfRange(1, bytes.size).decodeToString()
                            fromContactId.takeIf { it !in blockedContactIds }?.let { registerConnection(it, connection) }
                        }
                        FRAME_MEDIA_CHUNK -> onMediaChunkReceived(bytes)
                        FRAME_FRIEND_REQUEST -> handleFriendRequestFrame(bytes)
                        FRAME_FRIEND_RESPONSE -> handleFriendResponseFrame(bytes)
                        FRAME_MESSAGE, FRAME_PREKEY -> {
                            val senderId = fromContactId ?: continue
                            if (senderId in blockedContactIds) continue
                            onEnvelopeReceived(senderId, bytes)
                        }
                    }
                }
            } catch (_: Exception) {
                // connection closed by peer, or network error - normal, nothing to do
            } finally {
                connection.close()
            }
        }
    }

    private suspend fun readLoop(contactId: String, connection: TorConnection) {
        try {
            while (true) {
                val bytes = withContext(Dispatchers.IO) { connection.receive() }
                if (bytes.isEmpty()) continue
                when (bytes[0]) {
                    FRAME_MEDIA_CHUNK -> onMediaChunkReceived(bytes)
                    FRAME_FRIEND_RESPONSE -> handleFriendResponseFrame(bytes)
                    FRAME_MESSAGE, FRAME_PREKEY -> if (contactId !in blockedContactIds) onEnvelopeReceived(contactId, bytes)
                    else -> Unit
                }
            }
        } catch (_: Exception) {
            // normal on connection close
        } finally {
            if (connections[contactId] === connection) connections.remove(contactId)
            connection.close()
        }
    }

    private fun onEnvelopeReceived(contactId: String, bytes: ByteArray) {
        val envelope = EncryptedEnvelope(isPreKeyMessage = bytes[0] == FRAME_PREKEY, ciphertext = bytes.copyOfRange(1, bytes.size))
        val plaintext = runCatching { cipherFor(contactId).decrypt(envelope) }.getOrNull() ?: return
        val payload = runCatching { json.decodeFromString(ChatPayload.serializer(), plaintext.decodeToString()) }.getOrNull() ?: return

        if (payload.kind == PayloadKind.IMAGE || payload.kind == PayloadKind.VIDEO ||
            payload.kind == PayloadKind.VOICE || payload.kind == PayloadKind.AVATAR
        ) {
            beginIncomingTransfer(contactId, payload)
            return
        }
        if (payload.kind != PayloadKind.TEXT) return

        val entity = MessageEntity(
            messageId = payload.messageId,
            contactId = contactId,
            fromMe = false,
            type = MessageType.TEXT,
            text = payload.text,
            timestamp = payload.sentAt,
            deliveryState = DeliveryState.DELIVERED,
        )
        scope.launch {
            messageDao.upsert(entity)
            _events.tryEmit(ChatServiceEvent.MessageReceived(contactId, entity))
        }
    }

    private fun handleFriendRequestFrame(bytes: ByteArray) {
        val request = runCatching {
            json.decodeFromString(FriendRequestPayload.serializer(), bytes.copyOfRange(1, bytes.size).decodeToString())
        }.getOrNull() ?: return
        val senderContactId = identityKeyManager.contactIdFor(Base64.decode(request.identityKeyBase64, Base64.NO_WRAP))
        if (senderContactId in blockedContactIds) return
        val incoming = IncomingFriendRequest(senderContactId, request.nickname, request)
        scope.launch {
            if (settingsRepository.settings.first().autoAcceptFriendRequests) {
                respondFriendRequest(incoming, true)
            } else {
                _incomingRequests.value = _incomingRequests.value.filterNot { it.contactId == senderContactId } + incoming
                _events.tryEmit(ChatServiceEvent.FriendRequestReceived(incoming))
            }
        }
    }

    private fun handleFriendResponseFrame(bytes: ByteArray) {
        val response = runCatching {
            json.decodeFromString(FriendResponsePayload.serializer(), bytes.copyOfRange(1, bytes.size).decodeToString())
        }.getOrNull() ?: return
        if (!response.accepted) return
        val senderContactId = identityKeyManager.contactIdFor(Base64.decode(response.identityKeyBase64, Base64.NO_WRAP))
        addTrustedContact(senderContactId, response.nickname, response.identityKeyBase64, response.onionAddress)
        mediaStorage.selfAvatarFile().takeIf { it.exists() }?.let { sendAvatarTo(senderContactId, it.readBytes()) }
    }

    private fun frame(type: Byte, payload: ByteArray): ByteArray = byteArrayOf(type) + payload
}
