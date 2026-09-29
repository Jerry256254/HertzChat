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
import cz.kuclab.hertzchat.data.model.MessageSequencer
import cz.kuclab.hertzchat.data.model.PayloadKind
import cz.kuclab.hertzchat.media.FileChunks
import cz.kuclab.hertzchat.media.ImageEditor
import cz.kuclab.hertzchat.media.MediaCrypto
import cz.kuclab.hertzchat.media.MediaStorage
import cz.kuclab.hertzchat.network.p2p.FriendRequestPayload
import cz.kuclab.hertzchat.network.p2p.FriendResponsePayload
import cz.kuclab.hertzchat.network.p2p.HertzId
import cz.kuclab.hertzchat.network.p2p.I2pState
import cz.kuclab.hertzchat.network.p2p.I2pTransport
import cz.kuclab.hertzchat.network.p2p.LanTransport
import cz.kuclab.hertzchat.network.p2p.P2pConnection
import java.io.File
import java.io.RandomAccessFile
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Wire framing prefix byte for the length-prefixed frames sent over a P2pConnection.
private const val FRAME_HELLO: Byte = 0
private const val FRAME_MESSAGE: Byte = 1
private const val FRAME_PREKEY: Byte = 2
private const val FRAME_MEDIA_CHUNK: Byte = 3
private const val FRAME_FRIEND_REQUEST: Byte = 4
private const val FRAME_FRIEND_RESPONSE: Byte = 5

private const val RETRY_INTERVAL_MS = 15_000L
/** Idle open connections get a tiny ping on this cadence, so tunnels and NAT bindings stay warm instead of dying between messages. */
private const val HEARTBEAT_MS = 180_000L
/** A send that doesn't leave the device within this long is treated as a dead connection and re-dialled, instead of blocking behind TCP retransmits. */
private const val SEND_TIMEOUT_MS = 12_000L
/** How long a SENT message waits for a delivery receipt before it's confirmed optimistically (the peer may run a version that never sends receipts). */
private const val ACK_GRACE_MS = 10 * 60_000L
/** A SENT message unacked past this age gets its payload re-sent to solicit a fresh receipt (idempotent - the receiver just re-acks). */
private const val ACK_RESOLICIT_MS = 30_000L
/** Incoming transfers with no progress past this age are discarded (their file handle closed, partial file deleted). */
private const val STALE_TRANSFER_MS = 30 * 60_000L
/** Attempts per chunk before the whole transfer is abandoned - re-dialing between attempts. */
private const val CHUNK_ATTEMPTS = 5
/** Friend requests are re-sent until accepted or this old - then they expire quietly. */
private const val REQUEST_EXPIRY_MS = 7 * 24 * 60 * 60_000L
/** Chunks buffered for transfers whose control payload hasn't arrived yet, per transfer - past this the sender is presumed broken and extras drop. */
private const val PENDING_CHUNKS_CAP = 1024
/** How many recent 1:1 contacts get pre-dialled the moment I2P connects (see [P2pChatService.warmRecentContacts]). */
private const val WARMUP_CONTACTS = 4

data class IncomingFriendRequest(val contactId: String, val nickname: String, val request: FriendRequestPayload)

sealed interface ChatServiceEvent {
    data class FriendRequestReceived(val request: IncomingFriendRequest) : ChatServiceEvent
    data class MessageReceived(val threadId: String, val message: MessageEntity) : ChatServiceEvent
    /** One decrypted call packet (signaling or audio) - consumed by CallManager, never stored. */
    data class CallSignaling(val contactId: String, val payload: ChatPayload) : ChatServiceEvent
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
    private val pushPinger: cz.kuclab.hertzchat.p2p.PushPinger,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Sockets we dialled, by contact - the send path. Kept strictly separate from
     * [incomingConnections]: the old single slot let an inbound dial evict (and close)
     * our outbound socket mid-write, which read exactly as "receiving works but
     * sending is stuck" whenever both sides talked at once.
     */
    private val outgoingConnections = java.util.concurrent.ConcurrentHashMap<String, P2pConnection>()
    /** Sockets peers dialled to us, by contact - the receive path. Never used for sending. */
    private val incomingConnections = java.util.concurrent.ConcurrentHashMap<String, P2pConnection>()
    private val ciphers = mutableMapOf<String, MessageCipher>()
    /** Serializes retry sweeps - the loop, the event-driven flushes and a manual send may all fire at once. */
    private val retryMutex = Mutex()
    /** One dial at a time per contact - parallel dials evict each other's connections mid-write (see [sendFrameTo]). */
    private val dialMutexes = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private fun dialMutexFor(contactId: String): Mutex = dialMutexes.getOrPut(contactId) { Mutex() }
    /** Avatar hashes already pull-requested per contact - stops every incoming message re-requesting the same photo while its transfer is still in flight. */
    private val requestedAvatarHashes = java.util.concurrent.ConcurrentHashMap<String, String>()
    @Volatile private var cachedAvatarHash: String? = null
    @Volatile private var cachedAvatarHashValid = false


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

    internal fun nextSeq(sentAtMs: Long): Long = MessageSequencer.next(sentAtMs)

    private data class IncomingTransfer(
        val contactId: String,
        /** Where the finished message belongs - the group for group media, otherwise the sender's 1:1 thread. */
        val threadId: String,
        /** Non-null only for group media, so the bubble can be attributed to the member who sent it. */
        val senderContactId: String?,
        val messageId: String,
        /** The control payload's own timestamp/sequence - the bubble sorts by *send* order, not by when its last byte landed. */
        val sentAt: Long,
        val seq: Long,
        val kind: PayloadKind,
        val key: ByteArray,
        val nonceSalt: ByteArray,
        val chunkCount: Int,
        val mimeType: String,
        val fileName: String?,
        val durationMs: Long?,
        val outputFile: File,
        val out: RandomAccessFile,
        /** Which chunk indexes already landed - retried chunks would otherwise "complete" a transfer early with holes. */
        val receivedIndices: MutableSet<Int> = mutableSetOf(),
        val startedAt: Long = System.currentTimeMillis(),
    )

    private val incomingTransfers = ConcurrentHashMap<String, IncomingTransfer>()

