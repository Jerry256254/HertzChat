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
import cz.kuclab.hertzchat.media.FileChunks
import cz.kuclab.hertzchat.media.ImageEditor
import cz.kuclab.hertzchat.media.MediaCrypto
import cz.kuclab.hertzchat.media.MediaStorage
import cz.kuclab.hertzchat.network.p2p.FriendRequestPayload
import cz.kuclab.hertzchat.network.p2p.FriendResponsePayload
import cz.kuclab.hertzchat.network.p2p.HertzId
import cz.kuclab.hertzchat.network.p2p.LanTransport
import cz.kuclab.hertzchat.network.p2p.P2pConnection
import cz.kuclab.hertzchat.network.p2p.SealedWire
import cz.kuclab.hertzchat.network.relay.NostrCrypto
import cz.kuclab.hertzchat.network.relay.NostrProtocol
import cz.kuclab.hertzchat.network.relay.RelayState
import cz.kuclab.hertzchat.network.relay.RelayTransport
import java.io.File
import java.io.RandomAccessFile
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
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

// Wire framing prefix byte for one framed payload - the same bytes travel over
// a LAN socket frame and inside a relay event's content, so everything above
// this layer (Signal sessions, media chunking, delivery state) is identical
// and transport-agnostic.
private const val FRAME_HELLO: Byte = 0
private const val FRAME_MESSAGE: Byte = 1
private const val FRAME_PREKEY: Byte = 2
private const val FRAME_MEDIA_CHUNK: Byte = 3
private const val FRAME_FRIEND_REQUEST: Byte = 4
private const val FRAME_FRIEND_RESPONSE: Byte = 5

private const val RETRY_INTERVAL_MS = 15_000L
/** Idle open LAN connections get a tiny ping on this cadence, so NAT bindings stay warm instead of dying between messages. */
private const val HEARTBEAT_MS = 180_000L
/** A LAN send that doesn't leave the device within this long is treated as a dead connection and re-dialled, instead of blocking behind TCP retransmits. */
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
/** How often a contact we hear from gets a fresh group-state sync at most. */
private const val GROUP_SYNC_INTERVAL_MS = 24 * 60 * 60_000L
/** Chunks buffered for transfers whose control payload hasn't arrived yet, per transfer - past this the sender is presumed broken and extras drop. */
private const val PENDING_CHUNKS_CAP = 1024

data class IncomingFriendRequest(val contactId: String, val nickname: String, val request: FriendRequestPayload)

sealed interface ChatServiceEvent {
    data class FriendRequestReceived(val request: IncomingFriendRequest) : ChatServiceEvent
    data class MessageReceived(val threadId: String, val message: MessageEntity) : ChatServiceEvent
    /** One decrypted call packet (signaling or audio) - consumed by CallManager, never stored. */
    data class CallSignaling(val contactId: String, val payload: ChatPayload) : ChatServiceEvent
}

