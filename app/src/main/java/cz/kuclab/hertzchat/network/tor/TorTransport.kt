package cz.kuclab.hertzchat.network.tor

import android.app.Application
import android.os.Build
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
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
import org.briarproject.android.dontkillmelib.wakelock.AndroidWakeLockManagerFactory
import org.briarproject.onionwrapper.AndroidTorWrapper
import org.briarproject.onionwrapper.TorWrapper
import org.briarproject.socks.SocksSocketFactory

private const val START_TIMEOUT_MS = 120_000L
private const val PUBLISH_TIMEOUT_MS = 60_000L

private const val LOCAL_LISTEN_PORT = 47821
private const val HIDDEN_SERVICE_PORT = 47822
private const val SOCKS_PORT = 59050
private const val CONTROL_PORT = 59051

/**
 * Two Hertz Chat devices find and reach each other over the public Tor
 * network - each device publishes a small Tor onion (hidden) service, and
 * contacts connect directly to that .onion address. No server of ours (or
 * anyone's) is involved: Tor is free, decentralized, requires no account,
 * and as a side effect also hides both parties' real IP addresses from
 * each other and solves NAT traversal, since onion services are reachable
 * from anywhere without port forwarding or STUN/ICE.
 */
@Singleton
class TorTransport @Inject constructor(
    private val app: Application,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ioExecutor = Executors.newCachedThreadPool()
    private val eventExecutor = Executors.newSingleThreadExecutor()

    private var torWrapper: TorWrapper? = null
    private var serverSocket: ServerSocket? = null

    private val _state = MutableStateFlow(TorWrapper.TorState.NOT_STARTED)
    val state: StateFlow<TorWrapper.TorState> = _state

    private val _bootstrapPercent = MutableStateFlow(0)
    val bootstrapPercent: StateFlow<Int> = _bootstrapPercent

    private val _onionAddress = MutableStateFlow<String?>(null)
    val onionAddress: StateFlow<String?> = _onionAddress

    private val _error = MutableStateFlow<String?>(null)
    /** Non-null when Tor failed to start - surfaced to the UI instead of leaving the user staring at an infinite spinner. */
    val error: StateFlow<String?> = _error

    private val _incomingConnections = MutableSharedFlow<Socket>(extraBufferCapacity = 16)
    val incomingConnections: SharedFlow<Socket> = _incomingConnections

    /**
     * Starts the embedded Tor client and publishes our onion service.
     * [persistedPrivateKey] should be whatever [onKeySaved] returned the
     * first time this ran, so the .onion address stays stable across restarts.
     */
    fun start(persistedPrivateKey: String, onKeySaved: (String) -> Unit) {
        if (torWrapper != null) return
        _error.value = null

        val wakeLockManager = AndroidWakeLockManagerFactory.createAndroidWakeLockManager(app)
        val torDirectory = File(app.filesDir, "tor")
        val wrapper = AndroidTorWrapper(
            app,
            wakeLockManager,
            ioExecutor,
            eventExecutor,
            Build.SUPPORTED_ABIS.first(),
            torDirectory,
            SOCKS_PORT,
            CONTROL_PORT,
        )
        torWrapper = wrapper

        wrapper.setObserver(object : TorWrapper.Observer {
            override fun onState(state: TorWrapper.TorState) {
                _state.value = state
                if (state == TorWrapper.TorState.STARTED) {
                    scope.launch {
                        runCatching { publish(persistedPrivateKey, onKeySaved) }.onFailure { e ->
                            _error.value = if (e is TimeoutCancellationException) {
                                "Zveřejnění tvé adresy v síti Tor trvá příliš dlouho - zkus to znovu."
                            } else {
                                e.message ?: e.javaClass.simpleName
                            }
                            runCatching { wrapper.stop() }
                            torWrapper = null
                        }
                    }
                }
            }
            override fun onBootstrapPercentage(percentage: Int) {
                _bootstrapPercent.value = percentage
            }
            override fun onHsDescriptorUpload(onion: String) = Unit
            override fun onClockSkewDetected(skewSeconds: Long) = Unit
        })

        scope.launch {
            // wrapper.start() is a plain blocking call (not suspend-aware), so a bare
            // withTimeout around it can't actually interrupt it - runInterruptible does,
            // by calling Thread.interrupt() on timeout, which unblocks the library's
            // internal wait loop. Without this, a stuck Tor process used to hang the UI
            // forever with no error and no way to retry - the actual bug behind the
            // "connecting..." spinner that never resolves.
            val result = runCatching { withTimeout(START_TIMEOUT_MS) { runInterruptible(Dispatchers.IO) { wrapper.start() } } }
            result.onFailure { e ->
                _error.value = if (e is TimeoutCancellationException) {
                    "Připojení k síti Tor trvá příliš dlouho - zkontroluj internetové připojení a zkus to znovu."
                } else {
                    e.message ?: e.javaClass.simpleName
                }
                runCatching { wrapper.stop() }
                torWrapper = null
            }
        }
    }

    /** Clears a previous failure and tries again - torWrapper was reset to null on failure so [start] will actually retry. */
    fun retry(persistedPrivateKey: String, onKeySaved: (String) -> Unit) {
        if (torWrapper != null) return
        start(persistedPrivateKey, onKeySaved)
    }

    private suspend fun publish(persistedPrivateKey: String, onKeySaved: (String) -> Unit) {
        val wrapper = torWrapper ?: return
        startLocalServer()
        val properties = withTimeout(PUBLISH_TIMEOUT_MS) {
            runInterruptible(Dispatchers.IO) { wrapper.publishHiddenService(HIDDEN_SERVICE_PORT, LOCAL_LISTEN_PORT, persistedPrivateKey) }
        }
        if (persistedPrivateKey.isEmpty()) onKeySaved(properties.privKey)
        _onionAddress.value = properties.onion
    }

    private fun startLocalServer() {
        if (serverSocket != null) return
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress("127.0.0.1", LOCAL_LISTEN_PORT))
        serverSocket = socket
        scope.launch {
            while (true) {
                val incoming = runCatching { socket.accept() }.getOrNull() ?: break
                _incomingConnections.tryEmit(incoming)
            }
        }
    }

    /** Connects to a contact's onion address through our local Tor SOCKS proxy. Blocks until connected or throws - call from a background dispatcher. */
    fun connectTo(onionAddress: String, port: Int = HIDDEN_SERVICE_PORT, timeoutMs: Int = 45_000): Socket {
        val proxy = InetSocketAddress("127.0.0.1", SOCKS_PORT)
        val factory = SocksSocketFactory(proxy, 10_000, timeoutMs, timeoutMs)
        return factory.createSocket(onionAddress, port)
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { torWrapper?.stop() }
        torWrapper = null
        _state.value = TorWrapper.TorState.STOPPED
        _onionAddress.value = null
    }
}
