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
// First-ever router boot on a phone CPU (key generation, no warm netDb) can
// legitimately take past a minute - these were too tight and could surface a
// "failed" error for a router that just needed more time, which reads the same
// as "still doesn't work" to a user who then just retries into the same wall.
private const val ROUTER_START_TIMEOUT_MS = 150_000L
private const val SOCKET_MANAGER_TIMEOUT_MS = 150_000L
private const val I2CP_RETRY_DELAY_MS = 250L
private const val READY_PEER_COUNT = 1
private const val READINESS_MONITOR_TIMEOUT_MS = 60_000L
/** Same threshold the router's own reseed check uses - below this the netDb is too small to build tunnels from. */
private const val KNOWN_ROUTERS_TARGET = 50

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

    private val _bootstrapLabel = MutableStateFlow<String?>(null)
    /** What the router is actually doing right now, so a slow start reads as progress rather than a freeze. */
    val bootstrapLabel: StateFlow<String?> = _bootstrapLabel

    /** Where the current phase is allowed to reach; the ticker walks the visible value here. */
    private val targetPercent = MutableStateFlow(0)

    /** Progress only ever moves forward - a dip would read as something having gone wrong. */
    private fun advanceTo(percent: Int, label: String) {
        if (percent > targetPercent.value) targetPercent.value = percent
        _bootstrapLabel.value = label
    }

    /**
     * Walks the displayed percentage toward whatever the current phase allows, instead of
     * letting it sit on one number and then jump. Phases here are genuinely long (opening
     * a destination waits on the router's I2CP listener), so without this the bar looked
     * stuck at 10% and then leapt to done.
     */
    private fun startProgressTicker() {
        scope.launch {
            while (_state.value == I2pState.STARTING) {
                val target = targetPercent.value
                val shown = _bootstrapPercent.value
                if (shown < target) _bootstrapPercent.value = shown + 1
                kotlinx.coroutines.delay(if (target - shown > 10) 120 else 400)
            }
        }
    }

    private val _i2pDestination = MutableStateFlow<String?>(null)
    val i2pDestination: StateFlow<String?> = _i2pDestination

    private val _error = MutableStateFlow<String?>(null)
    /** Non-null when I2P failed to start - surfaced to the UI instead of leaving the user staring at an infinite spinner. */
    val error: StateFlow<String?> = _error

    private val _diagnostics = MutableStateFlow<String?>(null)
    /** Known-router count + reseed status, so a report of "still doesn't connect" comes with an actual reason instead of a guess. */
    val diagnostics: StateFlow<String?> = _diagnostics

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
        advanceTo(15, "Spouští se router I2P")
        startProgressTicker()

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

            advanceTo(35, "Otevírá se tvoje adresa v síti")

            // Reseeding and opening our destination don't depend on each other, so start
            // the (network-bound, potentially slow) reseed immediately and let it run while
            // the destination is being opened, rather than serialising the two.
            scope.launch { runCatching { requestReseedIfNeeded() } }

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

            advanceTo(45, "Hledají se routery v síti")

            // Best-effort progress/ready signal only - a failure here must never crash the
            // app or leave the destination we just opened unusable, so it's non-fatal.
            runCatching { monitorReadiness() }.onFailure {
                _state.value = I2pState.CONNECTED
            }
        }
    }

    private fun describeFailure(e: Throwable, action: String): String =
        if (e is TimeoutCancellationException) {
            "$action trvá příliš dlouho - zkontroluj internetové připojení a zkus to znovu."
        } else {
            e.message ?: e.javaClass.simpleName
        }

    /**
     * Copies I2P's own signing certificates out of our assets into the router's base
     * directory. This is not optional: reseeding (fetching the initial list of routers,
     * without which the router can never find a single peer) only accepts su3-signed
     * bundles - `Reseeder.ENABLE_SU3=true`, `ENABLE_NON_SU3=false` - and su3
     * verification resolves signing keys through `DirKeyRing` over
     * `<base>/certificates/reseed`. A normal I2P install ships that directory; none of
     * the Maven artifacts we depend on contain a single .crt, so without this the
     * download succeeds, the signature fails to verify, zero routers get imported, and
     * the router sits at zero peers forever - exactly the symptom seen on device.
     */
    private fun installCertificates(i2pDir: File) {
        val certRoot = File(i2pDir, "certificates")
        listOf("reseed", "ssl").forEach { kind ->
            val targetDir = File(certRoot, kind).apply { mkdirs() }
            val names = runCatching { app.assets.list("i2p-certificates/$kind") }.getOrNull() ?: return@forEach
            names.forEach { name ->
                val target = File(targetDir, name)
                if (target.exists()) return@forEach
                runCatching {
                    app.assets.open("i2p-certificates/$kind/$name").use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }

    private fun startRouter() {
        val i2pDir = File(app.filesDir, "i2p")
        i2pDir.mkdirs()
        val logDir = File(i2pDir, "logs").apply { mkdirs() }
        installCertificates(i2pDir)

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
            // A phone is a client, not infrastructure: refusing to relay other people's
            // tunnels keeps the CPU, battery and uplink for our own traffic, which is
            // most of what makes the first connection feel slow.
            setProperty("router.maxParticipatingTunnels", "0")
            setProperty("router.enableLoadTesting", "false")
        }

        val r = Router(props)
        router = r
        r.runRouter()
    }

    /**
     * Tunnel settings for our own destination, tuned for how this app is actually used:
     * a chat client that has to be reachable within seconds of opening, on a phone.
     *
     * Two hops each way instead of I2P's default three. The property that matters for a
     * messenger is preserved at two - neither the person you're talking to nor any single
     * relay learns your IP - while dropping a hop cuts both the time to build a working
     * tunnel and the latency of every message through it. Going to one hop would be
     * faster still but lets a single relay see your IP and your destination at once,
     * which is too much to give up here.
     *
     * Tunnels are also kept alive rather than torn down when idle: rebuilding them on the
     * next message is exactly the multi-second stall this is meant to avoid.
     */
    private fun tunnelOptions(): Properties = Properties().apply {
        setProperty("inbound.length", "2")
        setProperty("outbound.length", "2")
        setProperty("inbound.lengthVariance", "0")
        setProperty("outbound.lengthVariance", "0")
        setProperty("inbound.quantity", "3")
        setProperty("outbound.quantity", "3")
        setProperty("inbound.backupQuantity", "0")
        setProperty("outbound.backupQuantity", "0")
        setProperty("inbound.nickname", "Hertz Chat")
        setProperty("i2cp.reduceOnIdle", "false")
        setProperty("i2cp.closeOnIdle", "false")
        setProperty("i2cp.dontPublishLeaseSet", "false")
        // Without this I2P refuses a connection whose destination is our own ("local
        // loopback denied"), which is what adding yourself as a contact does. Routing
        // it through real tunnels costs a little latency but keeps every layer above
        // this one identical, instead of needing a special-case delivery path.
        setProperty("i2cp.disableLoopback", "true")
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
            Base64.decode(persistedPrivateKeyBase64, Base64.NO_WRAP).also { bytes ->
                // The private-key blob starts with the Destination itself, so our own
                // address is known the moment we have the stored key - no need to make
                // the QR code wait for the router's I2CP listener to come up, which is
                // what left returning users staring at a spinner while the network
                // bootstrapped (and forever, if bootstrapping never finished).
                runCatching {
                    _i2pDestination.value = Destination().apply { readBytes(ByteArrayInputStream(bytes)) }.toBase64()
                }
            }
        }

        // createManager() connects to the router's local I2CP port and returns null
        // (rather than throwing) if that listener isn't up yet - runRouter() returns
        // once startup is kicked off, not once every subsystem is actually ready, so
        // this has to be retried instead of assumed to work on the first try.
        var manager: I2PSocketManager? = null
        while (manager == null) {
            manager = I2PSocketManagerFactory.createManager(ByteArrayInputStream(keyBytes), I2CP_HOST, I2CP_PORT, tunnelOptions())
            if (manager == null) Thread.sleep(I2CP_RETRY_DELAY_MS)
        }
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

    // The destination is already open and usable for connections by the time this
    // runs - countActivePeers() (peers we've had recent successful comms with) is
    // only used as a cosmetic bootstrap indicator, never a gate on functionality.
    // It's capped to a hard timeout so a slow/stuck reseed can't leave the UI
    // spinning at "0%" forever with no way out, the same class of bug already
    // fixed for Tor's "Navazuje se spojení..." hang.
    /**
     * A normal full I2P install has a console webapp that offers a "reseed now" button
     * and triggers this on first boot - our headless embedded router has no such webapp,
     * so it's fired explicitly rather than assuming the router core's own auto-trigger
     * covers this setup. No-op once the netDb already knows enough routers, which is why
     * warm starts skip straight past it.
     */
    private fun requestReseedIfNeeded() {
        val r = router ?: return
        val netDb = r.context.netDb()
        netDb.reseedChecker().checkReseed(net.i2p.router.networkdb.reseed.ReseedChecker.MINIMUM)
    }

    private suspend fun monitorReadiness() {
        val r = router ?: return
        val netDb = r.context.netDb()
        val reseedChecker = runCatching { netDb.reseedChecker() }.getOrNull()

        val deadline = System.currentTimeMillis() + READINESS_MONITOR_TIMEOUT_MS
        var tick = 0
        while (router === r) {
            tick++
            val peers = r.context.commSystem().countActivePeers()
            val knownRouters = runCatching { netDb.knownRouters }.getOrDefault(0)
            // The reseed status/error is blank whenever nothing is wrong, so it's only
            // worth a line when it actually says something - otherwise it rendered as a
            // dangling "reseed:" with nothing after it.
            val reseedText = reseedChecker?.let { it.error ?: it.status }?.takeIf { it.isNotBlank() }
            _diagnostics.value = buildString {
                append("Známé routery: $knownRouters, aktivní sousedé: $peers")
                reseedText?.let { append(", reseed: $it") }
            }
            // Progress used to be peers/1, i.e. 0% until the first peer and then a jump
            // straight to 100 - which reads as "frozen, then suddenly done". These are
            // the two things that actually happen in order, each mapped to its own band:
            // filling the netDb (routers we know of), then building tunnels through it.
            if (knownRouters < KNOWN_ROUTERS_TARGET) {
                val share = knownRouters * 30 / KNOWN_ROUTERS_TARGET
                advanceTo(45 + share, "Stahuje se seznam routerů v síti ($knownRouters z $KNOWN_ROUTERS_TARGET)")
            } else {
                val share = (peers * 20 / READY_PEER_COUNT).coerceAtMost(20)
                advanceTo(75 + share, "Staví se tunely (známých routerů: $knownRouters)")
            }

            if (peers >= READY_PEER_COUNT || System.currentTimeMillis() >= deadline) {
                targetPercent.value = 100
                _bootstrapPercent.value = 100
                _bootstrapLabel.value = null
                _state.value = I2pState.CONNECTED
                // Once we're up this is just noise on the Contacts screen; it exists to
                // explain a *failure to* connect, not to sit there permanently.
                _diagnostics.value = null
                return
            }
            // Nudge the ceiling up slowly while a phase is genuinely waiting, so the
            // ticker always has somewhere to go. Capped below the next band so it can
            // never claim more than has actually happened.
            val ceiling = if (knownRouters < KNOWN_ROUTERS_TARGET) 74 else 95
            if (tick % 8 == 0 && targetPercent.value < ceiling) targetPercent.value += 1
            kotlinx.coroutines.delay(500)
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
