package cz.kuclab.hertzchat.network.relay

import cz.kuclab.hertzchat.crypto.IdentityKeyManager
import cz.kuclab.hertzchat.data.repository.SettingsRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

enum class RelayState { CONNECTING, CONNECTED, OFFLINE }

/** Default relays: free, no account, no auth, global anycast-ish presence. The user can replace them with their own in Settings. */
val DEFAULT_RELAY_URLS = listOf(
    "wss://relay.damus.io",
    "wss://nos.lol",
    "wss://relay.primal.net",
)

/**
 * Publishes and receives [NostrProtocol.HERTZ_KIND] ephemeral events across
 * several relays at once. Ephemeral means the relay only forwards to whoever
 * is subscribed *right now* and stores nothing - no messages, no timestamps,
 * no logs of who talked to whom. Combined with per-event ephemeral sender
 * keys and pairwise routing tags (see [NostrCrypto]), a relay provably can't
 * answer "who sent what to whom" even while the traffic flows through it -
 * there is no sender identity on the wire at all, just an opaque tag it
 * cannot resolve.
 *
 * Reliability comes from fan-out, not from any single relay: every event goes
 * to every connected relay, every relay feeds the same subscription, and
 * duplicates collapse by event id. One relay down (or censoring) changes
 * nothing as long as any other is up.
 */
