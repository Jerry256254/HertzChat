package cz.kuclab.hertzchat.data.repository

import android.content.Context
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
import cz.kuclab.hertzchat.network.signaling.FriendRequestPayload
import cz.kuclab.hertzchat.network.signaling.FriendResponsePayload
import cz.kuclab.hertzchat.network.signaling.IceCandidatePayload
import cz.kuclab.hertzchat.network.signaling.PresenceEntry
import cz.kuclab.hertzchat.network.signaling.SdpPayload
import cz.kuclab.hertzchat.network.signaling.SignalingClient
import cz.kuclab.hertzchat.network.signaling.SignalingEvent
import cz.kuclab.hertzchat.network.webrtc.P2pEvent
import cz.kuclab.hertzchat.network.webrtc.PeerConnection2
import cz.kuclab.hertzchat.network.webrtc.defaultIceServers
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

data class IncomingFriendRequest(val contactId: String, val nickname: String, val request: FriendRequestPayload)

sealed interface ChatServiceEvent {
    data class FriendRequestReceived(val request: IncomingFriendRequest) : ChatServiceEvent
    data class MessageReceived(val contactId: String, val message: MessageEntity) : ChatServiceEvent
}

/**
 * Orchestrates the whole P2P pipeline for the app: signaling (presence +
 * handshake rendezvous), WebRTC data channels (the actual transport) and the
 * Signal Protocol session per contact (the actual end-to-end encryption).
 * A message never exists in plaintext anywhere except on the two devices
 * that are party to the conversation.
 */