/**
 * Orchestrates the whole messaging pipeline: free public relay servers carry
 * opaque *ephemeral* events between devices (they forward to whoever is
 * subscribed right now and store nothing - no messages, no timestamps, no logs
 * of who talked to whom), and the Signal Protocol session per contact is the
 * end-to-end encryption. A message never exists in plaintext anywhere except
 * on the two devices party to the conversation, and thanks to per-event
 * ephemeral sender keys plus pairwise routing tags the relay can't even tell
 * who sent what to whom.
 *
 * Same local network still wins when available: LAN sockets are direct,
 * near-instant and work with no internet at all. The relay covers everything
 * else.
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
    private val relayTransport: RelayTransport,
    private val lanTransport: LanTransport,
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * LAN sockets we dialled, by contact - the LAN send path. Kept strictly
     * separate from [incomingConnections]: the old single slot let an inbound
     * dial evict (and close) our outbound socket mid-write, which read exactly
     * as "receiving works but sending is stuck" whenever both sides talked at
     * once.
     */
    private val outgoingConnections = ConcurrentHashMap<String, P2pConnection>()
    /** LAN sockets peers dialled to us, by contact - the LAN receive path. Never used for sending. */
    private val incomingConnections = ConcurrentHashMap<String, P2pConnection>()
    private val ciphers = mutableMapOf<String, MessageCipher>()
    /** Serializes retry sweeps - the loop, the event-driven flushes and a manual send may all fire at once. */
    private val retryMutex = Mutex()
    /** One LAN dial at a time per contact - parallel dials evict each other's connections mid-write (see [sendFrameTo]). */
    private val dialMutexes = ConcurrentHashMap<String, Mutex>()
    private fun dialMutexFor(contactId: String): Mutex = dialMutexes.getOrPut(contactId) { Mutex() }
    /** Avatar hashes already pull-requested per contact - stops every incoming message re-requesting the same photo while its transfer is still in flight. */
    private val requestedAvatarHashes = ConcurrentHashMap<String, String>()
    @Volatile private var cachedAvatarHash: String? = null
    @Volatile private var cachedAvatarHashValid = false
    /** Pairwise routing tag -> contactId, so an incoming relay event resolves to its sender without trial-decrypting every session. */
    private val routeTagCache = ConcurrentHashMap<String, String>()
    /** contactId -> last group-state sync, so ordinary traffic re-syncs groups without spamming invites. */
    private val lastGroupSyncAt = ConcurrentHashMap<String, Long>()
    /** Low bits of [nextSeq] - the high bits are the send timestamp, so the sequence survives restarts with no persistence. */
    private val seqCounter = AtomicLong(0)

    val relayState: StateFlow<RelayState> get() = relayTransport.state
    val relayCount: StateFlow<Int> get() = relayTransport.relayCount
    val lanPeerCount: StateFlow<Int> get() = lanTransport.peerCount

    /**
     * Strictly increasing per send: the high bits are the send time in ms, the
     * low 20 bits a counter. Two messages in the same millisecond still order
     * correctly, and a restart can't collide with (or reorder against) older
     * rows because wall-clock time only moves forward.
     */
    internal fun nextSeq(sentAtMs: Long): Long =
        (sentAtMs shl 20) or (seqCounter.incrementAndGet() and 0xFFFFF)

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
        /** Which chunk indexes already landed - relay fan-out duplicates every chunk, so a plain counter would "complete" early with holes. */
        val receivedIndices: MutableSet<Int> = mutableSetOf(),
        val startedAt: Long = System.currentTimeMillis(),
    )

    private val incomingTransfers = ConcurrentHashMap<String, IncomingTransfer>()

    private data class PendingChunks(val startedAt: Long = System.currentTimeMillis(), val chunks: MutableMap<Int, ByteArray> = mutableMapOf())
    /** Chunks that beat their control payload here - held until it arrives (or they go stale), never dropped on the floor. */
    private val pendingChunks = ConcurrentHashMap<String, PendingChunks>()

    private val _incomingRequests = MutableStateFlow<List<IncomingFriendRequest>>(emptyList())
    val incomingRequests: StateFlow<List<IncomingFriendRequest>> = _incomingRequests

    private val _events = MutableSharedFlow<ChatServiceEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ChatServiceEvent> = _events

    private var blockedContactIds: Set<String> = emptySet()
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch { contactDao.observeBlocked().collect { blocked -> blockedContactIds = blocked.map { it.contactId }.toSet() } }
        relayTransport.start()
        scope.launch {
            relayTransport.incoming.collect { incoming -> onRelayIncoming(incoming) }
        }
        lanTransport.start(identityKeyManager.contactId())
        scope.launch {
            lanTransport.incomingConnections.collect { socket -> handleIncomingSocket(socket) }
        }
        scope.launch { retryPendingLoop() }
        scope.launch { heartbeatLoop() }
        // Event-driven flushes: the moment the network (or a specific peer)
        // proves reachable, anything waiting goes out immediately instead of
        // sitting until the next retry tick.
        scope.launch {
            relayTransport.state.collect { state ->
                if (state == RelayState.CONNECTED) {
                    runCatching { retryPendingNow() }
                    runCatching { retryPendingRequests() }
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
            rebuildRouteTagCache()
        }
    }

    /**
     * A note to yourself has already arrived the moment it's written to the local
     * database - the "recipient" is this very device.
     */
    private fun isSelf(contactId: String): Boolean = contactId == identityKeyManager.contactId()

    /**
     * Everyone has themselves in their contacts, the way "Message yourself" works
     * elsewhere - and unconditionally, from the first launch onward.
     */
    private suspend fun ensureSelfContact() {
        val myId = identityKeyManager.contactId()
        if (contactDao.find(myId) != null) return
        contactDao.upsert(
            ContactEntity(
                contactId = myId,
                nickname = identityKeyManager.nickname,
                identityKeyBytes = identityKeyManager.identityKeyPair().publicKey.serialize(),
                nostrPubkey = identityKeyManager.nostrPubkeyHex(),
                addedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Resolves every known contact's pairwise routing tag once, so the hot receive path is a map lookup. */
    private suspend fun rebuildRouteTagCache() {
        val ownPrivate = identityKeyManager.identityKeyPair().privateKey.serialize()
        runCatching { contactDao.observeContacts().first() }.getOrDefault(emptyList()).forEach { contact ->
            runCatching {
                routeTagCache[NostrCrypto.pairRoutingTag(ownPrivate, contact.identityKeyBytes)] = contact.contactId
            }
        }
    }

    private fun routeTagFor(identityKeyBytes: ByteArray): String? = runCatching {
        NostrCrypto.pairRoutingTag(identityKeyManager.identityKeyPair().privateKey.serialize(), identityKeyBytes)
    }.getOrNull()

    fun stop() {
        relayTransport.stop()
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

    /**
     * Our shareable Hertz ID. Always available immediately - unlike the old
     * network address there is nothing to bootstrap first, so the QR code
     * never sits behind a spinner.
     */
    fun myHertzId(): HertzId = HertzId(
        contactId = identityKeyManager.contactId(),
        nickname = identityKeyManager.nickname,
        identityKeyBase64 = Base64.encodeToString(identityKeyManager.identityKeyPair().publicKey.serialize(), Base64.NO_WRAP),
        nostrPubkeyHex = identityKeyManager.nostrPubkeyHex(),
    )

    /** True for a well-formed relay key (64 hex chars) - anything else is a pre-relay address and can't be published to. */
    private fun isRelayKey(value: String): Boolean =
        value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    /** Result carries a human-readable reason on failure, since "nothing happened" after scanning a QR code is a bad silent failure mode. */
    suspend fun sendFriendRequest(target: HertzId, viaGroupId: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isRelayKey(target.nostrPubkeyHex) || target.contactId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Tenhle kód je ze staré verze aplikace - aktualizuj Hertz Chat na druhém telefonu a ukaž kód znovu."))
        }
        val me = myHertzId()
        val payload = FriendRequestPayload(
            nickname = me.nickname,
            identityKeyBase64 = me.identityKeyBase64,
            nostrPubkeyHex = me.nostrPubkeyHex,
            preKeyBundle = identityKeyManager.currentPreKeyBundle().toWire(),
            viaGroupId = viaGroupId,
        )
        val sent = publishSealed(target, json.encodeToString(payload).encodeToByteArray()) ||
            sendLanFriendFrame(target.contactId, frame(FRAME_FRIEND_REQUEST, json.encodeToString(payload).encodeToByteArray()))
        if (!sent) {
            return@withContext Result.failure(IllegalStateException("Nejde se připojit k relay serverům ani najít přítele v místní síti - zkontroluj připojení a zkus to znovu."))
        }
        // Relay events are ephemeral: if they were offline just now, the retry
        // sweep keeps re-sending until they accept (or it expires).
        rememberPendingRequest(target)
        Result.success(Unit)
    }

    /**
     * Publishes a sealed handshake payload (request or response) to a relay
     * key the sender isn't contacts with yet - or re-sends one they are.
     */
    private suspend fun publishSealed(recipientPubHex: String, recipientIdentityKeyBase64: String, plaintext: ByteArray): Boolean {
        if (!isRelayKey(recipientPubHex)) return false
        val recipientIdentity = runCatching { Base64.decode(recipientIdentityKeyBase64, Base64.NO_WRAP) }.getOrNull() ?: return false
        val sealed = NostrCrypto.sealToIdentityKey(plaintext, recipientIdentity)
        val wire = SealedWire(sealed.ephemeralPublicHex, sealed.nonceBase64, sealed.ciphertextBase64)
        return withContext(Dispatchers.IO) {
            relayTransport.publish(
                recipientPubHex,
                listOf(listOf(NostrProtocol.TAG_SEALED, sealed.ephemeralPublicHex)),
                json.encodeToString(wire).encodeToByteArray(),
            )
        }
    }

    private suspend fun publishSealed(target: HertzId, plaintext: ByteArray): Boolean =
        publishSealed(target.nostrPubkeyHex, target.identityKeyBase64, plaintext)

    /** Best-effort direct LAN delivery for handshake frames - instant when both phones share a network, silent no-op otherwise. */
    private suspend fun sendLanFriendFrame(contactId: String, bytes: ByteArray): Boolean {
        if (lanTransport.addressFor(contactId) == null) return false
        return runCatching {
            val connection = dialAndRegister(contactId)
            withTimeout(SEND_TIMEOUT_MS) { connection.send(bytes) }
            true
        }.getOrDefault(false)
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
        val me = myHertzId()
        val payload = FriendRequestPayload(
            nickname = me.nickname,
            identityKeyBase64 = me.identityKeyBase64,
            nostrPubkeyHex = me.nostrPubkeyHex,
            preKeyBundle = identityKeyManager.currentPreKeyBundle().toWire(),
        )
        publishSealed(target, json.encodeToString(payload).encodeToByteArray())
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
        scope.launch {
            if (accept) {
                addTrustedContact(
                    request.contactId,
                    request.nickname,
                    request.request.identityKeyBase64,
                    request.request.nostrPubkeyHex,
                )
                runCatching {
                    cipherFor(request.contactId).establishSessionFromBundle(request.request.preKeyBundle.toPreKeyBundle())
                }
                // Push mine and pull theirs: either direction alone can miss while
                // the fresh session settles, together the photos land on both sides.
                sendProfileTo(request.contactId)
                requestProfile(request.contactId)
            }
            // Their request is answered - if we also had one pending to them, it served its purpose.
            forgetPendingRequest(request.contactId)
            val me = myHertzId()
            val response = FriendResponsePayload(
                accepted = accept,
                nickname = me.nickname,
                identityKeyBase64 = me.identityKeyBase64,
                nostrPubkeyHex = me.nostrPubkeyHex,
                // The other half of the symmetric handshake - lets the
                // original requester establish their own side of the session too.
                preKeyBundle = if (accept) identityKeyManager.currentPreKeyBundle().toWire() else null,
            )
            val responseBytes = json.encodeToString(response).encodeToByteArray()
            publishSealed(request.request.nostrPubkeyHex, request.request.identityKeyBase64, responseBytes)
            sendLanFriendFrame(request.contactId, frame(FRAME_FRIEND_RESPONSE, responseBytes))
        }
    }

    private suspend fun addTrustedContact(contactId: String, nickname: String, identityKeyBase64: String, nostrPubkey: String) {
        contactDao.upsert(
            ContactEntity(
                contactId = contactId,
                nickname = nickname,
                identityKeyBytes = Base64.decode(identityKeyBase64, Base64.NO_WRAP),
                nostrPubkey = nostrPubkey,
                addedAt = System.currentTimeMillis(),
            ),
        )
        routeTagFor(Base64.decode(identityKeyBase64, Base64.NO_WRAP))?.let { routeTagCache[it] = contactId }
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

            val me = myHertzId()
            // Every member's HertzId (including the creator) so recipients who don't know each other yet can auto-introduce themselves.
            val roster = listOf(me) + members.map { HertzId(it.contactId, it.nickname, Base64.encodeToString(it.identityKeyBytes, Base64.NO_WRAP), it.nostrPubkey) }
            val invite = ChatPayload(UUID.randomUUID().toString(), now, PayloadKind.GROUP_INVITE, seq = nextSeq(now), groupId = groupId, groupName = name, groupMembers = roster, groupOwnerId = myId)
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
            if (group.ownerId != identityKeyManager.contactId()) return@launch
            val members = groupMemberDao.findMembers(groupId)
            val now = System.currentTimeMillis()
            val payload = ChatPayload(
                UUID.randomUUID().toString(), now, PayloadKind.GROUP_DELETE,
                seq = nextSeq(now), groupId = groupId,
            )
            coroutineScope {
                members.map { async { trySendPayload(it.contactId, payload) } }.awaitAll()
            }
            identityKeyManager.deletedGroupIds = identityKeyManager.deletedGroupIds + groupId
            wipeGroupLocally(groupId)
        }
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
            if (group.ownerId != myId) return@launch
            val existing = groupMemberDao.findMembers(groupId)
            val toAdd = newContactIds.filter { id -> id != myId && existing.none { it.contactId == id } }.mapNotNull { contactDao.find(it) }
            if (toAdd.isEmpty()) return@launch
            toAdd.forEach { groupMemberDao.upsert(GroupMemberEntity(groupId, it.contactId, it.nickname)) }

            val me = myHertzId()
            val allMembers = groupMemberDao.findMembers(groupId)
            val roster = listOf(me) + allMembers.mapNotNull { m -> contactDao.find(m.contactId)?.let { HertzId(it.contactId, it.nickname, Base64.encodeToString(it.identityKeyBytes, Base64.NO_WRAP), it.nostrPubkey) } }
            val now = System.currentTimeMillis()
            val invite = ChatPayload(
                UUID.randomUUID().toString(), now, PayloadKind.GROUP_INVITE,
                seq = nextSeq(now),
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
            if (group.ownerId != myId || contactId == myId) return@launch
            contactDao.find(contactId) ?: return@launch
            val now = System.currentTimeMillis()
            // Sent before deleting locally, so this device is still in its own roster send below.
            trySendPayload(
                contactId,
                ChatPayload(
                    UUID.randomUUID().toString(), now, PayloadKind.GROUP_ROSTER_UPDATE,
                    seq = nextSeq(now),
                    groupId = groupId, groupName = group.name, groupOwnerId = myId,
                    groupRoster = listOfNotNull(myHertzId()),
                ),
            )
            groupMemberDao.deleteMember(groupId, contactId)
            broadcastRoster(group)
        }
    }

    /**
     * Pushes everything group-shaped that [contactId] may have missed while away:
     * a fresh invite for every group we own with them in it (invites are
     * fire-and-forget, so without this a member added while offline never sees
     * the group appear), plus a GROUP_DELETE per tombstone. Called when they prove
     * reachable - never on a timer, so it costs nothing when idle.
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
                val now = System.currentTimeMillis()
                trySendPayload(
                    contactId,
                    ChatPayload(UUID.randomUUID().toString(), now, PayloadKind.GROUP_DELETE, seq = nextSeq(now), groupId = groupId),
                )
            }
        }
    }

    /** Any traffic from a contact proves they're online - sync group state at most once a day per contact. */
    private fun maybeSyncGroupState(contactId: String) {
        val now = System.currentTimeMillis()
        if (now - (lastGroupSyncAt[contactId] ?: 0L) < GROUP_SYNC_INTERVAL_MS) return
        lastGroupSyncAt[contactId] = now
        syncGroupStateTo(contactId)
    }

    /** The same bootstrap invite [addGroupMembers] sends - extracted so late joiners get the identical shape. */
    private suspend fun groupInvitePayload(group: GroupEntity): ChatPayload? {
        val myId = identityKeyManager.contactId()
        val me = myHertzId()
        val members = groupMemberDao.findMembers(group.groupId)
        val roster = listOf(me) + members.mapNotNull { m ->
            contactDao.find(m.contactId)?.let { HertzId(it.contactId, it.nickname, Base64.encodeToString(it.identityKeyBytes, Base64.NO_WRAP), it.nostrPubkey) }
        }
        val now = System.currentTimeMillis()
        return ChatPayload(
            UUID.randomUUID().toString(), now, PayloadKind.GROUP_INVITE,
            seq = nextSeq(now),
            groupId = group.groupId, groupName = group.name, groupMembers = roster, groupOwnerId = myId,
        )
    }

    private suspend fun broadcastRoster(group: GroupEntity) {
        val myId = identityKeyManager.contactId()
        val me = myHertzId()
        val members = groupMemberDao.findMembers(group.groupId)
        val roster = listOf(me) + members.mapNotNull { m -> contactDao.find(m.contactId)?.let { HertzId(it.contactId, it.nickname, Base64.encodeToString(it.identityKeyBytes, Base64.NO_WRAP), it.nostrPubkey) } }
        val now = System.currentTimeMillis()
        val payload = ChatPayload(
            UUID.randomUUID().toString(), now, PayloadKind.GROUP_ROSTER_UPDATE,
            seq = nextSeq(now),
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
        if (fromContactId != group.ownerId) return
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
        if (fromContactId != group.ownerId) return
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
            val payload = ChatPayload(messageId, now, PayloadKind.TEXT, seq = seq, text = text, groupId = groupId)
            val delivered = coroutineScope {
                members.map { async { trySendPayload(it.contactId, payload) } }.awaitAll().any { it }
            }
            messageDao.updateState(messageId, if (delivered) DeliveryState.SENT else DeliveryState.PENDING)
        }
    }

    /**
     * encrypt() is what actually threw the NoSessionException that used to crash the app
     * outright - wrapped now so any future encryption failure fails
     * this one send instead of taking the whole app down. A message that can't be
     * encrypted can't be sent either way, so "not delivered" is the correct outcome,
     * not a crash.
     */
    /** Sends one call-signaling or audio packet - through the same E2E session as chat, so calls are encrypted exactly like messages. */
    suspend fun sendCallPayload(contactId: String, payload: ChatPayload): Boolean =
        trySendPayload(contactId, payload, fastLane = payload.kind == PayloadKind.CALL_AUDIO)

    /** Returns true if the envelope made it onto the wire - not a delivery receipt, just "left this device". */
    private suspend fun trySendPayload(contactId: String, payload: ChatPayload, fastLane: Boolean = false): Boolean = runCatching {
        val contact = contactDao.find(contactId) ?: return false
        // Every payload carries who we currently are - the receiver syncs our
        // nickname/avatar/address off ordinary traffic, so an offline peer catches up
        // on the next message instead of missing a one-shot broadcast forever.
        val stamped = payload.copy(
            senderNickname = identityKeyManager.nickname,
            senderAvatarHash = myAvatarHash(),
            senderNostrPub = identityKeyManager.nostrPubkeyHex(),
        )
        val envelope = cipherFor(contactId).encrypt(json.encodeToString(stamped).encodeToByteArray())
        val frameType = if (envelope.isPreKeyMessage) FRAME_PREKEY else FRAME_MESSAGE
        sendRoutedBytes(contact, frame(frameType, envelope.ciphertext), fastLane)
    }.getOrDefault(false)

    /**
     * One routed send: same local network wins (direct, near-instant, works with
     * no internet at all), the relay covers everyone else. [fastLane] (call
     * audio only) publishes to a single relay instead of all of them.
     */
    private suspend fun sendRoutedBytes(contact: ContactEntity, bytes: ByteArray, fastLane: Boolean = false): Boolean {
        if (lanTransport.addressFor(contact.contactId) != null) {
            if (sendFrameTo(contact.contactId, bytes)) return true
            // LAN dial failed (they left the network) - fall through to the relay.
        }
        if (!isRelayKey(contact.nostrPubkey)) return false
        val tag = routeTagFor(contact.identityKeyBytes) ?: return false
        val tags = listOf(listOf(NostrProtocol.TAG_ROUTE, tag))
        return withContext(Dispatchers.IO) {
            if (fastLane) relayTransport.publishFast(contact.nostrPubkey, tags, bytes)
            else relayTransport.publish(contact.nostrPubkey, tags, bytes)
        }
    }

    private suspend fun sendFrameTo(contactId: String, bytes: ByteArray): Boolean {
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
        // or a flush) used to dial in parallel: each dial then evicted the
        // previous connection out from under its in-flight write, failing
        // messages that had a working socket. One dial per contact at a time;
        // whoever waited rechecks the cache first, since the winner already connected.
        return dialMutexFor(contactId).withLock {
            outgoingConnections[contactId]?.let { winner ->
                if (runCatching { withTimeout(SEND_TIMEOUT_MS) { winner.send(bytes) } }.isSuccess) return@withLock true
                outgoingConnections.remove(contactId, winner)
                runCatching { winner.close() }
            }
            runCatching {
                val fresh = dialAndRegister(contactId)
                withTimeout(SEND_TIMEOUT_MS) { fresh.send(bytes) }
            }.isSuccess
        }
    }

    private suspend fun dialAndRegister(contactId: String): P2pConnection = withContext(Dispatchers.IO) {
        val socket: Socket = lanTransport.connectTo(contactId)
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
                check(sendChunkStreamFromFile(contactId, transferId, key, nonceSalt, localCopy, chunkCount))
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
                check(sendChunkStream(contactId, transferId, key, nonceSalt, sourceBytes))
            }.isSuccess
            messageDao.updateState(messageId, if (delivered) DeliveryState.SENT else DeliveryState.PENDING)
        }
    }

    /**
     * Streams encrypted chunks over whatever route is currently alive, re-attempting
     * each chunk a few times. Without per-chunk retries, one failed chunk out of
     * hundreds abandons the whole photo.
     */
    private suspend fun sendChunkStream(contactId: String, transferId: UUID, key: ByteArray, nonceSalt: ByteArray, sourceBytes: ByteArray): Boolean {
        val contact = contactDao.find(contactId) ?: return false
        var offset = 0
        var index = 0
        while (offset < sourceBytes.size) {
            val end = minOf(offset + MediaCrypto.CHUNK_SIZE, sourceBytes.size)
            if (!sendOneChunk(contact, transferId, key, nonceSalt, index, sourceBytes.copyOfRange(offset, end))) return false
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
    private suspend fun sendChunkStreamFromFile(contactId: String, transferId: UUID, key: ByteArray, nonceSalt: ByteArray, sourceFile: File, chunkCount: Int): Boolean {
        val contact = contactDao.find(contactId) ?: return false
        return runCatching {
            FileChunks.Reader(sourceFile, MediaCrypto.CHUNK_SIZE).use { reader ->
                if (reader.chunkCount != chunkCount) return@runCatching false
                repeat(chunkCount) { index ->
                    if (!sendOneChunk(contact, transferId, key, nonceSalt, index, reader.readChunk(index))) return@runCatching false
                }
            }
            true
        }.getOrDefault(false)
    }

    /** Encrypts one chunk and pushes it through the per-chunk retry loop. */
    private suspend fun sendOneChunk(contact: ContactEntity, transferId: UUID, key: ByteArray, nonceSalt: ByteArray, index: Int, plaintext: ByteArray): Boolean {
        val cipherChunk = MediaCrypto.encryptChunk(key, nonceSalt, index, plaintext)
        var sent = false
        var attempts = 0
        while (!sent && attempts < CHUNK_ATTEMPTS) {
            sent = sendRoutedBytes(contact, frameMediaChunk(transferId, index, cipherChunk))
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
            val delivered = coroutineScope {
                members.map { member ->
                    async {
                        runCatching {
                            check(trySendPayload(member.contactId, control))
                            check(sendChunkStreamFromFile(member.contactId, transferId, key, nonceSalt, localCopy, chunkCount))
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
            val delivered = coroutineScope {
                members.map { member ->
                    async {
                        runCatching {
                            check(trySendPayload(member.contactId, control))
                            check(sendChunkStream(member.contactId, transferId, key, nonceSalt, sourceBytes))
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
     * directly, a changed relay key repairs the contact's address, and a mismatched
     * avatar hash pulls the full photo. Skipped for call audio (50 packets a second
     * would re-hash for nothing - the call signaling around it already carries the
     * same stamp) and for our own loopback.
     */
    private fun applySenderProfile(contactId: String, payload: ChatPayload) {
        if (payload.kind == PayloadKind.CALL_AUDIO) return
        if (payload.senderNickname == null && payload.senderAvatarHash == null && payload.senderNostrPub == null) return
        if (contactId == identityKeyManager.contactId()) return
        scope.launch {
            val contact = contactDao.find(contactId) ?: return@launch
            val nickname = payload.senderNickname?.trim().orEmpty()
            var updated = contact
            if (nickname.isNotEmpty() && nickname != contact.nickname) {
                updated = updated.copy(nickname = nickname)
            }
            val relayKey = payload.senderNostrPub
            if (relayKey != null && isRelayKey(relayKey) && relayKey != contact.nostrPubkey) {
                updated = updated.copy(nostrPubkey = relayKey)
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
            val now = System.currentTimeMillis()
            runCatching {
                trySendPayload(
                    contactId,
                    ChatPayload(UUID.randomUUID().toString(), now, PayloadKind.PROFILE_UPDATE, seq = nextSeq(now), profileNickname = identityKeyManager.nickname),
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
            val now = System.currentTimeMillis()
            runCatching {
                trySendPayload(
                    contactId,
                    ChatPayload(UUID.randomUUID().toString(), now, PayloadKind.PROFILE_REQUEST, seq = nextSeq(now)),
                )
            }
        }
    }

    private fun sendAvatarTo(contactId: String, jpegBytes: ByteArray) {
        val transferId = UUID.randomUUID()
        val key = MediaCrypto.generateKey()
        val nonceSalt = MediaCrypto.generateNonceSalt()
        val chunkCount = (jpegBytes.size + MediaCrypto.CHUNK_SIZE - 1) / MediaCrypto.CHUNK_SIZE
        val now = System.currentTimeMillis()
        val control = ChatPayload(
            messageId = UUID.randomUUID().toString(),
            sentAt = now,
            kind = PayloadKind.AVATAR,
            seq = nextSeq(now),
            mediaMimeType = "image/jpeg",
            mediaSizeBytes = jpegBytes.size.toLong(),
            mediaTransferId = transferId.toString(),
            mediaKeyBase64 = Base64.encodeToString(key, Base64.NO_WRAP),
            mediaNonceSaltBase64 = Base64.encodeToString(nonceSalt, Base64.NO_WRAP),
            mediaChunkCount = chunkCount,
        )
        scope.launch {
            contactDao.find(contactId) ?: return@launch
            runCatching {
                check(trySendPayload(contactId, control))
                check(sendChunkStream(contactId, transferId, key, nonceSalt, jpegBytes))
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
            if (!transfer.receivedIndices.add(chunkIndex)) return true // relay fan-out duplicate - already have it
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
     * Warms the LAN path to a contact - opening a chat dials while the user
     * reads, so the first send finds a live socket. No-op when they're not on
     * this network (the relay needs no warmup at all).
     */
    fun warmConnection(contactId: String) {
        if (outgoingConnections.containsKey(contactId)) return
        if (lanTransport.addressFor(contactId) == null) return
        scope.launch {
            val contact = contactDao.find(contactId) ?: return@launch
            if (contact.contactId in blockedContactIds) return@launch
            dialMutexFor(contactId).withLock {
                if (outgoingConnections.containsKey(contactId)) return@withLock
                runCatching { dialAndRegister(contactId) }
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

    private suspend fun heartbeatLoop() {
        while (started) {
            delay(HEARTBEAT_MS)
            // Only already-open LAN connections: a ping must never dial, or every
            // heartbeat would wake every dead peer like a retry sweep. The ping
            // still carries the profile stamp, so it doubles as profile refresh.
            outgoingConnections.keys.forEach { contactId ->
                if (!outgoingConnections.containsKey(contactId)) return@forEach
                val now = System.currentTimeMillis()
                runCatching {
                    trySendPayload(contactId, ChatPayload(UUID.randomUUID().toString(), now, PayloadKind.PING, seq = nextSeq(now)))
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
            // message in one sequence, so a slow peer head-of-line-blocked
            // everything behind it and one unreachable contact starved all the
            // reachable ones. Within a lane the sends stay ordered and stop at
            // the first failure - if the peer were up, the first message would
            // have gone through.
            coroutineScope {
                messageDao.findUnsent().groupBy { it.contactId }.values.map { lane ->
                    async {
                        for (message in lane) {
                            if (!retryMessage(message)) break
                        }
                    }
                }.awaitAll()
            }
        } finally {
            retryMutex.unlock()
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
        // receipt. Relay events are fire-and-forget, so a lost ack must never
        // wedge the message at "sent" forever - and re-sends are idempotent on
        // the receiving side (same message id, just another ack).
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
            file != null && resendMedia(message, file, groupId = null, toContactId = message.contactId)
        }
        if (delivered && !resolicit) messageDao.updateState(message.messageId, DeliveryState.SENT)
        return delivered
    }

    private suspend fun retryGroupMessage(groupId: String, message: MessageEntity): Boolean {
        val resolicit = message.deliveryState == DeliveryState.SENT
        val members = groupMemberDao.findMembers(groupId)
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
                        resendMedia(message, file, groupId = groupId, toContactId = member.contactId)
                    }
                }.awaitAll().any { it }
            }
        }
        if (delivered && !resolicit) messageDao.updateState(message.messageId, DeliveryState.SENT)
        return delivered
    }

    private suspend fun resendMedia(message: MessageEntity, sourceFile: File, groupId: String?, toContactId: String): Boolean {
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
            check(sendChunkStreamFromFile(toContactId, transferId, key, nonceSalt, sourceFile, chunkCount))
        }.isSuccess
    }

    /**
     * Confirms receipt back to the sender - this is what stops their spinner. Goes
     * out as a small burst: relay delivery is fire-and-forget, and one lost ack
     * used to wedge the sender's message at "sent" even though it had arrived.
     */
    private fun sendDeliveredAck(contactId: String, ackForMessageId: String) {
        scope.launch {
            repeat(3) { attempt ->
                if (attempt > 0) delay(300)
                runCatching {
                    val now = System.currentTimeMillis()
                    trySendPayload(
                        contactId,
                        ChatPayload(UUID.randomUUID().toString(), now, PayloadKind.DELIVERED_ACK, seq = nextSeq(now), ackForMessageId = ackForMessageId),
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
                val now = System.currentTimeMillis()
                runCatching {
                    trySendPayload(
                        threadId,
                        ChatPayload(UUID.randomUUID().toString(), now, PayloadKind.READ_ACK, seq = nextSeq(now), ackForMessageId = message.messageId),
                    )
                }
                messageDao.updateState(message.messageId, DeliveryState.READ)
            }
        }
    }

    /** Drops transfers whose chunks stopped arriving mid-photo: closes the leaked file handle and deletes the partial file. */
    private fun sweepStaleTransfers() {
        val cutoff = System.currentTimeMillis() - STALE_TRANSFER_MS
        incomingTransfers.entries.removeIf { (_, transfer) ->
            if (transfer.startedAt >= cutoff) return@removeIf false
            runCatching { transfer.out.close() }
            runCatching { transfer.outputFile.delete() }
            true
        }
        pendingChunks.entries.removeIf { (_, pending) -> pending.startedAt < cutoff }
    }

    // --- Relay receive path ---

    private fun onRelayIncoming(incoming: RelayTransport.Incoming) {
        val sealedTag = incoming.tagValue(NostrProtocol.TAG_SEALED)
        if (sealedTag != null) {
            onSealedIncoming(incoming)
            return
        }
        val routeTag = incoming.tagValue(NostrProtocol.TAG_ROUTE) ?: return
        val contactId = routeTagCache[routeTag] ?: return
        if (contactId in blockedContactIds) return
        val bytes = runCatching { Base64.decode(incoming.contentBase64, Base64.NO_WRAP) }.getOrNull() ?: return
        if (bytes.isEmpty()) return
        maybeSyncGroupState(contactId)
        when (bytes[0]) {
            FRAME_MEDIA_CHUNK -> onMediaChunkReceived(bytes)
            FRAME_MESSAGE, FRAME_PREKEY -> onEnvelopeReceived(contactId, bytes)
            else -> Unit
        }
    }

    /** Trial-opens a sealed handshake envelope with our identity key - strangers have no pairwise tag yet, so every sealed envelope is attempted. */
    private fun onSealedIncoming(incoming: RelayTransport.Incoming) {
        val wire = runCatching {
            val decoded = Base64.decode(incoming.contentBase64, Base64.NO_WRAP).decodeToString()
            json.decodeFromString(SealedWire.serializer(), decoded)
        }.getOrNull() ?: return
        val sealed = NostrCrypto.SealedRequest(wire.ephemeralPublicHex, wire.nonceBase64, wire.ciphertextBase64)
        val plaintext = NostrCrypto.openSealedRequest(sealed, identityKeyManager.identityKeyPair().privateKey.serialize()) ?: return
        val text = plaintext.decodeToString()
        // A response carries `accepted`; a request doesn't - try the shapes in order.
        runCatching { json.decodeFromString(FriendResponsePayload.serializer(), text) }.getOrNull()?.let {
            handleFriendResponsePayload(it)
            return
        }
        runCatching { json.decodeFromString(FriendRequestPayload.serializer(), text) }.getOrNull()?.let {
            handleFriendRequestPayload(it)
        }
    }

    // --- LAN connection handling ---

    private fun handleIncomingSocket(socket: Socket) {
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
                            // A LAN socket proves nothing on its own, so only honour the claim
                            // if mDNS actually announced that contact at this address - see
                            // LanTransport.matchesDiscovered.
                            val identityPlausible = lanTransport.matchesDiscovered(claimed, socket.inetAddress)
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
                        FRAME_FRIEND_REQUEST -> runCatching {
                            json.decodeFromString(FriendRequestPayload.serializer(), bytes.copyOfRange(1, bytes.size).decodeToString())
                        }.getOrNull()?.let { handleFriendRequestPayload(it) }
                        FRAME_FRIEND_RESPONSE -> runCatching {
                            json.decodeFromString(FriendResponsePayload.serializer(), bytes.copyOfRange(1, bytes.size).decodeToString())
                        }.getOrNull()?.let { handleFriendResponsePayload(it) }
                        FRAME_MESSAGE, FRAME_PREKEY -> {
                            val senderId = fromContactId ?: continue
                            if (senderId in blockedContactIds) continue
                            maybeSyncGroupState(senderId)
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
                    FRAME_FRIEND_RESPONSE -> runCatching {
                        json.decodeFromString(FriendResponsePayload.serializer(), bytes.copyOfRange(1, bytes.size).decodeToString())
                    }.getOrNull()?.let { handleFriendResponsePayload(it) }
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
                if (nickname.isNotEmpty()) {
                    contactDao.find(contactId)?.let { contactDao.update(it.copy(nickname = nickname)) }
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

    private fun handleFriendRequestPayload(request: FriendRequestPayload) {
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

    private fun handleFriendResponsePayload(response: FriendResponsePayload) {
        if (!response.accepted) return
        val senderContactId = identityKeyManager.contactIdFor(Base64.decode(response.identityKeyBase64, Base64.NO_WRAP))
        forgetPendingRequest(senderContactId)
        scope.launch {
            addTrustedContact(senderContactId, response.nickname, response.identityKeyBase64, response.nostrPubkeyHex)
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