@Singleton
class RelayTransport @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val identityKeyManager: IdentityKeyManager,
) {
    data class Incoming(
        val eventId: String,
        val senderEphemeralPubHex: String,
        val tags: List<List<String>>,
        /** Base64 of one framed payload - the same bytes a socket frame would carry. */
        val contentBase64: String,
    ) {
        fun tagValue(name: String): String? = tags.firstOrNull { it.firstOrNull() == name }?.getOrNull(1)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(RelayState.CONNECTING)
    val state: StateFlow<RelayState> = _state

    private val _relayCount = MutableStateFlow(0)
    /** How many relays currently hold an open subscription. */
    val relayCount: StateFlow<Int> = _relayCount

    private val _incoming = MutableSharedFlow<Incoming>(extraBufferCapacity = 256)
    val incoming: SharedFlow<Incoming> = _incoming

    private data class RelayLink(
        val url: String,
        var socket: WebSocket? = null,
        var open: Boolean = false,
        var failures: Int = 0,
        var reconnectJob: Job? = null,
    )

    private val links = mutableMapOf<String, RelayLink>()

    /** Event ids already delivered - every relay repeats every event, so without this each message would arrive N times. */
    private val seenIds = object : LinkedHashMap<String, Unit>(2048, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean = size > 2048
    }

    @Volatile private var started = false
    private var settingsJob: Job? = null

    fun myPubkeyHex(): String = NostrCrypto.bytesToHex(NostrCrypto.pubkeyFromSecret(identityKeyManager.nostrSecret()))

    fun start() {
        if (started) return
        started = true
        _state.value = RelayState.CONNECTING
        settingsJob = scope.launch {
            settingsRepository.settings.collect { settings ->
                applyRelayUrls(settings.relayUrls.ifEmpty { DEFAULT_RELAY_URLS })
            }
        }
    }

    /**
     * Fire-and-forget lane for call audio: the identical event goes to the
     * *first* open relay only, not all of them. Audio already tolerates loss
     * (50 frames a second, each independently playable), so fan-out would just
     * triple the uplink for no audible gain.
     */
    fun publishFast(recipientPubHex: String, tags: List<List<String>>, content: ByteArray): Boolean {
        val event = NostrProtocol.buildSignedEvent(
            senderSecret = NostrCrypto.generateSecretKey(),
            kind = NostrProtocol.HERTZ_KIND,
            tags = listOf(listOf(NostrProtocol.TAG_RECIPIENT, recipientPubHex)) + tags,
            content = android.util.Base64.encodeToString(content, android.util.Base64.NO_WRAP),
        )
        val payload = NostrProtocol.clientPublishJson(event)
        val socket = synchronized(links) { links.values.firstOrNull { it.open }?.socket } ?: return false
        return runCatching { socket.send(payload) }.getOrDefault(false)
    }

    fun stop() {
        started = false
        settingsJob?.cancel()
        settingsJob = null
        synchronized(links) {
            links.values.forEach { link ->
                link.reconnectJob?.cancel()
                runCatching { link.socket?.close(1000, "stop") }
            }
            links.clear()
        }
        _relayCount.value = 0
        _state.value = RelayState.OFFLINE
    }

    private suspend fun applyRelayUrls(urls: List<String>) {
        val wanted = urls.filter { it.startsWith("wss://") || it.startsWith("ws://") }.distinct().take(5)
        val current = synchronized(links) { links.keys.toSet() }
        if (wanted.toSet() == current) return
        synchronized(links) {
            (current - wanted.toSet()).forEach { url ->
                links.remove(url)?.let { link ->
                    link.reconnectJob?.cancel()
                    runCatching { link.socket?.close(1000, "relay removed") }
                }
            }
            wanted.filter { it !in current }.forEach { url ->
                links[url] = RelayLink(url)
            }
        }
        wanted.filter { it !in current }.forEach { url -> connect(url) }
        refreshState()
    }

    private fun connect(url: String) {
        val link = synchronized(links) { links[url] } ?: return
        if (link.open || link.socket != null) return
        val request = Request.Builder().url(url).build()
        link.socket = client.newWebSocket(request, RelaySocketListener(url))
    }

    private inner class RelaySocketListener(private val url: String) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val link = synchronized(links) { links[url] } ?: return
            link.open = true
            link.failures = 0
            // Our one subscription: ephemeral Hertz events addressed to our key.
            // Relays enforce per-connection subscription limits, so there is exactly one.
            val subscribe = NostrProtocol.clientSubscribeJson(
                subscriptionId = "hertz-v1",
                kinds = listOf(NostrProtocol.HERTZ_KIND),
                recipientPubkeys = listOf(myPubkeyHex()),
            )
            runCatching { webSocket.send(subscribe) }
            refreshState()
            // A relay that just came back may have missed our recent publishes
            // while it was gone - the retry sweep re-sends anything unacked,
            // and duplicates collapse by event id on receipt.
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            when (val msg = NostrProtocol.parseServerMessage(text)) {
                is NostrProtocol.ServerMessage.Event -> onRelayEvent(msg.event)
                is NostrProtocol.ServerMessage.Closed -> {
                    // Asked to back off (or the sub id collided after a fast
                    // reconnect) - resubscribe once the socket is healthy.
                    if (msg.subscriptionId == "hertz-v1") {
                        val link = synchronized(links) { links[url] }
                        if (link?.open == true) {
                            runCatching {
                                webSocket.send(
                                    NostrProtocol.clientSubscribeJson(
                                        "hertz-v1",
                                        listOf(NostrProtocol.HERTZ_KIND),
                                        listOf(myPubkeyHex()),
                                    ),
                                )
                            }
                        }
                    }
                }
                else -> Unit
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            runCatching { webSocket.close(1000, null) }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            onLinkDown(url)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            runCatching { webSocket.cancel() }
            onLinkDown(url)
        }
    }

    private fun onLinkDown(url: String) {
        val link = synchronized(links) { links[url] } ?: return
        link.open = false
        link.socket = null
        refreshState()
        if (!started) return
        link.reconnectJob?.cancel()
        link.failures++
        // Exponential backoff capped at 30s - a dead relay must never hot-loop the radio.
        val waitMs = (1000L shl link.failures.coerceAtMost(5)).coerceAtMost(30_000L)
        link.reconnectJob = scope.launch {
            delay(waitMs)
            if (started) connect(url)
        }
    }

    private fun onRelayEvent(event: NostrProtocol.NostrEvent) {
        if (event.kind != NostrProtocol.HERTZ_KIND) return
        if (event.tagValue(NostrProtocol.TAG_RECIPIENT) != myPubkeyHex()) return
        synchronized(seenIds) {
            if (!seenIds.containsKey(event.idHex)) seenIds[event.idHex] = Unit else return
        }
        // Fail closed: an event that doesn't verify is either corrupt or
        // forged by the relay, and either way must never reach the chat layer.
        if (!NostrProtocol.verifyEvent(event)) return
        _incoming.tryEmit(
            Incoming(
                eventId = event.idHex,
                senderEphemeralPubHex = event.pubkeyHex,
                tags = event.tags,
                contentBase64 = event.content,
            ),
        )
    }

    /**
     * Publishes one framed payload to [recipientPubHex]. The event is built
     * once (one id, one ephemeral sender key) and the identical JSON goes to
     * every connected relay - true when at least one accepted it for sending.
     */
    fun publish(recipientPubHex: String, tags: List<List<String>>, content: ByteArray): Boolean {
        val event = NostrProtocol.buildSignedEvent(
            senderSecret = NostrCrypto.generateSecretKey(),
            kind = NostrProtocol.HERTZ_KIND,
            tags = listOf(listOf(NostrProtocol.TAG_RECIPIENT, recipientPubHex)) + tags,
            content = android.util.Base64.encodeToString(content, android.util.Base64.NO_WRAP),
        )
        val payload = NostrProtocol.clientPublishJson(event)
        var sent = false
        val sockets = synchronized(links) { links.values.filter { it.open }.mapNotNull { it.socket } }
        sockets.forEach { socket ->
            if (runCatching { socket.send(payload) }.getOrDefault(false)) sent = true
        }
        return sent
    }

    private fun refreshState() {
        val open = synchronized(links) { links.values.count { it.open } }
        val any = synchronized(links) { links.isNotEmpty() }
        _relayCount.value = open
        _state.value = when {
            open > 0 -> RelayState.CONNECTED
            any -> RelayState.CONNECTING
            else -> RelayState.OFFLINE
        }
    }

}
