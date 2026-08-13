package cz.kuclab.hertzchat.network.p2p

import android.app.Application
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import net.i2p.client.I2PClientFactory
import net.i2p.client.streaming.I2PSocketManager
import net.i2p.client.streaming.I2PSocketManagerFactory
import net.i2p.data.Destination
import net.i2p.router.Router

enum class I2pState { NOT_STARTED, STARTING, CONNECTED, STOPPED }

private const val I2CP_HOST = "127.0.0.1"
private const val I2CP_PORT = 7654
private const val ROUTER_START_TIMEOUT_MS = 90_000L
private const val SOCKET_MANAGER_TIMEOUT_MS = 60_000L
private const val READY_PEER_COUNT = 3

/**
 * Two Hertz Chat devices find and reach each other over the public I2P
 * network - each device is its own I2P "destination" (the equivalent of a
 * Tor onion address), and contacts connect directly to that destination.
 * No server of ours (or anyone's) is involved: I2P is free, decentralized,
 * requires no account, and as a side effect also hides both parties' real
 * IP addresses from each other and solves NAT traversal.
 *
 * Unlike the Tor daemon this replaces, the I2P router runs as a plain Java
 * object in this process (`Router(props).runRouter()`), not a separate
 * executable launched via ProcessBuilder - so Android's restrictions on
 * executing arbitrary binaries as subprocesses (which broke Tor on real
 * hardware) don't apply here.
 */
@Singleton
class I2pTransport @Inject constructor(
    private val app: Application,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var router: Router? = null
    private var socketManager: I2PSocketManager? = null
    private var serverSocket: ServerSocket? = null

    private val _state = MutableStateFlow(I2pState.NOT_STARTED)
    val state: StateFlow<I2pState> = _state

    private val _bootstrapPercent = MutableStateFlow(0)
    val bootstrapPercent: StateFlow<Int> = _bootstrapPercent

    private val _i2pDestination = MutableStateFlow<String?>(null)
    val i2pDestination: StateFlow<String?> = _i2pDestination

    private val _error = MutableStateFlow<String?>(null)
    /** Non-null when I2P failed to start - surfaced to the UI instead of leaving the user staring at an infinite spinner. */
    val error: StateFlow<String?> = _error

    private val _incomingConnections = MutableSharedFlow<Socket>(extraBufferCapacity = 16)
    val incomingConnections: SharedFlow<Socket> = _incomingConnections

    /**
     * Starts the embedded I2P router and opens our destination.
     * [persistedPrivateKeyBase64] should be whatever [onKeySaved] returned the
     * first time this ran, so our destination stays stable across restarts.
     */
    fun start(persistedPrivateKeyBase64: String, onKeySaved: (String) -> Unit) {
        if (router != null) return
        _error.value = null
        _state.value = I2pState.STARTING

        scope.launch {
            val result = runCatching {
                withTimeout(ROUTER_START_TIMEOUT_MS) {
                    runInterruptible(Dispatchers.IO) { startRouter() }
                }
            }
            result.onFailure { e ->
                _error.value = describeFailure(e, "Spuštění routeru I2P")
                cleanup()
                return@launch
            }

            val openResult = runCatching {
                withTimeout(SOCKET_MANAGER_TIMEOUT_MS) {
                    runInterruptible(Dispatchers.IO) { openDestination(persistedPrivateKeyBase64, onKeySaved) }
                }
            }
            openResult.onFailure { e ->
                _error.value = describeFailure(e, "Otevření adresy v síti I2P")
                cleanup()
                return@launch
            }

            monitorReadiness()
        }
    }

    private fun describeFailure(e: Throwable, action: String): String =
        if (e is TimeoutCancellationException) {
            "$action trvá příliš dlouho - zkontroluj internetové připojení a zkus to znovu."
        } else {
            e.message ?: e.javaClass.simpleName
        }

    private fun startRouter() {
        val i2pDir = File(app.filesDir, "i2p")
        i2pDir.mkdirs()
        val logDir = File(i2pDir, "logs").apply { mkdirs() }

        val props = Properties().apply {
            setProperty("i2p.dir.base", i2pDir.absolutePath)
            setProperty("i2p.dir.config", i2pDir.absolutePath)
            setProperty("i2p.dir.router", i2pDir.absolutePath)
            setProperty("i2p.dir.pid", i2pDir.absolutePath)
            setProperty("i2p.dir.log", logDir.absolutePath)
            setProperty("i2p.dir.app", i2pDir.absolutePath)
            setProperty("router.trustedUpdate", "false")
            setProperty("router.updateDisabled", "true")
            setProperty("routerconsole.startAtLogin", "false")
            setProperty("i2p.vmCommandUsed", "true")
            // Headless: no console/UPnP/plugins - just the router core and I2CP for our own streaming session.
            setProperty("router.startupDelay", "0")
            setProperty("i2cp.port", I2CP_PORT.toString())
            setProperty("i2np.udp.enable", "true")
            setProperty("i2np.ntcp.enable", "true")
            setProperty("i2np.ntcp.autoip", "true")
            setProperty("i2np.ntcp.autoport", "true")
            setProperty("i2np.udp.autoip", "true")
            setProperty("i2np.udp.autoport", "true")
            setProperty("router.floodfillParticipant", "false")
        }

        val r = Router(props)
        router = r
        r.runRouter()
    }

    private fun openDestination(persistedPrivateKeyBase64: String, onKeySaved: (String) -> Unit) {
        val keyBytes = if (persistedPrivateKeyBase64.isEmpty()) {
            val client = I2PClientFactory.createClient()
            val out = ByteArrayOutputStream()
            val dest: Destination = client.createDestination(out)
            _i2pDestination.value = dest.toBase64()
            val bytes = out.toByteArray()
            onKeySaved(Base64.encodeToString(bytes, Base64.NO_WRAP))
            bytes
        } else {
            Base64.decode(persistedPrivateKeyBase64, Base64.NO_WRAP)
        }

        val manager = I2PSocketManagerFactory.createManager(ByteArrayInputStream(keyBytes), I2CP_HOST, I2CP_PORT, Properties())
        socketManager = manager
        if (_i2pDestination.value == null) {
            _i2pDestination.value = manager.session.myDestination.toBase64()
        }
        val standardServerSocket = manager.standardServerSocket
        serverSocket = standardServerSocket
        scope.launch {
            while (true) {
                val incoming = runCatching { standardServerSocket.accept() }.getOrNull() ?: break
                _incomingConnections.tryEmit(incoming)
            }
        }
    }

    private suspend fun monitorReadiness() {
        val r = router ?: return
        while (router === r) {
            val peers = r.context.commSystem().countActivePeers()
            _bootstrapPercent.value = (peers * 100 / READY_PEER_COUNT).coerceAtMost(100)
            if (peers >= READY_PEER_COUNT) {
                _state.value = I2pState.CONNECTED
                return
            }
            kotlinx.coroutines.delay(2_000)
        }
    }

    /** Connects to a contact's I2P destination. Blocks until connected or throws - call from a background dispatcher. */
    fun connectTo(destinationBase64: String, timeoutMs: Int = 45_000): Socket {
        val manager = socketManager ?: error("I2P ještě není připravené")
        val dest = Destination(destinationBase64)
        return manager.connectToSocket(dest, timeoutMs)
    }

    fun stop() {
        cleanup()
        _state.value = I2pState.STOPPED
        _i2pDestination.value = null
    }

    private fun cleanup() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { socketManager?.destroySocketManager() }
        socketManager = null
        runCatching { router?.shutdown(0) }
        router = null
    }
}