    private data class PendingChunks(val startedAt: Long = System.currentTimeMillis(), val chunks: MutableMap<Int, ByteArray> = mutableMapOf())
    /** Chunks that beat their control payload here - held until it arrives (or they go stale), never dropped on the floor. */
    private val pendingChunks = ConcurrentHashMap<String, PendingChunks>()

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
        scope.launch { heartbeatLoop() }
        // Event-driven flushes: the moment the network (or a specific peer)
        // proves reachable, anything waiting goes out immediately instead of
        // sitting until the next retry tick.
        scope.launch {
            // StateFlow already drops consecutive repeats, so this only fires
            // on genuine transitions into CONNECTED.
            i2pTransport.state.collect { state ->
                if (state == I2pState.CONNECTED) {
                    runCatching { retryPendingNow() }
                    runCatching { retryPendingRequests() }
                    // Just joined the network - push who we are to whoever is
                    // already online, so a changed name/photo propagates on
                    // connect instead of waiting for the next message.
                    broadcastProfile()
                    // And pre-dial the most recent contacts, so the first
                    // message after a cold start finds live sockets.
                    warmRecentContacts()
                }
            }
        }
        scope.launch {
            lanTransport.peerCount.collect {
                runCatching { retryPendingNow() }
            }
        }
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
        outgoingConnections.values.forEach { it.close() }
        outgoingConnections.clear()
        incomingConnections.values.forEach { it.close() }
        incomingConnections.clear()
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
        val result = runCatching {
            val payload = FriendRequestPayload(
                nickname = me.nickname,
                identityKeyBase64 = me.identityKeyBase64,
                i2pDestination = me.i2pDestination,
                preKeyBundle = identityKeyManager.currentPreKeyBundle().toWire(),
                viaGroupId = viaGroupId,
                pushTopic = settingsRepository.pushTopic(),
            )
            val connection = dialAndRegister(target.contactId, target.i2pDestination)
            connection.send(frame(FRAME_FRIEND_REQUEST, json.encodeToString(payload).encodeToByteArray()))
        }
        // A sent request may still never land (they were offline) - the retry
        // sweep keeps re-sending until they accept (or it expires).
        if (result.isSuccess) rememberPendingRequest(target)
        result
    }

    private fun rememberPendingRequest(target: HertzId) {
        val entry = "${System.currentTimeMillis()}\n${json.encodeToString(target)}"
        val kept = identityKeyManager.pendingFriendRequests.filterNot { it.substringAfter("\n", "") == json.encodeToString(target) }
        identityKeyManager.pendingFriendRequests = (kept + entry).toSet()
    }

    private fun forgetPendingRequest(contactId: String) {
        identityKeyManager.pendingFriendRequests = identityKeyManager.pendingFriendRequests.filterNot { entry ->
            runCatching { json.decodeFromString(HertzId.serializer(), entry.substringAfter("\n", "")) }.getOrNull()?.contactId == contactId
        }.toSet()
    }

    /** Re-sends every unaccepted request (and drops expired ones) - the other side may have been offline for all previous attempts. */
    private suspend fun retryPendingRequests() {
        if (i2pTransport.state.value != I2pState.CONNECTED) return
        val now = System.currentTimeMillis()
        val kept = mutableSetOf<String>()
        identityKeyManager.pendingFriendRequests.forEach { entry ->
            val createdAt = entry.substringBefore("\n", "").toLongOrNull() ?: 0L
            val target = runCatching { json.decodeFromString(HertzId.serializer(), entry.substringAfter("\n", "")) }.getOrNull()
            if (target == null || now - createdAt > REQUEST_EXPIRY_MS) return@forEach
            // Accepted meanwhile (their response may itself still be in flight) - stop asking.
            if (contactDao.find(target.contactId) != null) return@forEach
            kept += entry
            runCatching { sendFriendRequestNow(target) }
        }
        identityKeyManager.pendingFriendRequests = kept
    }

    /** The actual re-send behind [retryPendingRequests] - same payload shape as the original, fresh prekeys. */
    private suspend fun sendFriendRequestNow(target: HertzId) {
        val me = myHertzId() ?: return
        val payload = FriendRequestPayload(
            nickname = me.nickname,
            identityKeyBase64 = me.identityKeyBase64,
            i2pDestination = me.i2pDestination,
            preKeyBundle = identityKeyManager.currentPreKeyBundle().toWire(),
            pushTopic = settingsRepository.pushTopic(),
        )
        val connection = dialAndRegister(target.contactId, target.i2pDestination)
        connection.send(frame(FRAME_FRIEND_REQUEST, json.encodeToString(payload).encodeToByteArray()))
    }

    /**
     * Called straight from the UI when the user taps Accept, so everything it does has to
     * get off the main thread first: establishing the Signal session runs libsignal's
     * SessionBuilder, which reads and writes our identity/prekey tables through
     * [cz.kuclab.hertzchat.crypto.RoomSignalProtocolStore] synchronously. Room refuses
     * main-thread access outright ("Cannot access database on the main thread"), so
     * accepting a request crashed the app on the spot. Ordering matters too - the contact
     * row must exist before the session is built against it, which a fire-and-forget
     * insert alongside it never guaranteed.
     */
    fun respondFriendRequest(request: IncomingFriendRequest, accept: Boolean) {
        _incomingRequests.value = _incomingRequests.value.filterNot { it.contactId == request.contactId }
        // Their request is answered - if we also had one pending to them, it served its purpose.
        forgetPendingRequest(request.contactId)
        scope.launch {
            if (accept) {
                addTrustedContact(
                    request.contactId,
                    request.nickname,
                    request.request.identityKeyBase64,
                    request.request.i2pDestination,
                    request.request.pushTopic,
                )
                runCatching {
                    cipherFor(request.contactId).establishSessionFromBundle(request.request.preKeyBundle.toPreKeyBundle())
                }
                // Push mine and pull theirs: either direction alone can miss while
                // the fresh session settles, together the photos land on both sides.
                sendProfileTo(request.contactId)
                requestProfile(request.contactId)
            }
            val me = myHertzId() ?: return@launch
            val response = FriendResponsePayload(
                accepted = accept,
                nickname = me.nickname,
                identityKeyBase64 = me.identityKeyBase64,
                i2pDestination = me.i2pDestination,
                // The other half of the symmetric handshake described above - lets the
                // original requester establish their own side of the session too.
                preKeyBundle = if (accept) identityKeyManager.currentPreKeyBundle().toWire() else null,
                pushTopic = if (accept) settingsRepository.pushTopic() else null,
            )
            runCatching {
                val connection = dialAndRegister(request.contactId, request.request.i2pDestination)
                connection.send(frame(FRAME_FRIEND_RESPONSE, json.encodeToString(response).encodeToByteArray()))
            }
        }
    }

    private suspend fun addTrustedContact(contactId: String, nickname: String, identityKeyBase64: String, i2pDestination: String, pushTopic: String? = null) {
        run {
            // Never clobber an already-exchanged topic with a null from an older peer.
            val previous = contactDao.find(contactId)?.pushTopic
            contactDao.upsert(
                ContactEntity(
                    contactId = contactId,
                    nickname = nickname,
                    identityKeyBytes = Base64.decode(identityKeyBase64, Base64.NO_WRAP),
                    i2pDestination = i2pDestination,
                    addedAt = System.currentTimeMillis(),
                    pushTopic = pushTopic ?: previous,
                ),
            )
        }
    }

    // --- Groups ---

    /** All members must already be trusted 1:1 contacts (their sessions are what makes fan-out delivery work). The creator becomes the group's owner - see [GroupEntity.ownerId]. */
    fun createGroup(name: String, memberContactIds: List<String>) {
        scope.launch {
            val groupId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val myId = identityKeyManager.contactId()
            groupDao.upsert(GroupEntity(groupId, name, createdAt = now, ownerId = myId))
            // Belt-and-suspenders against the same self-invite that could crash this: the
            // self contact never has a Signal session, so trySendPayload to it below would
            // throw NoSessionException. The picker already excludes it (ContactsViewModel).
            val members = memberContactIds.filter { it != myId }.mapNotNull { contactDao.find(it) }
            members.forEach { groupMemberDao.upsert(GroupMemberEntity(groupId, it.contactId, it.nickname)) }

            val me = myHertzId() ?: return@launch
            // Every member's HertzId (including the creator) so recipients who don't know each other yet can auto-introduce themselves.
            val roster = listOf(me) + members.map { HertzId(it.contactId, it.nickname, Base64.encodeToString(it.identityKeyBytes, Base64.NO_WRAP), it.i2pDestination) }
            val invite = ChatPayload(UUID.randomUUID().toString(), now, PayloadKind.GROUP_INVITE, groupId = groupId, groupName = name, groupMembers = roster, groupOwnerId = myId)
            coroutineScope {
                members.map { async { trySendPayload(it.contactId, invite) } }.awaitAll()
            }
        }
    }

    fun leaveGroup(groupId: String) {
        scope.launch { wipeGroupLocally(groupId) }
    }

    /**
     * Owner-only: deletes the group everywhere - every member gets a GROUP_DELETE
     * (which their client honours only from the recorded owner) and this device
     * wipes it too. Offline members catch up later: the delete leaves a tombstone
     * that is re-sent to anyone reaching us afterwards (see [syncGroupStateTo]).
     */
    fun deleteGroup(groupId: String) {
        scope.launch {
            val group = groupDao.find(groupId) ?: return@launch
            if (!canManage(group)) return@launch
            val members = groupMemberDao.findMembers(groupId)
            // Wipe locally FIRST: the sends below dial every member and a single
            // unreachable one used to stall the wipe for minutes, leaving the
            // "deleted" group on screen here long after members already lost it.
            // The tombstone still catches offline members up later.
            identityKeyManager.deletedGroupIds = identityKeyManager.deletedGroupIds + groupId
            wipeGroupLocally(groupId)
            val payload = ChatPayload(
                UUID.randomUUID().toString(), System.currentTimeMillis(), PayloadKind.GROUP_DELETE,
                groupId = groupId,
            )
            coroutineScope {
                members.map { async { trySendPayload(it.contactId, payload) } }.awaitAll()
            }
        }
    }

    /**
     * Groups created before owner tracking carry a blank owner - anyone in them
     * may manage (the pre-owner free-for-all), and the first manager stamps
     * themselves so the group converges to the strict model from then on.
     */
    private fun canManage(group: GroupEntity): Boolean {
        val myId = identityKeyManager.contactId()
        return group.ownerId.isBlank() || group.ownerId == myId
    }

    private suspend fun claimOwnershipIfLegacy(group: GroupEntity): GroupEntity {
        if (group.ownerId.isNotBlank()) return group
        val claimed = group.copy(ownerId = identityKeyManager.contactId())
        groupDao.upsert(claimed)
        return claimed
    }

    private suspend fun wipeGroupLocally(groupId: String) {
        groupMemberDao.deleteAllForGroup(groupId)
        groupDao.delete(groupId)
        messageDao.deleteAllForContact(groupId)
    }

    /**
     * Owner-only: adds already-trusted 1:1 contacts to an existing group. Broadcasting a
     * fresh [PayloadKind.GROUP_INVITE] to *everyone* (existing members and new ones alike)
     * rather than [PayloadKind.GROUP_ROSTER_UPDATE] is deliberate - a brand-new member has
     * no local [GroupEntity] to update yet and needs the same bootstrap a founding member
     * gets, while [handleGroupInvite] is already idempotent for members who do have one
     * (it only ever upserts, never deletes), so one message shape correctly serves both.
     */
    fun addGroupMembers(groupId: String, newContactIds: List<String>) {
        scope.launch {
            val group = groupDao.find(groupId) ?: return@launch
            val myId = identityKeyManager.contactId()
            if (!canManage(group)) return@launch
            claimOwnershipIfLegacy(group)
            val existing = groupMemberDao.findMembers(groupId)
            val toAdd = newContactIds.filter { id -> id != myId && existing.none { it.contactId == id } }.mapNotNull { contactDao.find(it) }
            if (toAdd.isEmpty()) return@launch
            toAdd.forEach { groupMemberDao.upsert(GroupMemberEntity(groupId, it.contactId, it.nickname)) }

            val me = myHertzId() ?: return@launch
            val allMembers = groupMemberDao.findMembers(groupId)
            val roster = listOf(me) + allMembers.mapNotNull { m -> contactDao.find(m.contactId)?.let { HertzId(it.contactId, it.nickname, Base64.encodeToString(it.identityKeyBytes, Base64.NO_WRAP), it.i2pDestination) } }
            val invite = ChatPayload(
                UUID.randomUUID().toString(), System.currentTimeMillis(), PayloadKind.GROUP_INVITE,
                groupId = groupId, groupName = group.name, groupMembers = roster, groupOwnerId = myId,
            )
            coroutineScope {
                allMembers.map { async { trySendPayload(it.contactId, invite) } }.awaitAll()
            }
        }
    }

    /** Owner-only: removes a member and tells every remaining member (and the removed one) the resulting roster - the removed member's own client is what turns "I'm not in this list" into actually leaving. */
    fun removeGroupMember(groupId: String, contactId: String) {
        scope.launch {
            val group = groupDao.find(groupId) ?: return@launch
            val myId = identityKeyManager.contactId()
            if (!canManage(group) || contactId == myId) return@launch
            contactDao.find(contactId) ?: return@launch
            val managed = claimOwnershipIfLegacy(group)
            // Local state updates instantly; the notifications fan out in the
            // background instead of blocking the member list on slow dials.
            groupMemberDao.deleteMember(groupId, contactId)
            scope.launch {
                trySendPayload(
                    contactId,
                    ChatPayload(
                        UUID.randomUUID().toString(), System.currentTimeMillis(), PayloadKind.GROUP_ROSTER_UPDATE,
                        groupId = groupId, groupName = managed.name, groupOwnerId = myId,
                        groupRoster = listOfNotNull(myHertzId()),
                    ),
                )
                broadcastRoster(managed)
            }
        }
    }

    /**
     * Pushes everything group-shaped that [contactId] may have missed while away:
     * a fresh invite for every group we own with them in it (invites are
     * fire-and-forget, so without this a member added while offline never sees
     * the group appear), plus a GROUP_DELETE per tombstone. Called when they prove
     * reachable - a HELLO - never on a timer, so it costs nothing when idle.
     */
    fun syncGroupStateTo(contactId: String) {
        scope.launch {
            val myId = identityKeyManager.contactId()
            runCatching { groupDao.allGroups() }.getOrDefault(emptyList())
                .filter { it.ownerId == myId }
                .forEach { group ->
                    val members = groupMemberDao.findMembers(group.groupId)
                    if (members.none { it.contactId == contactId }) return@forEach
                    groupInvitePayload(group)?.let { trySendPayload(contactId, it) }
                }
            identityKeyManager.deletedGroupIds.forEach { groupId ->
                trySendPayload(
                    contactId,
                    ChatPayload(UUID.randomUUID().toString(), System.currentTimeMillis(), PayloadKind.GROUP_DELETE, groupId = groupId),
                )
            }
        }
    }

    /** The same bootstrap invite [addGroupMembers] sends - extracted so late joiners get the identical shape. */
    private suspend fun groupInvitePayload(group: GroupEntity): ChatPayload? {
        val myId = identityKeyManager.contactId()
        val me = myHertzId() ?: return null
        val members = groupMemberDao.findMembers(group.groupId)
        val roster = listOf(me) + members.mapNotNull { m ->
            contactDao.find(m.contactId)?.let { HertzId(it.contactId, it.nickname, Base64.encodeToString(it.identityKeyBytes, Base64.NO_WRAP), it.i2pDestination) }
        }
        return ChatPayload(
            UUID.randomUUID().toString(), System.currentTimeMillis(), PayloadKind.GROUP_INVITE,
            groupId = group.groupId, groupName = group.name, groupMembers = roster, groupOwnerId = myId,
        )
    }

    private suspend fun broadcastRoster(group: GroupEntity) {
        val myId = identityKeyManager.contactId()
        val me = myHertzId() ?: return
        val members = groupMemberDao.findMembers(group.groupId)
        val roster = listOf(me) + members.mapNotNull { m -> contactDao.find(m.contactId)?.let { HertzId(it.contactId, it.nickname, Base64.encodeToString(it.identityKeyBytes, Base64.NO_WRAP), it.i2pDestination) } }
        val payload = ChatPayload(
            UUID.randomUUID().toString(), System.currentTimeMillis(), PayloadKind.GROUP_ROSTER_UPDATE,
            groupId = group.groupId, groupName = group.name, groupOwnerId = myId, groupRoster = roster,
        )
        coroutineScope {
            members.map { async { trySendPayload(it.contactId, payload) } }.awaitAll()
        }
    }

    private suspend fun handleGroupRosterUpdate(fromContactId: String, payload: ChatPayload) {
        val groupId = payload.groupId ?: return
        val group = groupDao.find(groupId) ?: return
        // Only the owner we already recorded for this group may change its membership -
        // otherwise any group member could forge a roster update and remove or add people.
        if (group.ownerId.isNotBlank() && fromContactId != group.ownerId) return
        val roster = payload.groupRoster ?: return
        val myId = identityKeyManager.contactId()

        if (roster.none { it.contactId == myId }) {
            // We're not in the new roster: the owner removed us. Leave the same way
            // leaveGroup() does - there's nothing left here to be a member of.
            groupMemberDao.deleteAllForGroup(groupId)
            groupDao.delete(groupId)
            messageDao.deleteAllForContact(groupId)
            return
        }

        payload.groupName?.let { name -> groupDao.upsert(group.copy(name = name)) }
        groupMemberDao.deleteAllForGroup(groupId)
        roster.filter { it.contactId != myId }.forEach { member ->
            groupMemberDao.upsert(GroupMemberEntity(groupId, member.contactId, member.nickname))
            if (member.contactId != fromContactId && contactDao.find(member.contactId) == null) {
                // Same auto-introduction as a fresh invite: a newly-added member we don't
                // already know, vouched for by both of us trusting the group's owner.
                scope.launch { sendFriendRequest(member, viaGroupId = groupId) }
            }
        }
    }

    /**
     * Honours a group delete only from the owner we recorded - a forged delete
     * from anyone else would be a one-packet nuke of somebody's group.
     */
    private suspend fun handleGroupDelete(fromContactId: String, payload: ChatPayload) {
        val groupId = payload.groupId ?: return
        val group = groupDao.find(groupId) ?: return
        if (group.ownerId.isNotBlank() && fromContactId != group.ownerId) return
        wipeGroupLocally(groupId)
    }

    private suspend fun handleGroupInvite(fromContactId: String, payload: ChatPayload) {
        val groupId = payload.groupId ?: return
        val name = payload.groupName ?: return
        val roster = payload.groupMembers ?: return
        val myContactId = identityKeyManager.contactId()

        // Also arrives for a group we already have (addGroupMembers re-invites everyone) -
        // an unconditional new GroupEntity here would reset pinned/createdAt every time.
        val existingGroup = groupDao.find(groupId)
        groupDao.upsert(
            existingGroup?.copy(name = name, ownerId = payload.groupOwnerId ?: existingGroup.ownerId)
                ?: GroupEntity(groupId, name, createdAt = System.currentTimeMillis(), ownerId = payload.groupOwnerId.orEmpty()),
        )
        roster.filter { it.contactId != myContactId }.forEach { member ->
            groupMemberDao.upsert(GroupMemberEntity(groupId, member.contactId, member.nickname))
            if (member.contactId != fromContactId && contactDao.find(member.contactId) == null) {
                // Don't already know this member (they didn't invite us directly) - auto-introduce
                // ourselves, vouched for by both of us already trusting the group's creator.
                scope.launch { sendFriendRequest(member, viaGroupId = groupId) }
            }
        }
    }

    // --- @mentions ---

    /** Resolves `@nickname` tokens in a group message to contactIds, purely for the "you were mentioned" notification - not sent over the wire, every member re-derives it locally from the same roster. */
    suspend fun resolveMentions(groupId: String, text: String): List<String> {
        val ids = mutableListOf<String>()
        groupMemberDao.findMembers(groupId).forEach { member ->
            if (Regex("@" + Regex.escape(member.nickname) + "\\b").containsMatchIn(text)) ids += member.contactId
        }
        return ids
    }

    // --- Messaging ---

    fun sendText(contactId: String, text: String) {
        val now = System.currentTimeMillis()
        val payload = ChatPayload(
            messageId = UUID.randomUUID().toString(),
            sentAt = now,
            kind = PayloadKind.TEXT,
            seq = nextSeq(now),
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
                    seq = payload.seq,
                    deliveryState = DeliveryState.PENDING,
                ),
            )
            if (isSelf(contactId)) {
                messageDao.updateState(payload.messageId, DeliveryState.DELIVERED)
            } else {
                val delivered = trySendPayload(contactId, payload)
                messageDao.updateState(payload.messageId, if (delivered) DeliveryState.SENT else DeliveryState.PENDING)
            }
        }
    }

    /** Fans the message out individually to every member (each already has its own 1:1 Signal session - there's no group sender-key ratchet here). */
    fun sendGroupText(groupId: String, text: String) {
        scope.launch {
            val members = groupMemberDao.findMembers(groupId)
            val mentions = resolveMentions(groupId, text)
            val messageId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val seq = nextSeq(now)
            messageDao.upsert(
                MessageEntity(
                    messageId = messageId,
                    contactId = groupId,
                    fromMe = true,
                    type = MessageType.TEXT,
                    text = text,
                    timestamp = now,
                    seq = seq,
                    deliveryState = DeliveryState.PENDING,
                    mentionedContactIds = mentions.takeIf { it.isNotEmpty() }?.joinToString(","),
                ),
            )
            if (members.isEmpty()) {
                // Solo group (just me) - a note to self has already arrived the
                // moment it's written, same as the self 1:1 chat.
                messageDao.updateState(messageId, DeliveryState.DELIVERED)
                return@launch
            }
            val payload = ChatPayload(messageId, now, PayloadKind.TEXT, seq = seq, text = text, groupId = groupId)
            // Members dial in parallel - sequential dials would multiply one
            // slow I2P handshake by the member count on every group message.
            val delivered = coroutineScope {
                members.map { async { trySendPayload(it.contactId, payload) } }.awaitAll().any { it }
            }
            messageDao.updateState(messageId, if (delivered) DeliveryState.SENT else DeliveryState.PENDING)
        }
    }

    /** Returns true if the envelope made it onto a connection successfully - not a delivery receipt, just "left this device". */
    /**
     * encrypt() is what actually threw the NoSessionException that used to crash the app
     * outright (see the FriendResponsePayload fix above for why a session could be
     * missing in the first place) - wrapped now so any future encryption failure fails
     * this one send instead of taking the whole app down. A message that can't be
     * encrypted can't be sent either way, so "not delivered" is the correct outcome,
     * not a crash.
     */
    /** Sends one call-signaling or audio packet - through the same E2E session as chat, so calls are encrypted exactly like messages. */
    suspend fun sendCallPayload(contactId: String, payload: ChatPayload): Boolean = trySendPayload(contactId, payload)

    private suspend fun trySendPayload(contactId: String, payload: ChatPayload): Boolean = runCatching {
        val contact = contactDao.find(contactId) ?: return false
        // Every payload carries who we currently are - the receiver syncs our
        // nickname/avatar off ordinary traffic, so an offline peer catches up
        // on the next message instead of missing a one-shot broadcast forever.
        val stamped = payload.copy(
            senderNickname = identityKeyManager.nickname,
            senderAvatarHash = myAvatarHash(),
            senderI2PDest = identityKeyManager.i2pDestination.takeIf { it.isNotBlank() },
        )
        val envelope = cipherFor(contactId).encrypt(json.encodeToString(stamped).encodeToByteArray())
        val frameType = if (envelope.isPreKeyMessage) FRAME_PREKEY else FRAME_MESSAGE
        sendFrameTo(contactId, contact.i2pDestination, frame(frameType, envelope.ciphertext))
    }.getOrDefault(false)

    private suspend fun sendFrameTo(contactId: String, i2pDestination: String, bytes: ByteArray): Boolean {
        val cached = outgoingConnections[contactId]
        if (cached != null) {
            val ok = runCatching { withTimeout(SEND_TIMEOUT_MS) { cached.send(bytes) } }.isSuccess
            if (ok) return true
            // Dead connection: drop it so the re-dial below (and the next send)
            // doesn't trip over the same corpse again. Closing also unblocks the
            // timed-out write still stuck in its socket.
            outgoingConnections.remove(contactId, cached)
            runCatching { cached.close() }
        }
        // Parallel sends to the same peer (a tap on send racing the retry sweep
        // or a flush) used to dial in parallel: each multi-second I2P dial then
        // evicted the previous connection out from under its in-flight write,
        // failing messages that had a working socket - the flakiness that read
        // as "the app is unstable". One dial per contact at a time; whoever
        // waited rechecks the cache first, since the winner already connected.
        // The dial keeps its own (longer) connect timeout - only the write is
        // bounded, so a slow-but-working I2P dial is never cut short.
        return dialMutexFor(contactId).withLock {
            outgoingConnections[contactId]?.let { winner ->
                if (runCatching { withTimeout(SEND_TIMEOUT_MS) { winner.send(bytes) } }.isSuccess) return@withLock true
                outgoingConnections.remove(contactId, winner)
                runCatching { winner.close() }
            }
            runCatching {
                val fresh = dialAndRegister(contactId, i2pDestination)
                withTimeout(SEND_TIMEOUT_MS) { fresh.send(bytes) }
            }.isSuccess
        }
    }

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
        registerOutgoing(contactId, connection)
        scope.launch { readLoop(contactId, connection) }
        connection
    }

    private fun registerOutgoing(contactId: String, connection: P2pConnection) {
        outgoingConnections.put(contactId, connection)?.let { old -> if (old !== connection) old.close() }
    }

    private fun registerIncoming(contactId: String, connection: P2pConnection) {
        incomingConnections.put(contactId, connection)?.let { old -> if (old !== connection) old.close() }
    }

    // --- Media ---

    /**
     * Large-file variant: the staged file is copied and chunked straight off disk,
     * so sending a video or APK holds one 16KB chunk in RAM instead of the whole
     * file. Chunks stream from the local copy (not the staged file, which the caller
     * deletes right after this returns). Must be called off the main thread.
     */
    fun sendMedia(contactId: String, sourceFile: File, mimeType: String, kind: PayloadKind, fileName: String?, durationMs: Long? = null) {
        val size = sourceFile.length()
        val transferId = UUID.randomUUID()
        val key = MediaCrypto.generateKey()
        val nonceSalt = MediaCrypto.generateNonceSalt()
        val extension = mediaStorage.extensionFor(mimeType)
        val localCopy = runCatching { mediaStorage.newOutgoingCopyFromFile(sourceFile, extension) }.getOrNull()
        val messageId = UUID.randomUUID().toString()
        val chunkCount = FileChunks.chunkCount(size, MediaCrypto.CHUNK_SIZE).coerceAtLeast(1)

        val now = System.currentTimeMillis()
        val control = ChatPayload(
            messageId = messageId,
            sentAt = now,
            kind = kind,
            seq = nextSeq(now),
            mediaMimeType = mimeType,
            mediaFileName = fileName,
            mediaSizeBytes = size,
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
                    mediaPath = localCopy?.absolutePath,
                    mediaMimeType = mimeType,
                    mediaFileName = fileName,
                    mediaDurationMs = durationMs,
                    timestamp = control.sentAt,
                    seq = control.seq,
                    deliveryState = DeliveryState.PENDING,
                ),
            )

            if (localCopy == null || size <= 0) {
                messageDao.updateState(messageId, DeliveryState.FAILED)
                return@launch
            }

            if (isSelf(contactId)) {
                messageDao.updateState(messageId, DeliveryState.DELIVERED)
                return@launch
            }

            val contact = contactDao.find(contactId)
            val delivered = contact != null && runCatching {
                check(trySendPayload(contactId, control))
                check(sendChunkStreamFromFile(contactId, contact.i2pDestination, transferId, key, nonceSalt, localCopy, chunkCount))
            }.isSuccess
            messageDao.updateState(messageId, if (delivered) DeliveryState.SENT else DeliveryState.PENDING)
        }
    }

    fun sendMedia(contactId: String, sourceBytes: ByteArray, mimeType: String, kind: PayloadKind, fileName: String?, durationMs: Long? = null) {
        val transferId = UUID.randomUUID()
        val key = MediaCrypto.generateKey()
        val nonceSalt = MediaCrypto.generateNonceSalt()
        val extension = mediaStorage.extensionFor(mimeType)
        val localCopy = mediaStorage.newOutgoingCopy(sourceBytes, extension)
        val messageId = UUID.randomUUID().toString()
        val chunkCount = (sourceBytes.size + MediaCrypto.CHUNK_SIZE - 1) / MediaCrypto.CHUNK_SIZE

        val now = System.currentTimeMillis()
        val control = ChatPayload(
            messageId = messageId,
            sentAt = now,
            kind = kind,
            seq = nextSeq(now),
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
                    seq = control.seq,
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
                check(sendChunkStream(contactId, contact.i2pDestination, transferId, key, nonceSalt, sourceBytes))
            }.isSuccess
            messageDao.updateState(messageId, if (delivered) DeliveryState.SENT else DeliveryState.PENDING)
        }
    }

    /**
     * Streams encrypted chunks over whatever connection is currently alive, re-attempting
     * each chunk a few times (sendFrameTo re-dials between attempts). Without per-chunk
     * retries, one failed chunk out of hundreds abandons the whole photo - over flaky
     * I2P that makes large attachments effectively undeliverable.
     */
    private suspend fun sendChunkStream(contactId: String, i2pDestination: String, transferId: UUID, key: ByteArray, nonceSalt: ByteArray, sourceBytes: ByteArray): Boolean {
        var offset = 0
        var index = 0
        while (offset < sourceBytes.size) {
            val end = minOf(offset + MediaCrypto.CHUNK_SIZE, sourceBytes.size)
            if (!sendOneChunk(contactId, i2pDestination, transferId, key, nonceSalt, index, sourceBytes.copyOfRange(offset, end))) return false
            offset = end
            index++
        }
        return true
    }

    /**
     * The large-file variant: reads 16KB slices straight off disk, so sending a
     * video or APK holds one chunk in RAM instead of the whole file. Random access
     * (not a forward stream) so a retried chunk re-reads the same bytes.
     */
    private suspend fun sendChunkStreamFromFile(contactId: String, i2pDestination: String, transferId: UUID, key: ByteArray, nonceSalt: ByteArray, sourceFile: File, chunkCount: Int): Boolean {
        return runCatching {
            FileChunks.Reader(sourceFile, MediaCrypto.CHUNK_SIZE).use { reader ->
                if (reader.chunkCount != chunkCount) return@runCatching false
                repeat(chunkCount) { index ->
                    if (!sendOneChunk(contactId, i2pDestination, transferId, key, nonceSalt, index, reader.readChunk(index))) return@runCatching false
                }
            }
            true
        }.getOrDefault(false)
    }

    /** Encrypts one chunk and pushes it through the per-chunk retry loop. */
    private suspend fun sendOneChunk(contactId: String, i2pDestination: String, transferId: UUID, key: ByteArray, nonceSalt: ByteArray, index: Int, plaintext: ByteArray): Boolean {
        val cipherChunk = MediaCrypto.encryptChunk(key, nonceSalt, index, plaintext)
        var sent = false
        var attempts = 0
        while (!sent && attempts < CHUNK_ATTEMPTS) {
            sent = sendFrameTo(contactId, i2pDestination, frameMediaChunk(transferId, index, cipherChunk))
            attempts++
        }
        return sent
    }

    /**
     * Group media, fanned out per member like [sendGroupText] is. The media itself is
     * encrypted once with one symmetric key (the bytes on the wire are identical for
     * every member), but that key travels inside the per-member Signal-encrypted control
     * payload - so each member unwraps it through their own ratchet and there's still no
     * shared group key anywhere.
     */
    /**
     * Large-file variant of group send - same off-disk chunking as [sendMedia],
     * fanned out per member. Must be called off the main thread.
     */
    fun sendGroupMedia(groupId: String, sourceFile: File, mimeType: String, kind: PayloadKind, fileName: String?, durationMs: Long? = null) {
        val size = sourceFile.length()
        val transferId = UUID.randomUUID()
        val key = MediaCrypto.generateKey()
        val nonceSalt = MediaCrypto.generateNonceSalt()
        val extension = mediaStorage.extensionFor(mimeType)
        val localCopy = runCatching { mediaStorage.newOutgoingCopyFromFile(sourceFile, extension) }.getOrNull()
        val messageId = UUID.randomUUID().toString()
        val chunkCount = FileChunks.chunkCount(size, MediaCrypto.CHUNK_SIZE).coerceAtLeast(1)

        val now = System.currentTimeMillis()
        val control = ChatPayload(
            messageId = messageId,
            sentAt = now,
            kind = kind,
            seq = nextSeq(now),
            mediaMimeType = mimeType,
            mediaFileName = fileName,
            mediaSizeBytes = size,
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
                    mediaPath = localCopy?.absolutePath,
                    mediaMimeType = mimeType,
                    mediaFileName = fileName,
                    mediaDurationMs = durationMs,
                    timestamp = control.sentAt,
                    seq = control.seq,
                    deliveryState = DeliveryState.PENDING,
                ),
            )

            if (localCopy == null || size <= 0) {
                messageDao.updateState(messageId, DeliveryState.FAILED)
                return@launch
            }

            val members = groupMemberDao.findMembers(groupId)
            if (members.isEmpty()) {
                // Solo group (just me) - see sendGroupText.
                messageDao.updateState(messageId, DeliveryState.DELIVERED)
                return@launch
            }
            val delivered = coroutineScope {
                members.map { member ->
                    async {
                        val contact = contactDao.find(member.contactId)
                        contact != null && runCatching {
                            check(trySendPayload(member.contactId, control))
                            check(sendChunkStreamFromFile(member.contactId, contact.i2pDestination, transferId, key, nonceSalt, localCopy, chunkCount))
                        }.isSuccess
                    }
                }.awaitAll().any { it }
            }
            messageDao.updateState(messageId, if (delivered) DeliveryState.SENT else DeliveryState.PENDING)
        }
    }

    fun sendGroupMedia(groupId: String, sourceBytes: ByteArray, mimeType: String, kind: PayloadKind, fileName: String?, durationMs: Long? = null) {
        val transferId = UUID.randomUUID()
        val key = MediaCrypto.generateKey()
        val nonceSalt = MediaCrypto.generateNonceSalt()
        val extension = mediaStorage.extensionFor(mimeType)
        val localCopy = mediaStorage.newOutgoingCopy(sourceBytes, extension)
        val messageId = UUID.randomUUID().toString()
        val chunkCount = (sourceBytes.size + MediaCrypto.CHUNK_SIZE - 1) / MediaCrypto.CHUNK_SIZE

        val now = System.currentTimeMillis()
        val control = ChatPayload(
            messageId = messageId,
            sentAt = now,
            kind = kind,
            seq = nextSeq(now),
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
                    seq = control.seq,
                    deliveryState = DeliveryState.PENDING,
                ),
            )

            val members = groupMemberDao.findMembers(groupId)
            if (members.isEmpty()) {
                // Solo group (just me) - see sendGroupText.
                messageDao.updateState(messageId, DeliveryState.DELIVERED)
                return@launch
            }
            val delivered = coroutineScope {
                members.map { member ->
                    async {
                        val contact = contactDao.find(member.contactId)
                        contact != null && runCatching {
                            check(trySendPayload(member.contactId, control))
                            check(sendChunkStream(member.contactId, contact.i2pDestination, transferId, key, nonceSalt, sourceBytes))
                        }.isSuccess
                    }
                }.awaitAll().any { it }
            }
            messageDao.updateState(messageId, if (delivered) DeliveryState.SENT else DeliveryState.PENDING)
        }
    }

    // --- Avatar ---

    /** Saves the new avatar locally and pushes it to every existing contact. */
    fun updateMyAvatar(jpegBytes: ByteArray) {
        // Called from the profile screen: writing a full-size JPEG is disk I/O and
        // belongs off the main thread, same as everything else in here.
        scope.launch {
            mediaStorage.selfAvatarFile().writeBytes(ImageEditor.downscaleAvatar(jpegBytes))
            cachedAvatarHashValid = false
            broadcastProfile()
        }
    }

    /** Commits a new own nickname locally, fixes the self row (which the self chat and list read), and pushes it to every contact. */
    fun updateMyNickname(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed == identityKeyManager.nickname) return
        identityKeyManager.nickname = trimmed
        scope.launch {
            val myId = identityKeyManager.contactId()
            contactDao.find(myId)?.let { contactDao.update(it.copy(nickname = trimmed)) }
        }
        broadcastProfile()
    }

    /** Hash of our current avatar for the per-payload profile stamp - cached, the file only changes through [updateMyAvatar]. Null when we have no photo. */
    private fun myAvatarHash(): String? {
        if (cachedAvatarHashValid) return cachedAvatarHash
        val file = mediaStorage.selfAvatarFile().takeIf { it.exists() }
        cachedAvatarHash = file?.let { runCatching { sha256Hex(it.readBytes()) }.getOrNull() }
        cachedAvatarHashValid = true
        return cachedAvatarHash
    }

    private fun sha256Hex(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /**
     * Applies the sender profile stamped on an incoming payload: the nickname lands
     * directly, and a mismatched avatar hash pulls the full photo. Skipped for call
     * audio (50 packets a second would re-hash for nothing - the call signaling
     * around it already carries the same stamp) and for our own loopback.
     */
    private fun applySenderProfile(contactId: String, payload: ChatPayload) {
        if (payload.kind == PayloadKind.CALL_AUDIO) return
        if (payload.senderNickname == null && payload.senderAvatarHash == null && payload.senderI2PDest == null) return
        if (contactId == identityKeyManager.contactId()) return
        scope.launch {
            val contact = contactDao.find(contactId) ?: return@launch
            val nickname = payload.senderNickname?.trim().orEmpty()
            var updated = contact
            if (nickname.isNotEmpty() && nickname != contact.nickname) {
                updated = updated.copy(nickname = nickname)
            }
            val i2pDest = payload.senderI2PDest
            if (i2pDest != null && i2pDest.isNotBlank() && i2pDest != contact.i2pDestination) {
                updated = updated.copy(i2pDestination = i2pDest)
            }
            if (updated != contact) contactDao.update(updated)
            val remoteHash = payload.senderAvatarHash
            val localHash = contact.avatarPath?.let { File(it) }?.takeIf { it.exists() }
                ?.let { runCatching { sha256Hex(it.readBytes()) }.getOrNull() }
            if (remoteHash == localHash) return@launch
            if (remoteHash == null) {
                contact.avatarPath?.let { runCatching { File(it).delete() } }
                contactDao.find(contactId)?.let { contactDao.update(it.copy(avatarPath = null)) }
            } else if (requestedAvatarHashes[contactId] != remoteHash) {
                requestedAvatarHashes[contactId] = remoteHash
                requestProfile(contactId)
            }
        }
    }

    /**
     * Pushes our current nickname (and avatar, if any) to every contact, so both update
     * on their side without anyone re-scanning a QR code. Called after an avatar change
     * and after a nickname change alike - profile data is tiny and changes are rare.
     */
    fun broadcastProfile() {
        scope.launch {
            val myId = identityKeyManager.contactId()
            contactDao.observeContacts().first().forEach { contact ->
                if (contact.contactId == myId) return@forEach
                sendProfileTo(contact.contactId)
            }
        }
    }

    /** Pushes our current nickname and avatar to one contact - accept-time sync, profile pulls, broadcasts. */
    fun sendProfileTo(contactId: String) {
        scope.launch {
            runCatching {
                trySendPayload(
                    contactId,
                    ChatPayload(
                        UUID.randomUUID().toString(),
                        System.currentTimeMillis(),
                        PayloadKind.PROFILE_UPDATE,
                        profileNickname = identityKeyManager.nickname,
                        pushTopic = settingsRepository.pushTopic(),
                    ),
                )
            }
            mediaStorage.selfAvatarFile().takeIf { it.exists() }?.let { sendAvatarTo(contactId, ImageEditor.downscaleAvatar(it.readBytes())) }
        }
    }

    /**
     * Asks a contact to push their current profile back. Accept-time pushes alone
     * miss whenever the other side's session isn't usable yet - the pull closes the
     * loop, so a new contact's photo appears without them having to re-save it.
     */
    fun requestProfile(contactId: String) {
        scope.launch {
            runCatching {
                trySendPayload(
                    contactId,
                    ChatPayload(UUID.randomUUID().toString(), System.currentTimeMillis(), PayloadKind.PROFILE_REQUEST),
                )
            }
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
                check(sendChunkStream(contactId, contact.i2pDestination, transferId, key, nonceSalt, jpegBytes))
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
        // A re-solicited control for a transfer that already finished - just re-ack, don't rebuild anything.
        if (incomingTransfers.containsKey(transferId)) return
        val key = payload.mediaKeyBase64?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return
        val nonceSalt = payload.mediaNonceSaltBase64?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return
        val chunkCount = payload.mediaChunkCount ?: return
        val mimeType = payload.mediaMimeType ?: "application/octet-stream"
        val outputFile = if (payload.kind == PayloadKind.AVATAR) {
            mediaStorage.newContactAvatarFile(contactId)
        } else {
            mediaStorage.fileFor(transferId, mediaStorage.extensionFor(mimeType))
        }

        val transfer = IncomingTransfer(
            contactId = contactId,
            threadId = payload.groupId ?: contactId,
            senderContactId = payload.groupId?.let { contactId },
            messageId = payload.messageId,
            sentAt = payload.sentAt,
            seq = payload.seq,
            kind = payload.kind,
            key = key,
            nonceSalt = nonceSalt,
            chunkCount = chunkCount,
            mimeType = mimeType,
            fileName = payload.mediaFileName,
            durationMs = payload.mediaDurationMs,
            outputFile = outputFile,
            out = RandomAccessFile(outputFile, "rw"),
        )
        incomingTransfers[transferId] = transfer
        // Chunks that beat the control here get written now, at their own offsets.
        pendingChunks.remove(transferId)?.chunks?.forEach { (index, ciphertext) ->
            writeChunk(transfer, index, ciphertext)
        }
        maybeCompleteTransfer(transferId, transfer)
    }

    /** Writes one chunk at its own offset - order-independent, duplicate-safe. Returns false when the chunk is corrupt. */
    private fun writeChunk(transfer: IncomingTransfer, chunkIndex: Int, ciphertext: ByteArray): Boolean {
        if (chunkIndex < 0 || chunkIndex >= transfer.chunkCount) return false
        val plaintext = runCatching { MediaCrypto.decryptChunk(transfer.key, transfer.nonceSalt, chunkIndex, ciphertext) }.getOrNull() ?: return false
        synchronized(transfer) {
            if (!transfer.receivedIndices.add(chunkIndex)) return true // retried duplicate - already have it
            runCatching {
                transfer.out.seek(chunkIndex.toLong() * MediaCrypto.CHUNK_SIZE)
                transfer.out.write(plaintext)
            }.getOrElse { return false }
        }
        return true
    }

    private fun onMediaChunkReceived(bytes: ByteArray) {
        if (bytes.size < 1 + 16 + 4) return
        val buffer = java.nio.ByteBuffer.wrap(bytes, 1, bytes.size - 1)
        val transferId = UUID(buffer.long, buffer.long).toString()
        val chunkIndex = buffer.int
        val ciphertext = bytes.copyOfRange(1 + 16 + 4, bytes.size)

        val transfer = incomingTransfers[transferId]
        if (transfer == null) {
            // Control hasn't arrived yet - hold the chunk rather than dropping it.
            val pending = pendingChunks.getOrPut(transferId) { PendingChunks() }
            synchronized(pending) {
                if (pending.chunks.size < PENDING_CHUNKS_CAP) pending.chunks.putIfAbsent(chunkIndex, ciphertext)
            }
            return
        }
        if (!writeChunk(transfer, chunkIndex, ciphertext)) return
        maybeCompleteTransfer(transferId, transfer)
    }

    private fun maybeCompleteTransfer(transferId: String, transfer: IncomingTransfer) {
        val complete = synchronized(transfer) { transfer.receivedIndices.size >= transfer.chunkCount }
        if (!complete) return
        if (incomingTransfers.remove(transferId) == null) return
        runCatching { transfer.out.close() }

        if (transfer.kind == PayloadKind.AVATAR) {
            // The photo we pulled has landed - the hash guard lifts, so a
            // *newer* photo later pulls again instead of being swallowed.
            requestedAvatarHashes.remove(transfer.contactId)
            scope.launch {
                contactDao.find(transfer.contactId)?.let {
                    val previous = it.avatarPath?.let { path -> java.io.File(path) }
                    contactDao.update(it.copy(avatarPath = transfer.outputFile.absolutePath))
                    if (previous != null && previous.absolutePath != transfer.outputFile.absolutePath) {
                        runCatching { previous.delete() }
                    }
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
            timestamp = transfer.sentAt,
            seq = transfer.seq,
            deliveryState = DeliveryState.DELIVERED,
            senderContactId = transfer.senderContactId,
        )
        scope.launch {
            // Retries reuse the message id, so a completed transfer may simply be the
            // second copy of something already shown - store idempotently, but only
            // notify (and vibrate) the first time.
            if (messageDao.find(transfer.messageId) == null) {
                messageDao.upsert(entity)
                _events.tryEmit(ChatServiceEvent.MessageReceived(transfer.threadId, entity))
            }
        }
        sendDeliveredAck(transfer.contactId, transfer.messageId)
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
            runCatching { retryPendingRequests() }
        }
    }

    /**
     * Dials [contactId] in the background without sending anything - opening a chat
     * warms its peer, so the first message usually finds a live connection instead
     * of paying the multi-second I2P dial. No-op when already connected.
     */
    fun warmConnection(contactId: String) {
        if (outgoingConnections.containsKey(contactId)) return
        scope.launch {
            val contact = contactDao.find(contactId) ?: return@launch
            if (contact.contactId in blockedContactIds) return@launch
            dialMutexFor(contactId).withLock {
                if (outgoingConnections.containsKey(contactId)) return@withLock
                runCatching { dialAndRegister(contactId, contact.i2pDestination) }
            }
        }
    }

    /** Warms every member of [groupId] - a group send fans out, so its dials warm up the same way. */
    fun warmGroup(groupId: String) {
        scope.launch {
            runCatching { groupMemberDao.findMembers(groupId) }.getOrDefault(emptyList())
                .forEach { warmConnection(it.contactId) }
        }
    }

    /**
     * Cold-start killer: the moment I2P connects, dial the most recently active
     * 1:1 contacts in the background, so the user's first message finds live
     * sockets instead of paying a cold I2P dial (tunnel use plus lease lookup,
     * easily tens of seconds) on the send path. Capped small - this is a head
     * start, not a fan-out.
     */
    private fun warmRecentContacts() {
        scope.launch {
            val myId = identityKeyManager.contactId()
            val threads = runCatching { messageDao.recentThreadIds(WARMUP_CONTACTS) }.getOrDefault(emptyList())
                .filter { it != myId }
            coroutineScope {
                threads.map { async { warmConnection(it) } }.awaitAll()
            }
        }
    }

    private suspend fun heartbeatLoop() {
        while (started) {
            delay(HEARTBEAT_MS)
            // Only already-open connections: a ping must never dial, or every
            // heartbeat would wake every dead peer like a retry sweep. The ping
            // still carries the profile stamp, so it doubles as profile refresh.
            outgoingConnections.keys.forEach { contactId ->
                if (!outgoingConnections.containsKey(contactId)) return@forEach
                runCatching {
                    trySendPayload(contactId, ChatPayload(UUID.randomUUID().toString(), System.currentTimeMillis(), PayloadKind.PING))
                }
            }
        }
    }

    private suspend fun retryPendingNow() {
        // Overlapping sweeps would double-send everything; whoever is already
        // sweeping covers the newcomer, so a contended sweep just skips.
        if (!retryMutex.tryLock()) return
        try {
            sweepStaleTransfers()
            // Each thread gets its own lane: the sweep used to walk every unsent
            // message in one sequence, so a 45s dial timeout to one dead peer
            // head-of-line-blocked everything behind it and one unreachable
            // contact starved all the reachable ones for minutes. Within a lane
            // the sends stay ordered and stop at the first failure - if the
            // peer were up, the first message would have gone through.
            coroutineScope {
                messageDao.findUnsent().groupBy { it.contactId }.values.map { lane ->
                    async {
                        for (message in lane) {
                            if (!retryMessage(message)) {
                                // Direct P2P to this thread is down right now - wake the
                                // peer(s) with an empty ntfy ping so their worker starts
                                // the service and the queue can flow. Rate-limited inside.
                                pingThread(lane.first().contactId)
                                break
                            }
                        }
                    }
                }.awaitAll()
            }
        } finally {
            retryMutex.unlock()
        }
    }

    /** Wakes one thread's peer(s) after a failed retry sweep - a 1:1 thread pings its contact, a group thread pings every member. Never pings self. */
    private suspend fun pingThread(threadId: String) {
        if (groupDao.find(threadId) != null) {
            runCatching { groupMemberDao.findMembers(threadId) }.getOrDefault(emptyList())
                .forEach { member -> if (!isSelf(member.contactId)) pushPinger.maybePing(member.contactId) }
        } else if (!isSelf(threadId)) {
            pushPinger.maybePing(threadId)
        }
    }

    /** Pushes out everything waiting for one contact - called the moment they prove reachable (their HELLO just arrived), without waiting for the retry tick. */
    private suspend fun flushFor(contactId: String) {
        if (!retryMutex.tryLock()) return
        try {
            messageDao.findUnsent().forEach { message ->
                if (message.contactId == contactId) {
                    retryMessage(message)
                } else if (groupDao.find(message.contactId) != null &&
                    groupMemberDao.findMembers(message.contactId).any { it.contactId == contactId }
                ) {
                    retryMessage(message)
                }
            }
        } finally {
            retryMutex.unlock()
        }
    }

    /**
     * Returns false only when the send actually failed against a peer we tried to
     * reach - the sweep then stops that thread's lane (the peer is down, later
     * messages would fail the same way). Anything settled or skipped (confirmed,
     * blocked, unknown, file gone) returns true so the lane keeps moving.
     */
    private suspend fun retryMessage(message: MessageEntity): Boolean {
        val age = System.currentTimeMillis() - message.timestamp
        // SENT without a receipt past the grace period means the peer runs a version
        // that never sends receipts (or is gone) - confirm optimistically instead of
        // spinning under the message forever.
        if (message.deliveryState == DeliveryState.SENT && age > ACK_GRACE_MS) {
            messageDao.updateState(message.messageId, DeliveryState.DELIVERED)
            return true
        }
        // SENT but only briefly unacked: re-send the payload to solicit a fresh
        // receipt. A lost ack must never wedge the message at "sent" forever -
        // and re-sends are idempotent on the receiving side (same message id,
        // just another ack).
        val resolicit = message.deliveryState == DeliveryState.SENT && age > ACK_RESOLICIT_MS
        // Group threads live under the group id, not a contact id - resolving them as
        // contacts silently skipped every failed group message forever.
        if (groupDao.find(message.contactId) != null) {
            return retryGroupMessage(message.contactId, message)
        }
        val contact = contactDao.find(message.contactId) ?: return true
        if (contact.contactId in blockedContactIds) return true
        val delivered = if (message.type == MessageType.TEXT) {
            trySendPayload(message.contactId, ChatPayload(message.messageId, message.timestamp, PayloadKind.TEXT, seq = message.seq, text = message.text))
        } else {
            val file = message.mediaPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
            file != null && resendMedia(message, file, contact.i2pDestination, groupId = null, toContactId = message.contactId)
        }
        if (delivered && !resolicit) messageDao.updateState(message.messageId, DeliveryState.SENT)
        return delivered
    }

    private suspend fun retryGroupMessage(groupId: String, message: MessageEntity): Boolean {
        val resolicit = message.deliveryState == DeliveryState.SENT
        val members = groupMemberDao.findMembers(groupId)
        if (members.isEmpty()) {
            // Everyone left (or solo group) - nothing to send to, confirm locally.
            messageDao.updateState(message.messageId, DeliveryState.DELIVERED)
            return true
        }
        val delivered = if (message.type == MessageType.TEXT) {
            val payload = ChatPayload(message.messageId, message.timestamp, PayloadKind.TEXT, seq = message.seq, text = message.text, groupId = groupId)
            coroutineScope {
                members.map { async { trySendPayload(it.contactId, payload) } }.awaitAll().any { it }
            }
        } else {
            val file = message.mediaPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 } ?: return true
            coroutineScope {
                members.map { member ->
                    async {
                        val contact = contactDao.find(member.contactId) ?: return@async false
                        if (contact.contactId in blockedContactIds) return@async false
                        resendMedia(message, file, contact.i2pDestination, groupId = groupId, toContactId = member.contactId)
                    }
                }.awaitAll().any { it }
            }
        }
        if (delivered && !resolicit) messageDao.updateState(message.messageId, DeliveryState.SENT)
        return delivered
    }

    private suspend fun resendMedia(message: MessageEntity, sourceFile: File, i2pDestination: String, groupId: String?, toContactId: String): Boolean {
        // A retried media message reuses a fresh transfer id/key - the original attempt may have
        // partially landed on the peer's side, and re-keying is simpler and just as cheap as
        // trying to resume a specific byte offset.
        val transferId = UUID.randomUUID()
        val key = MediaCrypto.generateKey()
        val nonceSalt = MediaCrypto.generateNonceSalt()
        val size = sourceFile.length()
        val chunkCount = FileChunks.chunkCount(size, MediaCrypto.CHUNK_SIZE).coerceAtLeast(1)
        val control = ChatPayload(
            messageId = message.messageId,
            sentAt = message.timestamp,
            kind = when (message.type) {
                MessageType.IMAGE -> PayloadKind.IMAGE
                MessageType.VIDEO -> PayloadKind.VIDEO
                MessageType.VOICE -> PayloadKind.VOICE
                else -> PayloadKind.FILE
            },
            seq = message.seq,
            mediaMimeType = message.mediaMimeType,
            mediaFileName = message.mediaFileName,
            mediaSizeBytes = size,
            mediaDurationMs = message.mediaDurationMs,
            mediaTransferId = transferId.toString(),
            mediaKeyBase64 = Base64.encodeToString(key, Base64.NO_WRAP),
            mediaNonceSaltBase64 = Base64.encodeToString(nonceSalt, Base64.NO_WRAP),
            mediaChunkCount = chunkCount,
            groupId = groupId,
        )
        return runCatching {
            check(trySendPayload(toContactId, control))
            check(sendChunkStreamFromFile(toContactId, i2pDestination, transferId, key, nonceSalt, sourceFile, chunkCount))
        }.isSuccess
    }

    /** Confirms receipt back to the sender - this is what stops their spinner. Fire-and-forget: a lost receipt just means another retry cycle, never lost data. */
    /**
     * Confirms receipt back to the sender - this is what stops their spinner. Goes
     * out as a small burst: one lost ack used to wedge the sender's message at
     * "sent" even though it had arrived.
     */
    private fun sendDeliveredAck(contactId: String, ackForMessageId: String) {
        scope.launch {
            repeat(3) { attempt ->
                if (attempt > 0) delay(300)
                runCatching {
                    trySendPayload(
                        contactId,
                        ChatPayload(UUID.randomUUID().toString(), System.currentTimeMillis(), PayloadKind.DELIVERED_ACK, ackForMessageId = ackForMessageId),
                    )
                }
            }
        }
    }

    /**
     * Tells a 1:1 contact that everything they sent up to now has been seen -
     * this is what turns their double tick blue. Called when the chat screen's
     * read watermark advances; group threads are skipped (no group read
     * receipts - "seen by whom" needs a roster the receipts don't carry).
     */
    fun sendReadAcks(threadId: String) {
        scope.launch {
            if (groupDao.find(threadId) != null) return@launch
            contactDao.find(threadId) ?: return@launch
            messageDao.findDeliveredIncoming(threadId).forEach { message ->
                runCatching {
                    trySendPayload(
                        threadId,
                        ChatPayload(UUID.randomUUID().toString(), System.currentTimeMillis(), PayloadKind.READ_ACK, ackForMessageId = message.messageId),
                    )
                }
                messageDao.updateState(message.messageId, DeliveryState.READ)
            }
        }
    }

    /** Drops transfers whose chunks stopped arriving mid-photo: closes the leaked file handle and deletes the partial file. */
    private fun sweepStaleTransfers() {
        val cutoff = System.currentTimeMillis() - STALE_TRANSFER_MS
        val staleIds = incomingTransfers.filterValues { it.startedAt < cutoff }.keys.toList()
        staleIds.forEach { id ->
            incomingTransfers.remove(id)?.let { transfer ->
                runCatching { transfer.out.close() }
                runCatching { transfer.outputFile.delete() }
            }
        }
        pendingChunks.entries.removeIf { (_, pending) -> pending.startedAt < cutoff }
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
                            registerIncoming(claimed, connection)
                            // They just proved reachable - flush anything waiting
                            // for them instead of sitting until the retry tick, and
                            // push our profile: they may have missed every broadcast
                            // we sent while they were offline.
                            scope.launch { runCatching { flushFor(claimed) } }
                            sendProfileTo(claimed)
                            syncGroupStateTo(claimed)
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
                fromContactId?.let { id ->
                    if (incomingConnections[id] === connection) incomingConnections.remove(id)
                }
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
            if (outgoingConnections[contactId] === connection) outgoingConnections.remove(contactId)
            connection.close()
        }
    }

    private fun onEnvelopeReceived(contactId: String, bytes: ByteArray) {
        val envelope = EncryptedEnvelope(isPreKeyMessage = bytes[0] == FRAME_PREKEY, ciphertext = bytes.copyOfRange(1, bytes.size))
        val plaintext = runCatching { cipherFor(contactId).decrypt(envelope) }.getOrNull() ?: return
        val payload = runCatching { json.decodeFromString(ChatPayload.serializer(), plaintext.decodeToString()) }.getOrNull() ?: return

        applySenderProfile(contactId, payload)

        when (payload.kind) {
            PayloadKind.CALL_OFFER, PayloadKind.CALL_ANSWER, PayloadKind.CALL_REJECT, PayloadKind.CALL_HANGUP, PayloadKind.CALL_AUDIO ->
                _events.tryEmit(ChatServiceEvent.CallSignaling(contactId, payload))
            // Heartbeat: nothing to do - the profile stamp was already applied above.
            PayloadKind.PING -> Unit
            PayloadKind.IMAGE, PayloadKind.VIDEO, PayloadKind.VOICE, PayloadKind.FILE, PayloadKind.AVATAR -> scope.launch {
                // A re-solicited control for a transfer that already finished just re-acks.
                if (messageDao.find(payload.messageId) == null) {
                    beginIncomingTransfer(contactId, payload)
                } else {
                    sendDeliveredAck(contactId, payload.messageId)
                }
            }
            PayloadKind.GROUP_INVITE -> scope.launch { handleGroupInvite(contactId, payload) }
            PayloadKind.GROUP_ROSTER_UPDATE -> scope.launch { handleGroupRosterUpdate(contactId, payload) }
            PayloadKind.GROUP_DELETE -> scope.launch { handleGroupDelete(contactId, payload) }
            PayloadKind.DELIVERED_ACK -> scope.launch {
                payload.ackForMessageId?.let { messageDao.updateState(it, DeliveryState.DELIVERED) }
            }
            PayloadKind.READ_ACK -> scope.launch {
                payload.ackForMessageId?.let { messageDao.updateState(it, DeliveryState.READ) }
            }
            PayloadKind.PROFILE_UPDATE -> scope.launch {
                val nickname = payload.profileNickname?.trim().orEmpty()
                val topic = payload.pushTopic?.takeIf { cz.kuclab.hertzchat.p2p.NtfyPing.isValidTopic(it) }
                if (nickname.isNotEmpty() || topic != null) {
                    contactDao.find(contactId)?.let { contact ->
                        contactDao.update(contact.copy(nickname = nickname.ifEmpty { contact.nickname }, pushTopic = topic ?: contact.pushTopic))
                    }
                }
            }
            PayloadKind.PROFILE_REQUEST -> sendProfileTo(contactId)
            PayloadKind.TEXT -> {
                val threadId = payload.groupId ?: contactId
                scope.launch {
                    // Same dedup as media below: retried texts reuse the message id.
                    if (messageDao.find(payload.messageId) == null) {
                        val mentions = if (payload.groupId != null) resolveMentions(payload.groupId, payload.text.orEmpty()) else emptyList()
                        val entity = MessageEntity(
                            messageId = payload.messageId,
                            contactId = threadId,
                            fromMe = false,
                            type = MessageType.TEXT,
                            text = payload.text,
                            timestamp = payload.sentAt,
                            seq = payload.seq,
                            deliveryState = DeliveryState.DELIVERED,
                            senderContactId = if (payload.groupId != null) contactId else null,
                            mentionedContactIds = mentions.takeIf { it.isNotEmpty() }?.joinToString(","),
                        )
                        messageDao.upsert(entity)
                        _events.tryEmit(ChatServiceEvent.MessageReceived(threadId, entity))
                    }
                }
                sendDeliveredAck(contactId, payload.messageId)
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
        forgetPendingRequest(senderContactId)
        scope.launch {
            addTrustedContact(senderContactId, response.nickname, response.identityKeyBase64, response.i2pDestination, response.pushTopic)
            // Mirrors what the accepter did with our bundle in respondFriendRequest - without
            // this, we (the original requester) would have no session and encrypt() to this
            // contact would throw NoSessionException on the very first message we sent.
            response.preKeyBundle?.let { bundle ->
                runCatching { cipherFor(senderContactId).establishSessionFromBundle(bundle.toPreKeyBundle()) }
            }
            sendProfileTo(senderContactId)
            requestProfile(senderContactId)
        }
    }

    private fun frame(type: Byte, payload: ByteArray): ByteArray = byteArrayOf(type) + payload
}
