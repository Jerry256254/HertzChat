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
import cz.kuclab.hertzchat.data.db.GroupDao
import cz.kuclab.hertzchat.data.db.GroupEntity
import cz.kuclab.hertzchat.data.db.GroupMemberDao
import cz.kuclab.hertzchat.data.db.GroupMemberEntity
import cz.kuclab.hertzchat.data.db.MessageDao
import cz.kuclab.hertzchat.data.db.MessageEntity
import cz.kuclab.hertzchat.data.db.MessageType
import cz.kuclab.hertzchat.data.model.ChatPayload
import cz.kuclab.hertzchat.data.model.PayloadKind
import cz.kuclab.hertzchat.media.MediaCrypto
import cz.kuclab.hertzchat.media.MediaStorage
import cz.kuclab.hertzchat.mistral.MISTRAL_ASSISTANT_CONTACT_ID
import cz.kuclab.hertzchat.mistral.MistralApiClient
import cz.kuclab.hertzchat.mistral.MistralKeyStore
import cz.kuclab.hertzchat.mistral.MistralMessage
import cz.kuclab.hertzchat.network.p2p.FriendRequestPayload
import cz.kuclab.hertzchat.network.p2p.FriendResponsePayload
import cz.kuclab.hertzchat.network.p2p.HertzId
import cz.kuclab.hertzchat.network.p2p.I2pState
import cz.kuclab.hertzchat.network.p2p.I2pTransport
import cz.kuclab.hertzchat.network.p2p.LanTransport
import cz.kuclab.hertzchat.network.p2p.P2pConnection
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

// Wire framing prefix byte for the length-prefixed frames sent over a P2pConnection.
private const val FRAME_HELLO: Byte = 0
private const val FRAME_MESSAGE: Byte = 1
private const val FRAME_PREKEY: Byte = 2
private const val FRAME_MEDIA_CHUNK: Byte = 3
private const val FRAME_FRIEND_REQUEST: Byte = 4
private const val FRAME_FRIEND_RESPONSE: Byte = 5

private const val RETRY_INTERVAL_MS = 60_000L

/** `^@Mistral 5 what does everyone think?` - the number is how many recent thread messages to hand it as context. */
private val MISTRAL_INVOKE_REGEX = Regex("""^@Mistral\s+(\d+)\s+(.+)$""", RegexOption.IGNORE_CASE)
private const val MISTRAL_THREAD_SYSTEM_PROMPT = """Jsi AI asistent zabudovaný do KucLab Hertz Chat, vyvolaný pomocí @Mistral přímo v konverzaci mezi lidmi.
Dostaneš posledních pár zpráv z konverzace (u skupin s uvedeným jménem odesílatele) a dotaz na konci. Odpověz stručně a věcně,
v jazyce konverzace. Tvoje odpověď se pošle všem účastníkům vlákna jako zpráva od tebe."""

data class IncomingFriendRequest(val contactId: String, val nickname: String, val request: FriendRequestPayload)

sealed interface ChatServiceEvent {
    data class FriendRequestReceived(val request: IncomingFriendRequest) : ChatServiceEvent
    data class MessageReceived(val threadId: String, val message: MessageEntity) : ChatServiceEvent
}

