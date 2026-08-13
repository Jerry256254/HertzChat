package cz.kuclab.hertzchat.network.webrtc

import android.content.Context
import java.nio.ByteBuffer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

/**
 * Application-wide WebRTC factory. STUN/TURN only ever help two phones find
 * a *direct* network path to each other - the media/data itself always
 * travels end-to-end encrypted (both by WebRTC's own DTLS and by our Signal
 * Protocol layer on top), so even a TURN relay only ever forwards opaque bytes.
 */
object WebRtcFactoryHolder {
    @Volatile private var factory: PeerConnectionFactory? = null

    fun get(context: Context): PeerConnectionFactory {
        factory?.let { return it }
        synchronized(this) {
            factory?.let { return it }
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                    .createInitializationOptions(),
            )
            val created = PeerConnectionFactory.builder().createPeerConnectionFactory()
            factory = created
            return created
        }
    }
}

sealed interface P2pEvent {
    data class IceCandidateGenerated(val candidate: IceCandidate) : P2pEvent
    data class ConnectionStateChanged(val state: PeerConnection.PeerConnectionState) : P2pEvent
    data class DataChannelOpen(val open: Boolean) : P2pEvent
    data class MessageReceived(val bytes: ByteArray) : P2pEvent
}

fun defaultIceServers(turnUrl: String? = null, turnUsername: String? = null, turnPassword: String? = null): List<PeerConnection.IceServer> {
    val servers = mutableListOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
    )
    if (!turnUrl.isNullOrBlank()) {
        servers += PeerConnection.IceServer.builder(turnUrl)
            .setUsername(turnUsername.orEmpty())
            .setPassword(turnPassword.orEmpty())
            .createIceServer()
    }
    return servers
}

/** One WebRTC connection to exactly one contact, carrying a single reliable, ordered data channel used to exchange encrypted chat envelopes. */
class PeerConnection2(
    context: Context,
    iceServers: List<PeerConnection.IceServer>,
    private val isInitiator: Boolean,
) {
    private val factory = WebRtcFactoryHolder.get(context)
    private val events = MutableSharedFlow<P2pEvent>(extraBufferCapacity = 256)
    val eventFlow: SharedFlow<P2pEvent> = events

    private var dataChannel: DataChannel? = null

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
        override fun onIceCandidate(candidate: IceCandidate) {
            events.tryEmit(P2pEvent.IceCandidateGenerated(candidate))
        }
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
        override fun onAddStream(stream: MediaStream?) = Unit
        override fun onRemoveStream(stream: MediaStream?) = Unit
        override fun onDataChannel(channel: DataChannel) {
            attachDataChannel(channel)
        }
        override fun onRenegotiationNeeded() = Unit
        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            events.tryEmit(P2pEvent.ConnectionStateChanged(newState))
        }
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) = Unit
    }

    private val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
        sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
    }

    private val connection: PeerConnection = requireNotNull(
        factory.createPeerConnection(rtcConfig, observer),
    ) { "Failed to create PeerConnection" }

    init {
        if (isInitiator) {
            attachDataChannel(connection.createDataChannel("hertzchat", DataChannel.Init()))
        }
    }

    private fun attachDataChannel(channel: DataChannel) {
        dataChannel = channel
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit
            override fun onStateChange() {
                events.tryEmit(P2pEvent.DataChannelOpen(channel.state() == DataChannel.State.OPEN))
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                events.tryEmit(P2pEvent.MessageReceived(bytes))
            }
        })
    }

    fun send(bytes: ByteArray): Boolean {
        val channel = dataChannel ?: return false
        return channel.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), true))
    }

    fun createOffer(onCreated: (SessionDescription) -> Unit) {
        connection.createOffer(
            sdpObserver(onCreateSuccess = { desc ->
                connection.setLocalDescription(sdpObserver(onSetSuccess = { onCreated(desc) }), desc)
            }),
            MediaConstraints(),
        )
    }

    fun createAnswer(onCreated: (SessionDescription) -> Unit) {
        connection.createAnswer(
            sdpObserver(onCreateSuccess = { desc ->
                connection.setLocalDescription(sdpObserver(onSetSuccess = { onCreated(desc) }), desc)
            }),
            MediaConstraints(),
        )
    }

    fun setRemoteDescription(description: SessionDescription) {
        connection.setRemoteDescription(sdpObserver(), description)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        connection.addIceCandidate(candidate)
    }

    fun close() {
        dataChannel?.unregisterObserver()
        dataChannel?.close()
        connection.close()
    }

    private fun sdpObserver(
        onCreateSuccess: (SessionDescription) -> Unit = {},
        onSetSuccess: () -> Unit = {},
    ) = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = onCreateSuccess(description)
        override fun onSetSuccess() = onSetSuccess()
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }
}