@Singleton
class P2pChatService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val identityKeyManager: IdentityKeyManager,
    private val protocolStore: RoomSignalProtocolStore,
    private val contactDao: ContactDao,
    private val messageDao: MessageDao,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var signalingClient: SignalingClient? = null
    private val connections = mutableMapOf<String, PeerConnection2>()
    private val ciphers = mutableMapOf<String, MessageCipher>()
    private val pendingOutgoing = mutableMapOf<String, MutableList<ByteArray>>()

    private val _onlinePresence = MutableStateFlow<List<PresenceEntry>>(emptyList())
    val onlinePresence: StateFlow<List<PresenceEntry>> = _onlinePresence

    private val _events = MutableSharedFlow<ChatServiceEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ChatServiceEvent> = _events

    private val _incomingRequests = MutableStateFlow<List<IncomingFriendRequest>>(emptyList())
    val incomingRequests: StateFlow<List<IncomingFriendRequest>> = _incomingRequests

    var relayUrl: String = DEFAULT_RELAY_URL
    var turnUrl: String? = null
    var turnUsername: String? = null
    var turnPassword: String? = null

    private var blockedContactIds: Set<String> = emptySet()

    fun start() {
        scope.launch { contactDao.observeBlocked().collect { blocked -> blockedContactIds = blocked.map { it.contactId }.toSet() } }
        if (signalingClient != null) return
        val client = SignalingClient(relayUrl, identityKeyManager.contactId(), identityKeyManager.nickname)
        signalingClient = client
        scope.launch {
            client.events.collect { event -> handleSignalingEvent(event) }
        }
        client.connect()
    }

    fun stop() {
        signalingClient?.disconnect()
        signalingClient = null
        connections.values.forEach { it.close() }
        connections.clear()
    }

    private fun cipherFor(contactId: String): MessageCipher =
        ciphers.getOrPut(contactId) { MessageCipher(protocolStore, contactId) }

    // --- Friend requests ---

    fun sendFriendRequest(targetContactId: String) {
        val payload = FriendRequestPayload(
            nickname = identityKeyManager.nickname,
            identityKeyBase64 = android.util.Base64.encodeToString(
                identityKeyManager.identityKeyPair().publicKey.serialize(),
                android.util.Base64.NO_WRAP,
            ),
            preKeyBundle = identityKeyManager.currentPreKeyBundle().toWire(),
        )
        relay(targetContactId, "friend_request", payload)
    }

    fun respondFriendRequest(request: IncomingFriendRequest, accept: Boolean) {
        _incomingRequests.value = _incomingRequests.value.filterNot { it.contactId == request.contactId }
        if (accept) {
            addTrustedContact(request.contactId, request.nickname, request.request.identityKeyBase64)
            cipherFor(request.contactId).establishSessionFromBundle(request.request.preKeyBundle.toPreKeyBundle())
        }
        val response = FriendResponsePayload(
            accepted = accept,
            nickname = identityKeyManager.nickname,
            identityKeyBase64 = android.util.Base64.encodeToString(
                identityKeyManager.identityKeyPair().publicKey.serialize(),
                android.util.Base64.NO_WRAP,
            ),
        )
        relay(request.contactId, "friend_response", response)
    }

    private fun addTrustedContact(contactId: String, nickname: String, identityKeyBase64: String) {
        scope.launch {
            contactDao.upsert(
                ContactEntity(
                    contactId = contactId,
                    nickname = nickname,
                    identityKeyBytes = android.util.Base64.decode(identityKeyBase64, android.util.Base64.NO_WRAP),
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
        sendPayload(contactId, payload)

        scope.launch {
            messageDao.upsert(
                MessageEntity(
                    messageId = payload.messageId,
                    contactId = contactId,
                    fromMe = true,
                    type = MessageType.TEXT,
                    text = text,
                    timestamp = payload.sentAt,
                    deliveryState = DeliveryState.SENDING,
                ),
            )
        }
    }

    private fun sendPayload(contactId: String, payload: ChatPayload) {
        val envelope = cipherFor(contactId).encrypt(json.encodeToString(payload).encodeToByteArray())
        val framed = frame(envelope)
        val connection = connections[contactId]
        if (connection != null && connection.send(framed)) return
        pendingOutgoing.getOrPut(contactId) { mutableListOf() }.add(framed)
        ensureConnection(contactId, asInitiator = true)
    }

    private fun frame(envelope: EncryptedEnvelope): ByteArray {
        val prefix = if (envelope.isPreKeyMessage) 1.toByte() else 0.toByte()
        return byteArrayOf(prefix) + envelope.ciphertext
    }

    private fun unframe(bytes: ByteArray): EncryptedEnvelope =
        EncryptedEnvelope(isPreKeyMessage = bytes[0] == 1.toByte(), ciphertext = bytes.copyOfRange(1, bytes.size))

    // --- WebRTC connection lifecycle ---

    private fun ensureConnection(contactId: String, asInitiator: Boolean): PeerConnection2 {
        connections[contactId]?.let { return it }
        val pc = PeerConnection2(context, defaultIceServers(turnUrl, turnUsername, turnPassword), isInitiator = asInitiator)
        connections[contactId] = pc
        scope.launch { pc.eventFlow.collect { handlePeerEvent(contactId, it) } }
        if (asInitiator) {
            pc.createOffer { sdp -> relay(contactId, "sdp", SdpPayload("offer", sdp.description)) }
        }
        return pc
    }

    private fun handlePeerEvent(contactId: String, event: P2pEvent) {
        when (event) {
            is P2pEvent.IceCandidateGenerated -> relay(
                contactId,
                "ice",
                IceCandidatePayload(event.candidate.sdpMid, event.candidate.sdpMLineIndex, event.candidate.sdp),
            )
            is P2pEvent.DataChannelOpen -> if (event.open) flushPending(contactId)
            is P2pEvent.MessageReceived -> onDataReceived(contactId, event.bytes)
            is P2pEvent.ConnectionStateChanged -> if (event.state == PeerConnection.PeerConnectionState.FAILED ||
                event.state == PeerConnection.PeerConnectionState.CLOSED
            ) {
                connections.remove(contactId)?.close()
            }
        }
    }

    private fun flushPending(contactId: String) {
        val queued = pendingOutgoing.remove(contactId) ?: return
        val connection = connections[contactId] ?: return
        queued.forEach { connection.send(it) }
    }

    private fun onDataReceived(contactId: String, bytes: ByteArray) {
        val plaintext = runCatching { cipherFor(contactId).decrypt(unframe(bytes)) }.getOrNull() ?: return
        val payload = runCatching { json.decodeFromString(ChatPayload.serializer(), plaintext.decodeToString()) }.getOrNull() ?: return
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

    // --- Signaling glue ---

    private fun relay(to: String, kind: String, payload: Any) {
        val element: JsonElement = when (payload) {
            is FriendRequestPayload -> json.encodeToJsonElement(FriendRequestPayload.serializer(), payload)
            is FriendResponsePayload -> json.encodeToJsonElement(FriendResponsePayload.serializer(), payload)
            is SdpPayload -> json.encodeToJsonElement(SdpPayload.serializer(), payload)
            is IceCandidatePayload -> json.encodeToJsonElement(IceCandidatePayload.serializer(), payload)
            else -> return
        }
        val wrapped = buildJsonObject {
            put("kind", JsonPrimitive(kind))
            put("data", element)
        }
        signalingClient?.relay(to, wrapped)
    }

    private fun handleSignalingEvent(event: SignalingEvent) {
        when (event) {
            is SignalingEvent.Presence -> _onlinePresence.value = event.update.online.filter { it.contactId != identityKeyManager.contactId() }
            is SignalingEvent.Relay -> handleRelay(event.envelope.from, event.envelope.payload)
            else -> Unit
        }
    }

    private fun handleRelay(from: String, payload: JsonElement) {
        if (from in blockedContactIds) return
        val obj = payload.jsonObject
        val kind = obj["kind"]?.jsonPrimitive?.content ?: return
        val data = obj["data"] ?: return
        when (kind) {
            "friend_request" -> {
                val request = runCatching { json.decodeFromJsonElement(FriendRequestPayload.serializer(), data) }.getOrNull() ?: return
                val claimedId = identityKeyManager.contactIdFor(
                    android.util.Base64.decode(request.identityKeyBase64, android.util.Base64.NO_WRAP),
                )
                if (claimedId != from) return // identity key doesn't match the claimed sender - drop it
                val incoming = IncomingFriendRequest(from, request.nickname, request)
                _incomingRequests.value = _incomingRequests.value.filterNot { it.contactId == from } + incoming
                _events.tryEmit(ChatServiceEvent.FriendRequestReceived(incoming))
            }
            "friend_response" -> {
                val response = runCatching { json.decodeFromJsonElement(FriendResponsePayload.serializer(), data) }.getOrNull() ?: return
                if (response.accepted) addTrustedContact(from, response.nickname, response.identityKeyBase64)
            }
            "sdp" -> {
                val sdp = runCatching { json.decodeFromJsonElement(SdpPayload.serializer(), data) }.getOrNull() ?: return
                val type = if (sdp.kind == "offer") SessionDescription.Type.OFFER else SessionDescription.Type.ANSWER
                if (sdp.kind == "offer") {
                    val pc = ensureConnection(from, asInitiator = false)
                    pc.setRemoteDescription(SessionDescription(type, sdp.sdp))
                    pc.createAnswer { answer -> relay(from, "sdp", SdpPayload("answer", answer.description)) }
                } else {
                    connections[from]?.setRemoteDescription(SessionDescription(type, sdp.sdp))
                }
            }
            "ice" -> {
                val ice = runCatching { json.decodeFromJsonElement(IceCandidatePayload.serializer(), data) }.getOrNull() ?: return
                connections[from]?.addIceCandidate(IceCandidate(ice.sdpMid, ice.sdpMLineIndex, ice.candidate))
            }
        }
    }
}