/**
 * Orchestrates the whole P2P pipeline: I2P destinations are the transport
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
    private val groupDao: GroupDao,
    private val groupMemberDao: GroupMemberDao,
    private val mediaStorage: MediaStorage,
    private val i2pTransport: I2pTransport,
    private val lanTransport: LanTransport,
    private val settingsRepository: SettingsRepository,
    private val mistralKeyStore: MistralKeyStore,
    private val mistralApiClient: MistralApiClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val connections = mutableMapOf<String, P2pConnection>()
    private val ciphers = mutableMapOf<String, MessageCipher>()

    val i2pState: StateFlow<I2pState> get() = i2pTransport.state
    val bootstrapPercent: StateFlow<Int> get() = i2pTransport.bootstrapPercent
    val bootstrapLabel: StateFlow<String?> get() = i2pTransport.bootstrapLabel
    val i2pDestination: StateFlow<String?> get() = i2pTransport.i2pDestination
    val i2pError: StateFlow<String?> get() = i2pTransport.error
    val i2pDiagnostics: StateFlow<String?> get() = i2pTransport.diagnostics
    val lanPeerCount: StateFlow<Int> get() = lanTransport.peerCount

    /** Retries starting the I2P router after a previous failure (e.g. no internet at the time). */
    fun retryI2p() {
        i2pTransport.start(identityKeyManager.i2pPrivateKey) { newKey -> identityKeyManager.i2pPrivateKey = newKey }
    }

    private val _incomingRequests = MutableStateFlow<List<IncomingFriendRequest>>(emptyList())
    val incomingRequests: StateFlow<List<IncomingFriendRequest>> = _incomingRequests

    private val _events = MutableSharedFlow<ChatServiceEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ChatServiceEvent> = _events

    private var blockedContactIds: Set<String> = emptySet()
    private var started = false

    private data class IncomingTransfer(
        val contactId: String,
        /** Where the finished message belongs - the group for group media, otherwise the sender's 1:1 thread. */
        val threadId: String,
        /** Non-null only for group media, so the bubble can be attributed to the member who sent it. */
        val senderContactId: String?,
        val messageId: String,
        val kind: PayloadKind,
        val key: ByteArray,
        val nonceSalt: ByteArray,
        val chunkCount: Int,
        val mimeType: String,
        val fileName: String?,
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
        i2pTransport.start(identityKeyManager.i2pPrivateKey) { newKey -> identityKeyManager.i2pPrivateKey = newKey }
        scope.launch {
            i2pTransport.i2pDestination.collect { address -> if (address != null) identityKeyManager.i2pDestination = address }
        }
        scope.launch {
            i2pTransport.incomingConnections.collect { socket -> handleIncomingSocket(socket, viaLan = false) }
        }
        lanTransport.start(identityKeyManager.contactId())
        scope.launch {
            lanTransport.incomingConnections.collect { socket -> handleIncomingSocket(socket, viaLan = true) }
        }
        scope.launch { retryPendingLoop() }
        scope.launch {
            ensureSelfContact()
            // Backfill our own destination once I2P opens it, so the self entry isn't
            // left permanently address-less on the very first run.
            val destination = i2pTransport.i2pDestination.first { !it.isNullOrBlank() } ?: return@launch
            val myId = identityKeyManager.contactId()
            contactDao.find(myId)?.takeIf { it.i2pDestination.isBlank() }?.let {
                contactDao.update(it.copy(i2pDestination = destination))
            }
        }
    }

    /**
     * A note to yourself has already arrived the moment it's written to the local
     * database - the "recipient" is this very device. Routing it out through I2P and
     * back would leave it sitting at "waiting for the recipient to come online" for
     * as long as the loopback takes (and forever if it never completes).
     */
    private fun isSelf(contactId: String): Boolean = contactId == identityKeyManager.contactId()

    /**
     * Everyone has themselves in their contacts, the way "Message yourself" works
     * elsewhere - and unconditionally, from the first launch onward.
     *
     * This used to run the full friend-request pipeline against our own Hertz ID once
     * I2P reached CONNECTED, to establish a genuine Signal session rather than a
     * shortcut. That session buys nothing now that notes to self are delivered locally
     * (see [isSelf]) and it cost the entry outright whenever the network never got
     * there: no I2P, no self contact. Writing the row directly needs neither the
     * network nor a destination, so the contact is simply always present.
     */
    private suspend fun ensureSelfContact() {
        val myId = identityKeyManager.contactId()
        if (contactDao.find(myId) != null) return
        contactDao.upsert(
            ContactEntity(
                contactId = myId,
                nickname = identityKeyManager.nickname,
                identityKeyBytes = identityKeyManager.identityKeyPair().publicKey.serialize(),
                // Filled in once I2P opens our destination; nothing is ever dialled for
                // a note to self, so an empty address here changes nothing.
                i2pDestination = i2pTransport.i2pDestination.value.orEmpty(),
                addedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun stop() {
        i2pTransport.stop()
        lanTransport.stop()
        connections.values.forEach { it.close() }
        connections.clear()
        started = false
    }

    private fun cipherFor(contactId: String): MessageCipher =
        ciphers.getOrPut(contactId) { MessageCipher(protocolStore, contactId) }

    // --- Identity / friend requests ---

    /** Null until I2P has opened our destination - there's no usable Hertz ID to share before that. */
    fun myHertzId(): HertzId? {
        val address = i2pTransport.i2pDestination.value ?: return null
        return HertzId(
            contactId = identityKeyManager.contactId(),
            nickname = identityKeyManager.nickname,
            identityKeyBase64 = Base64.encodeToString(identityKeyManager.identityKeyPair().publicKey.serialize(), Base64.NO_WRAP),
            i2pDestination = address,
        )
    }

    /** Result carries a human-readable reason on failure, since "nothing happened" after scanning a QR code is a bad silent failure mode. */
    suspend fun sendFriendRequest(target: HertzId, viaGroupId: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        val me = myHertzId() ?: return@withContext Result.failure(IllegalStateException("Síť I2P ještě není připravená - zkus to za chvíli znovu"))
        if (target.i2pDestination.isBlank()) return@withContext Result.failure(IllegalStateException("Neplatné Hertz ID (chybí adresa)"))
        runCatching {
            val payload = FriendRequestPayload(
                nickname = me.nickname,
                identityKeyBase64 = me.identityKeyBase64,
                i2pDestination = me.i2pDestination,
                preKeyBundle = identityKeyManager.currentPreKeyBundle().toWire(),
                viaGroupId = viaGroupId,
                allowsMistralAccess = settingsRepository.settings.first().allowMistralOnMyMessages,
            )
            val connection = dialAndRegister(target.contactId, target.i2pDestination)
            connection.send(frame(FRAME_FRIEND_REQUEST, json.encodeToString(payload).encodeToByteArray()))
        }
    }

    fun respondFriendRequest(request: IncomingFriendRequest, accept: Boolean) {
        _incomingRequests.value = _incomingRequests.value.filterNot { it.contactId == request.contactId }
        if (accept) {
            addTrustedContact(
                request.contactId,
                request.nickname,
                request.request.identityKeyBase64,
                request.request.i2pDestination,
                request.request.allowsMistralAccess,
            )
            cipherFor(request.contactId).establishSessionFromBundle(request.request.preKeyBundle.toPreKeyBundle())
            mediaStorage.selfAvatarFile().takeIf { it.exists() }?.let { sendAvatarTo(request.contactId, it.readBytes()) }
        }
        scope.launch {
            val me = myHertzId() ?: return@launch
            val response = FriendResponsePayload(
                accepted = accept,
                nickname = me.nickname,
                identityKeyBase64 = me.identityKeyBase64,
                i2pDestination = me.i2pDestination,
                allowsMistralAccess = settingsRepository.settings.first().allowMistralOnMyMessages,
            )
            runCatching {
                val connection = dialAndRegister(request.contactId, request.request.i2pDestination)
                connection.send(frame(FRAME_FRIEND_RESPONSE, json.encodeToString(response).encodeToByteArray()))
            }
        }
    }

    private fun addTrustedContact(contactId: String, nickname: String, identityKeyBase64: String, i2pDestination: String, allowsMistralAccess: Boolean = true) {
        scope.launch {
            contactDao.upsert(
                ContactEntity(
                    contactId = contactId,
                    nickname = nickname,
                    identityKeyBytes = Base64.decode(identityKeyBase64, Base64.NO_WRAP),
                    i2pDestination = i2pDestination,
                    addedAt = System.currentTimeMillis(),
                    allowsMistralAccess = allowsMistralAccess,
                ),
            )
        }
    }

    // --- Groups ---

    /** All members must already be trusted 1:1 contacts (their sessions are what makes fan-out delivery work). */
    fun createGroup(name: String, memberContactIds: List<String>) {
        scope.launch {
            val groupId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            groupDao.upsert(GroupEntity(groupId, name, createdAt = now))
            val members = memberContactIds.mapNotNull { contactDao.find(it) }
            members.forEach { groupMemberDao.upsert(GroupMemberEntity(groupId, it.contactId, it.nickname)) }

            val me = myHertzId() ?: return@launch
            // Every member's HertzId (including the creator) so recipients who don't know each other yet can auto-introduce themselves.
            val roster = listOf(me) + members.map { HertzId(it.contactId, it.nickname, Base64.encodeToString(it.identityKeyBytes, Base64.NO_WRAP), it.i2pDestination) }
            val invite = ChatPayload(UUID.randomUUID().toString(), now, PayloadKind.GROUP_INVITE, groupId = groupId, groupName = name, groupMembers = roster)
            members.forEach { trySendPayload(it.contactId, invite) }
        }
    }

    fun leaveGroup(groupId: String) {
        scope.launch {
            groupMemberDao.deleteAllForGroup(groupId)
            groupDao.delete(groupId)
            messageDao.deleteAllForContact(groupId)
        }
    }

    private suspend fun handleGroupInvite(fromContactId: String, payload: ChatPayload) {
        val groupId = payload.groupId ?: return
        val name = payload.groupName ?: return
        val roster = payload.groupMembers ?: return
        val myContactId = identityKeyManager.contactId()

        groupDao.upsert(GroupEntity(groupId, name, createdAt = System.currentTimeMillis()))
        roster.filter { it.contactId != myContactId }.forEach { member ->
            groupMemberDao.upsert(GroupMemberEntity(groupId, member.contactId, member.nickname))
            if (member.contactId != fromContactId && contactDao.find(member.contactId) == null) {
                // Don't already know this member (they didn't invite us directly) - auto-introduce
                // ourselves, vouched for by both of us already trusting the group's creator.
                scope.launch { sendFriendRequest(member, viaGroupId = groupId) }
            }
        }
    }

    /** Broadcasts the local "let others use @Mistral on my messages" preference to every existing contact. */
    fun broadcastMistralPreference(allowed: Boolean) {
        scope.launch {
            val payload = ChatPayload(UUID.randomUUID().toString(), System.currentTimeMillis(), PayloadKind.PREFERENCE_UPDATE, allowsMistralAccess = allowed)
            contactDao.observeContacts().first().forEach { trySendPayload(it.contactId, payload) }
        }
    }

    // --- @mentions ---

    /** Resolves `@nickname` (and `@Mistral`) tokens in a group message to contactIds, purely for the "you were mentioned" notification - not sent over the wire, every member re-derives it locally from the same roster. */
    suspend fun resolveMentions(groupId: String, text: String): List<String> {
        val ids = mutableListOf<String>()
        if (Regex("(?i)@Mistral\\b").containsMatchIn(text)) ids += MISTRAL_ASSISTANT_CONTACT_ID
        groupMemberDao.findMembers(groupId).forEach { member ->
            if (Regex("@" + Regex.escape(member.nickname) + "\\b").containsMatchIn(text)) ids += member.contactId
        }
        return ids
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
            if (isSelf(contactId)) {
                messageDao.updateState(payload.messageId, DeliveryState.DELIVERED)
            } else {
                val delivered = trySendPayload(contactId, payload)
                messageDao.updateState(payload.messageId, if (delivered) DeliveryState.SENT else DeliveryState.PENDING)
            }
            maybeInvokeMistral(threadId = contactId, isGroup = false, text = text)
        }
    }

    /** Fans the message out individually to every member (each already has its own 1:1 Signal session - there's no group sender-key ratchet here). */
    fun sendGroupText(groupId: String, text: String) {
        scope.launch {
            val members = groupMemberDao.findMembers(groupId)
            val mentions = resolveMentions(groupId, text)
            val messageId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            messageDao.upsert(
                MessageEntity(
                    messageId = messageId,
                    contactId = groupId,
                    fromMe = true,
                    type = MessageType.TEXT,
                    text = text,
                    timestamp = now,
                    deliveryState = DeliveryState.PENDING,
                    mentionedContactIds = mentions.takeIf { it.isNotEmpty() }?.joinToString(","),
                ),
            )
            val payload = ChatPayload(messageId, now, PayloadKind.TEXT, text = text, groupId = groupId)
            val delivered = members.map { trySendPayload(it.contactId, payload) }.any { it }
            messageDao.updateState(messageId, if (delivered) DeliveryState.SENT else DeliveryState.PENDING)
            maybeInvokeMistral(threadId = groupId, isGroup = true, text = text)
        }
    }

    // --- @Mistral invocation ---

    private suspend fun maybeInvokeMistral(threadId: String, isGroup: Boolean, text: String) {
        if (!mistralKeyStore.enabled.value) return
        val match = MISTRAL_INVOKE_REGEX.find(text) ?: return
        val historyLimit = match.groupValues[1].toIntOrNull()?.coerceIn(1, 100) ?: return
        val question = match.groupValues[2]

        val allowedSenderIds: Set<String>
        if (isGroup) {
            val members = groupMemberDao.findMembers(threadId)
            val allowed = members.filter { contactDao.find(it.contactId)?.allowsMistralAccess != false }
            if (allowed.isEmpty()) return // nobody else in the group allows it - refuse, per spec
            allowedSenderIds = allowed.map { it.contactId }.toSet()
        } else {
            val contact = contactDao.find(threadId) ?: return
            if (!contact.allowsMistralAccess) return
            allowedSenderIds = setOf(threadId)
        }

        val nicknames = if (isGroup) groupMemberDao.findMembers(threadId).associate { it.contactId to it.nickname } else emptyMap()
        val recent = messageDao.recentForThread(threadId, historyLimit).reversed()
            .filter { it.fromMe || it.senderContactId == null || it.senderContactId in allowedSenderIds }

        val history = buildList {
            add(MistralMessage("system", MISTRAL_THREAD_SYSTEM_PROMPT))
            recent.forEach { m ->
                val role = if (m.fromAssistant) "assistant" else "user"
                val content = m.text ?: return@forEach
                val labeled = if (isGroup && !m.fromMe && !m.fromAssistant) {
                    "${nicknames[m.senderContactId] ?: "?"}: $content"
                } else {
                    content
                }
                add(MistralMessage(role, labeled))
            }
            add(MistralMessage("user", question))
        }

        val reply = mistralApiClient.chat(history).getOrNull() ?: return
        relayAssistantReply(threadId, isGroup, reply)
    }

    private suspend fun relayAssistantReply(threadId: String, isGroup: Boolean, text: String) {
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val entity = MessageEntity(
            messageId = messageId,
            contactId = threadId,
            fromMe = false,
            type = MessageType.TEXT,
            text = text,
            timestamp = now,
            deliveryState = DeliveryState.DELIVERED,
            fromAssistant = true,
        )
        messageDao.upsert(entity)
        _events.tryEmit(ChatServiceEvent.MessageReceived(threadId, entity))

        val payload = ChatPayload(messageId, now, PayloadKind.TEXT, text = text, groupId = if (isGroup) threadId else null, fromAssistant = true)
        if (isGroup) {
            groupMemberDao.findMembers(threadId).forEach { trySendPayload(it.contactId, payload) }
        } else {
            trySendPayload(threadId, payload)
        }
    }

    /** Returns true if the envelope made it onto a connection successfully - not a delivery receipt, just "left this device". */
    private suspend fun trySendPayload(contactId: String, payload: ChatPayload): Boolean {
        val contact = contactDao.find(contactId) ?: return false
        val envelope = cipherFor(contactId).encrypt(json.encodeToString(payload).encodeToByteArray())
        val frameType = if (envelope.isPreKeyMessage) FRAME_PREKEY else FRAME_MESSAGE
        return sendFrameTo(contactId, contact.i2pDestination, frame(frameType, envelope.ciphertext))
    }

    private suspend fun sendFrameTo(contactId: String, i2pDestination: String, bytes: ByteArray): Boolean = runCatching {
        val connection = connections[contactId]?.takeIf { runCatching { it.send(bytes) }.isSuccess }
            ?: dialAndRegister(contactId, i2pDestination).also { it.send(bytes) }
        connection
    }.isSuccess

    private suspend fun dialAndRegister(contactId: String, i2pDestination: String): P2pConnection = withContext(Dispatchers.IO) {
        // Same local network wins: it's direct, near-instant, works with no internet at
        // all, and needs no bootstrap of any kind. I2P is the fallback for everyone else.
        val socket: Socket = if (lanTransport.addressFor(contactId) != null) {
            runCatching { lanTransport.connectTo(contactId) }.getOrElse { i2pTransport.connectTo(i2pDestination) }
        } else {
            i2pTransport.connectTo(i2pDestination)
        }
        val connection = P2pConnection(socket)
        connection.send(frame(FRAME_HELLO, identityKeyManager.contactId().encodeToByteArray()))
        registerConnection(contactId, connection)
        scope.launch { readLoop(contactId, connection) }
        connection
    }

    private fun registerConnection(contactId: String, connection: P2pConnection) {
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
                    mediaFileName = fileName,
                    mediaDurationMs = durationMs,
                    timestamp = control.sentAt,
                    deliveryState = DeliveryState.PENDING,
                ),
            )

            if (isSelf(contactId)) {
                messageDao.updateState(messageId, DeliveryState.DELIVERED)
                return@launch
            }

            val contact = contactDao.find(contactId)
            val delivered = contact != null && runCatching {
                check(trySendPayload(contactId, control))
                var offset = 0
                var index = 0
                while (offset < sourceBytes.size) {
                    val end = minOf(offset + MediaCrypto.CHUNK_SIZE, sourceBytes.size)
                    val cipherChunk = MediaCrypto.encryptChunk(key, nonceSalt, index, sourceBytes.copyOfRange(offset, end))
                    check(sendFrameTo(contactId, contact.i2pDestination, frameMediaChunk(transferId, index, cipherChunk)))
                    offset = end
                    index++
                }
            }.isSuccess
            messageDao.updateState(messageId, if (delivered) DeliveryState.SENT else DeliveryState.PENDING)
        }
    }

    /**
     * Group media, fanned out per member like [sendGroupText] is. The media itself is
     * encrypted once with one symmetric key (the bytes on the wire are identical for
     * every member), but that key travels inside the per-member Signal-encrypted control
     * payload - so each member unwraps it through their own ratchet and there's still no
     * shared group key anywhere.
     */
    fun sendGroupMedia(groupId: String, sourceBytes: ByteArray, mimeType: String, kind: PayloadKind, fileName: String?, durationMs: Long? = null) {
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
            groupId = groupId,
        )

        scope.launch {
            messageDao.upsert(
                MessageEntity(
                    messageId = messageId,
                    contactId = groupId,
                    fromMe = true,
                    type = kind.toMessageType(),
                    mediaPath = localCopy.absolutePath,
                    mediaMimeType = mimeType,
                    mediaFileName = fileName,
                    mediaDurationMs = durationMs,
                    timestamp = control.sentAt,
                    deliveryState = DeliveryState.PENDING,
                ),
            )

            val members = groupMemberDao.findMembers(groupId)
            val delivered = members.map { member ->
                val contact = contactDao.find(member.contactId)
                contact != null && runCatching {
                    check(trySendPayload(member.contactId, control))
                    var offset = 0
                    var index = 0
                    while (offset < sourceBytes.size) {
                        val end = minOf(offset + MediaCrypto.CHUNK_SIZE, sourceBytes.size)
                        val cipherChunk = MediaCrypto.encryptChunk(key, nonceSalt, index, sourceBytes.copyOfRange(offset, end))
                        check(sendFrameTo(member.contactId, contact.i2pDestination, frameMediaChunk(transferId, index, cipherChunk)))
                        offset = end
                        index++
                    }
                }.isSuccess
            }.any { it }
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
                    check(sendFrameTo(contactId, contact.i2pDestination, frameMediaChunk(transferId, index, cipherChunk)))
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
            threadId = payload.groupId ?: contactId,
            senderContactId = payload.groupId?.let { contactId },
            messageId = payload.messageId,
            kind = payload.kind,
            key = key,
            nonceSalt = nonceSalt,
            chunkCount = chunkCount,
            mimeType = mimeType,
            fileName = payload.mediaFileName,
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
                contactId = transfer.threadId,
                fromMe = false,
                type = transfer.kind.toMessageType(),
                mediaPath = transfer.outputFile.absolutePath,
                mediaMimeType = transfer.mimeType,
                mediaFileName = transfer.fileName,
                mediaDurationMs = transfer.durationMs,
                timestamp = System.currentTimeMillis(),
                deliveryState = DeliveryState.DELIVERED,
                senderContactId = transfer.senderContactId,
            )
            scope.launch {
                messageDao.upsert(entity)
                _events.tryEmit(ChatServiceEvent.MessageReceived(transfer.threadId, entity))
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
                bytes != null && resendMedia(message, bytes, contact.i2pDestination)
            }
            if (delivered) messageDao.updateState(message.messageId, DeliveryState.SENT)
        }
    }

    private suspend fun resendMedia(message: MessageEntity, sourceBytes: ByteArray, i2pDestination: String): Boolean {
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
                check(sendFrameTo(message.contactId, i2pDestination, frameMediaChunk(transferId, index, cipherChunk)))
                offset = end
                index++
            }
        }.isSuccess
    }

    // --- Connection handling ---

    private fun handleIncomingSocket(socket: Socket, viaLan: Boolean) {
        scope.launch {
            val connection = P2pConnection(socket)
            var fromContactId: String? = null
            try {
                while (true) {
                    val bytes = withContext(Dispatchers.IO) { connection.receive() }
                    if (bytes.isEmpty()) continue
                    when (bytes[0]) {
                        FRAME_HELLO -> {
                            val claimed = bytes.copyOfRange(1, bytes.size).decodeToString()
                            // Over I2P the destination that dialled us is itself proof of identity.
                            // A LAN socket proves nothing, so only honour the claim if mDNS actually
                            // announced that contact at this address - see LanTransport.matchesDiscovered.
                            val identityPlausible = !viaLan || lanTransport.matchesDiscovered(claimed, socket.inetAddress)
                            if (!identityPlausible || claimed in blockedContactIds) return@launch
                            fromContactId = claimed
                            registerConnection(claimed, connection)
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

    private suspend fun readLoop(contactId: String, connection: P2pConnection) {
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

        when (payload.kind) {
            PayloadKind.IMAGE, PayloadKind.VIDEO, PayloadKind.VOICE, PayloadKind.FILE, PayloadKind.AVATAR -> beginIncomingTransfer(contactId, payload)
            PayloadKind.GROUP_INVITE -> scope.launch { handleGroupInvite(contactId, payload) }
            PayloadKind.PREFERENCE_UPDATE -> scope.launch { contactDao.setAllowsMistralAccess(contactId, payload.allowsMistralAccess ?: true) }
            PayloadKind.TEXT -> {
                val threadId = payload.groupId ?: contactId
                scope.launch {
                    val mentions = if (payload.groupId != null) resolveMentions(payload.groupId, payload.text.orEmpty()) else emptyList()
                    val entity = MessageEntity(
                        messageId = payload.messageId,
                        contactId = threadId,
                        fromMe = false,
                        type = MessageType.TEXT,
                        text = payload.text,
                        timestamp = payload.sentAt,
                        deliveryState = DeliveryState.DELIVERED,
                        senderContactId = if (payload.groupId != null) contactId else null,
                        fromAssistant = payload.fromAssistant,
                        mentionedContactIds = mentions.takeIf { it.isNotEmpty() }?.joinToString(","),
                    )
                    messageDao.upsert(entity)
                    _events.tryEmit(ChatServiceEvent.MessageReceived(threadId, entity))
                }
            }
            else -> Unit
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
            // Vouched for by mutual membership in a group we already joined ourselves - no need to bother the user with a manual prompt for it.
            val viaKnownGroup = request.viaGroupId?.let { groupDao.find(it) != null } == true
            val isSelf = senderContactId == identityKeyManager.contactId()
            if (isSelf || settingsRepository.settings.first().autoAcceptFriendRequests || viaKnownGroup) {
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
        addTrustedContact(senderContactId, response.nickname, response.identityKeyBase64, response.i2pDestination, response.allowsMistralAccess)
        mediaStorage.selfAvatarFile().takeIf { it.exists() }?.let { sendAvatarTo(senderContactId, it.readBytes()) }
    }

    private fun frame(type: Byte, payload: ByteArray): ByteArray = byteArrayOf(type) + payload
}
