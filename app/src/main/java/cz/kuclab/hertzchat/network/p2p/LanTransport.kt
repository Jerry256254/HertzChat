package cz.kuclab.hertzchat.network.p2p

import android.app.Application
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
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

private const val SERVICE_TYPE = "_hertzchat._tcp."
private const val ATTR_CONTACT_ID = "cid"
private const val CONNECT_TIMEOUT_MS = 4_000

/**
 * Finds and connects to contacts on the same local network (same Wi-Fi or hotspot)
 * with genuinely zero infrastructure - no server, no bootstrap host, no reseed, not
 * even an internet connection. Each device announces itself over mDNS/DNS-SD (the
 * same mechanism printers and Chromecasts use) and connects directly by IP.
 *
 * This is the one configuration where "no servers at all" is literally true. Over
 * the internet it can't work - two phones behind different NATs have no way to find
 * each other without *something* in the middle - which is what [I2pTransport] is
 * for. The two run side by side: LAN is tried first because it's faster and works
 * offline, I2P covers everything else.
 *
 * Sockets handed out here are plain [Socket]s carrying the exact same length-prefixed
 * frames as I2P ones, so everything above this layer (Signal sessions, media chunking,
 * delivery state) is identical and transport-agnostic.
 */
@Singleton
class LanTransport @Inject constructor(
    private val app: Application,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nsdManager by lazy { app.getSystemService(android.content.Context.NSD_SERVICE) as NsdManager }

    private var serverSocket: ServerSocket? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    /** contactId -> last known address on this network. */
    private val peers = ConcurrentHashMap<String, InetSocketAddress>()

    private val _peerCount = MutableStateFlow(0)
    val peerCount: StateFlow<Int> = _peerCount

    private val _incomingConnections = MutableSharedFlow<Socket>(extraBufferCapacity = 16)
    val incomingConnections: SharedFlow<Socket> = _incomingConnections

    fun start(myContactId: String) {
        if (serverSocket != null) return
        runCatching {
            val socket = ServerSocket(0)
            serverSocket = socket
            acceptLoop(socket)
            register(myContactId, socket.localPort)
            discover(myContactId)
        }
    }

    private fun acceptLoop(socket: ServerSocket) {
        scope.launch {
            while (true) {
                val incoming = runCatching { socket.accept() }.getOrNull() ?: break
                _incomingConnections.tryEmit(incoming)
            }
        }
    }

    private fun register(myContactId: String, port: Int) {
        val info = NsdServiceInfo().apply {
            // The service name is only a display label and gets renamed on collision;
            // the contactId in the attributes is what peers actually match on.
            serviceName = "Hertz-" + myContactId.take(8)
            serviceType = SERVICE_TYPE
            this.port = port
            setAttribute(ATTR_CONTACT_ID, myContactId)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo?) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo?, errorCode: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo?) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo?, errorCode: Int) = Unit
        }
        registrationListener = listener
        runCatching { nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    private fun discover(myContactId: String) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String?) = Unit
            override fun onDiscoveryStopped(serviceType: String?) = Unit
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit

            override fun onServiceFound(info: NsdServiceInfo?) {
                if (info == null || info.serviceType?.contains("hertzchat") != true) return
                resolve(info, myContactId)
            }

            override fun onServiceLost(info: NsdServiceInfo?) {
                val name = info?.serviceName ?: return
                peers.entries.removeIf { it.key.take(8) == name.removePrefix("Hertz-").take(8) }
                _peerCount.value = peers.size
            }
        }
        discoveryListener = listener
        runCatching { nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    private fun resolve(info: NsdServiceInfo, myContactId: String) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo?, errorCode: Int) = Unit
            override fun onServiceResolved(resolved: NsdServiceInfo?) {
                resolved ?: return
                val contactId = resolved.attributes[ATTR_CONTACT_ID]?.toString(Charsets.UTF_8) ?: return
                if (contactId == myContactId) return // our own announcement echoed back
                val host = resolved.host ?: return
                peers[contactId] = InetSocketAddress(host, resolved.port)
                _peerCount.value = peers.size
            }
        }
        runCatching { nsdManager.resolveService(info, resolveListener) }
    }

    /** Non-null when this contact is reachable on the current local network. */
    fun addressFor(contactId: String): InetSocketAddress? = peers[contactId]

    /** Opens a direct LAN socket, or throws if that contact isn't on this network right now. */
    fun connectTo(contactId: String): Socket {
        val address = peers[contactId] ?: error("Kontakt není v místní síti")
        return Socket().apply { connect(address, CONNECT_TIMEOUT_MS) }
    }

    fun stop() {
        runCatching { registrationListener?.let { nsdManager.unregisterService(it) } }
        registrationListener = null
        runCatching { discoveryListener?.let { nsdManager.stopServiceDiscovery(it) } }
        discoveryListener = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        peers.clear()
        _peerCount.value = 0
    }
}
