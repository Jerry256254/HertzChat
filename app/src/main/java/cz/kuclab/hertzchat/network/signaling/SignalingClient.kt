package cz.kuclab.hertzchat.network.signaling

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response
import java.util.concurrent.TimeUnit

sealed interface SignalingEvent {
    data class Presence(val update: PresenceUpdate) : SignalingEvent
    data class Relay(val envelope: RelayEnvelope) : SignalingEvent
    data object Connected : SignalingEvent
    data object Disconnected : SignalingEvent
}

/**
 * WebSocket client for the blind rendezvous relay (see /signaling-relay).
 * Only ever sends: our pseudonymous contactId + display nickname (presence),
 * and opaque relay envelopes addressed to a specific contactId (WebRTC
 * handshake / friend-request payloads). Never sends message content or media.
 */
class SignalingClient(
    private val relayUrl: String,
    private val myContactId: String,
    private val myNickname: String,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private var socket: WebSocket? = null

    private val _events = MutableSharedFlow<SignalingEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SignalingEvent> = _events

    fun connect() {
        val request = Request.Builder().url(relayUrl).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                sendJson(mapOf("type" to "hello", "contactId" to myContactId, "nickname" to myNickname))
                _events.tryEmit(SignalingEvent.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncoming(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _events.tryEmit(SignalingEvent.Disconnected)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _events.tryEmit(SignalingEvent.Disconnected)
            }
        })
    }

    fun disconnect() {
        socket?.close(1000, "bye")
        socket = null
    }

    fun requestOnlineList() {
        sendJson(mapOf("type" to "list_online"))
    }

    fun relay(to: String, payload: JsonElement) {
        val obj = buildJsonObject {
            put("type", JsonPrimitive("relay"))
            put("to", JsonPrimitive(to))
            put("payload", payload)
        }
        socket?.send(obj.toString())
    }

    private fun sendJson(fields: Map<String, String>) {
        val obj = buildJsonObject {
            fields.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
        }
        socket?.send(obj.toString())
    }

    private fun handleIncoming(text: String) {
        val element = runCatching { json.parseToJsonElement(text) }.getOrNull()?.jsonObject ?: return
        when (element["type"]?.jsonPrimitive?.content) {
            "presence_update", "hello_ack" -> {
                val update = runCatching { json.decodeFromJsonElement(PresenceUpdate.serializer(), element) }.getOrNull()
                    ?: return
                _events.tryEmit(SignalingEvent.Presence(update))
            }
            "relay" -> {
                val envelope = runCatching { json.decodeFromJsonElement(RelayEnvelope.serializer(), element) }.getOrNull()
                    ?: return
                _events.tryEmit(SignalingEvent.Relay(envelope))
            }
        }
    }
}
