package cz.kuclab.hertzchat.p2p

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Base64
import cz.kuclab.hertzchat.data.model.ChatPayload
import cz.kuclab.hertzchat.data.model.PayloadKind
import cz.kuclab.hertzchat.data.repository.ChatServiceEvent
import cz.kuclab.hertzchat.data.repository.P2pChatService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

private const val CALL_SAMPLE_RATE = 8000
private const val CALL_FRAME_SAMPLES = 160
private const val CALL_RING_TIMEOUT_MS = 30_000L
/** Outbound frames past this backlog are dropped, never queued - stale audio is worse than a gap. */
private const val SEND_QUEUE_CAPACITY = 50
/** Inbound jitter buffer in 20ms frames - ~half a second, then old frames drop. */
private const val PLAY_QUEUE_CAPACITY = 25

enum class CallEndReason { DECLINED, MISSED, NO_ANSWER, HANGUP, FAILED, BUSY }

sealed interface CallState {
    data object Idle : CallState
    data class Outgoing(val contactId: String, val callId: String) : CallState
    data class Incoming(val contactId: String, val callId: String) : CallState
    data class Active(val contactId: String, val callId: String, val startedAtMs: Long) : CallState
    data class Ended(val contactId: String, val reason: CallEndReason) : CallState
}

/**
 * Realtime 1:1 voice calls over the existing P2P transport. Deliberately low-fi:
 * 8kHz mono PCM in 20ms frames, which squeezes through an I2P tunnel where
 * wideband audio never would.
 *
 * Encryption is exactly the chat's: every signaling packet and every audio frame
 * travels as a [ChatPayload] through the contact's Signal session (X3DH +
 * Double Ratchet, fresh key per packet with forward secrecy). No separate key
 * exchange, no plaintext anywhere, nothing stored - audio frames are never
 * written to the database, only played.
 */
@Singleton
class CallManager @Inject constructor(
    @ApplicationContext private val app: Context,
    private val p2pChatService: P2pChatService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _state = MutableStateFlow<CallState>(CallState.Idle)
    val state: StateFlow<CallState> = _state

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted

    private val _speaker = MutableStateFlow(false)
    val speaker: StateFlow<Boolean> = _speaker

    /** Live mic level 0..1 for the strands - perceptual curve, same as recording. */
    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel

    private var ringJob: Job? = null
    private var senderJob: Job? = null
    private var captureThread: Thread? = null
    private var playbackThread: Thread? = null
    @Volatile private var audioRunning = false
    private val sendQueue = ArrayBlockingQueue<ShortArray>(SEND_QUEUE_CAPACITY)
    private val playQueue = ArrayBlockingQueue<ShortArray>(PLAY_QUEUE_CAPACITY)
    private var audioSeq = 0L
    private var lastPlayedSeq = -1L

    init {
        scope.launch {
            p2pChatService.events.collect { event ->
                if (event is ChatServiceEvent.CallSignaling) handleSignaling(event.contactId, event.payload)
            }
        }
    }

    // --- Outgoing ---

    /** Starts ringing [contactId]. The UI must hold RECORD_AUDIO before calling - audio fails cleanly without it. */
    fun startCall(contactId: String) {
        if (_state.value != CallState.Idle) return
        val callId = UUID.randomUUID().toString()
        _state.value = CallState.Outgoing(contactId, callId)
        _muted.value = false
        _speaker.value = false
        scope.launch { sendPacket(contactId, PayloadKind.CALL_OFFER, callId) }
        ringJob?.cancel()
        ringJob = scope.launch {
            delay(CALL_RING_TIMEOUT_MS)
            if (_state.value is CallState.Outgoing) {
                scope.launch { sendPacket(contactId, PayloadKind.CALL_HANGUP, callId) }
                endCall(CallEndReason.NO_ANSWER)
            }
        }
    }

    // --- Incoming ---

    fun accept() {
        val incoming = _state.value as? CallState.Incoming ?: return
        ringJob?.cancel()
        stopVibration()
        scope.launch { sendPacket(incoming.contactId, PayloadKind.CALL_ANSWER, incoming.callId) }
        if (!startAudio(incoming.contactId, incoming.callId)) {
            scope.launch { sendPacket(incoming.contactId, PayloadKind.CALL_HANGUP, incoming.callId) }
            endCall(CallEndReason.FAILED)
            return
        }
        _state.value = CallState.Active(incoming.contactId, incoming.callId, System.currentTimeMillis())
    }

    fun reject() {
        val incoming = _state.value as? CallState.Incoming ?: return
        ringJob?.cancel()
        stopVibration()
        scope.launch { sendPacket(incoming.contactId, PayloadKind.CALL_REJECT, incoming.callId) }
        _state.value = CallState.Idle
    }

    // --- Either side ---

    fun hangup() {
        val current = _state.value
        val contactId = when (current) {
            is CallState.Outgoing -> current.contactId
            is CallState.Active -> current.contactId
            else -> null
        }
        val callId = when (current) {
            is CallState.Outgoing -> current.callId
            is CallState.Active -> current.callId
            else -> null
        }
        ringJob?.cancel()
        stopVibration()
        if (contactId != null && callId != null) {
            scope.launch { sendPacket(contactId, PayloadKind.CALL_HANGUP, callId) }
        }
        endCall(CallEndReason.HANGUP)
    }

    /** Clears a terminal [CallState.Ended] back to idle - the UI calls it when leaving the call screen. */
    fun clearEnded() {
        if (_state.value is CallState.Ended) _state.value = CallState.Idle
    }

    fun toggleMute() {
        _muted.value = !_muted.value
    }

    fun toggleSpeaker() {
        _speaker.value = !_speaker.value
        runCatching { audioManager.isSpeakerphoneOn = _speaker.value }
    }

    private fun handleSignaling(contactId: String, payload: ChatPayload) {
        val callId = payload.callId ?: return
        when (payload.kind) {
            PayloadKind.CALL_OFFER -> {
                if (_state.value != CallState.Idle) {
                    // Busy on another call - decline, don't queue.
                    scope.launch { sendPacket(contactId, PayloadKind.CALL_REJECT, callId) }
                    return
                }
                _muted.value = false
                _speaker.value = false
                _state.value = CallState.Incoming(contactId, callId)
                startVibration()
                ringJob?.cancel()
                ringJob = scope.launch {
                    delay(CALL_RING_TIMEOUT_MS)
                    if (_state.value is CallState.Incoming) {
                        stopVibration()
                        endCall(CallEndReason.MISSED)
                    }
                }
            }
            PayloadKind.CALL_ANSWER -> {
                val outgoing = _state.value as? CallState.Outgoing ?: return
                if (outgoing.callId != callId || outgoing.contactId != contactId) return
                ringJob?.cancel()
                if (!startAudio(contactId, callId)) {
                    scope.launch { sendPacket(contactId, PayloadKind.CALL_HANGUP, callId) }
                    endCall(CallEndReason.FAILED)
                    return
                }
                _state.value = CallState.Active(contactId, callId, System.currentTimeMillis())
            }
            PayloadKind.CALL_REJECT -> {
                val outgoing = _state.value as? CallState.Outgoing ?: return
                if (outgoing.callId != callId) return
                ringJob?.cancel()
                endCall(CallEndReason.DECLINED)
            }
            PayloadKind.CALL_HANGUP -> {
                val current = _state.value
                val matches = when (current) {
                    is CallState.Outgoing -> current.callId == callId
                    is CallState.Incoming -> current.callId == callId
                    is CallState.Active -> current.callId == callId
                    else -> false
                }
                if (!matches) return
                ringJob?.cancel()
                stopVibration()
                endCall(if (current is CallState.Incoming) CallEndReason.MISSED else CallEndReason.HANGUP)
            }
            PayloadKind.CALL_AUDIO -> {
                val active = _state.value as? CallState.Active ?: return
                if (active.callId != callId || active.contactId != contactId) return
                val seq = payload.audioSeq ?: return
                // Late or duplicate frames are worse than a gap - drop them.
                if (seq <= lastPlayedSeq) return
                val pcm = payload.audioBase64?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() } ?: return
                if (pcm.size != CALL_FRAME_SAMPLES * 2) return
                lastPlayedSeq = seq
                val shorts = ShortArray(CALL_FRAME_SAMPLES) { i ->
                    ((pcm[i * 2 + 1].toInt() shl 8) or (pcm[i * 2].toInt() and 0xFF)).toShort()
                }
                if (!playQueue.offer(shorts)) {
                    playQueue.poll()
                    playQueue.offer(shorts)
                }
            }
            else -> Unit
        }
    }

    private fun endCall(reason: CallEndReason) {
        stopAudio()
        val contactId = when (val s = _state.value) {
            is CallState.Outgoing -> s.contactId
            is CallState.Incoming -> s.contactId
            is CallState.Active -> s.contactId
            is CallState.Ended -> s.contactId
            CallState.Idle -> null
        }
        _micLevel.value = 0f
        _state.value = if (contactId != null) CallState.Ended(contactId, reason) else CallState.Idle
    }

    private suspend fun sendPacket(contactId: String, kind: PayloadKind, callId: String) {
        p2pChatService.sendCallPayload(
            contactId,
            ChatPayload(UUID.randomUUID().toString(), System.currentTimeMillis(), kind, callId = callId),
        )
    }

    // --- Audio ---

    /**
     * Starts capture, playback and the sender. False when the mic can't open
     * (no permission, in use) - the caller then hangs up cleanly instead of a
     * silent call nobody can diagnose. The UI holds the RECORD_AUDIO grant
     * before any call starts; the runCatching is the belt to those braces.
     */
    @Suppress("MissingPermission")
    private fun startAudio(contactId: String, callId: String): Boolean {
        stopAudio()
        sendQueue.clear()
        playQueue.clear()
        audioSeq = 0L
        lastPlayedSeq = -1L
        return runCatching {
            @Suppress("MissingPermission")
            val recorder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(CALL_SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(AudioRecord.getMinBufferSize(CALL_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(CALL_FRAME_SAMPLES * 2) * 4)
                .build()
            check(recorder.state == AudioRecord.STATE_INITIALIZED)
            val player = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(CALL_SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(AudioTrack.getMinBufferSize(CALL_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(CALL_FRAME_SAMPLES * 2) * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            check(player.state == AudioTrack.STATE_INITIALIZED)

            runCatching {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = _speaker.value
            }
            audioRunning = true
            recorder.startRecording()
            player.play()

            captureThread = Thread({
                val frame = ShortArray(CALL_FRAME_SAMPLES)
                var lastLevelAt = 0L
                while (audioRunning) {
                    val read = recorder.read(frame, 0, CALL_FRAME_SAMPLES)
                    if (read != CALL_FRAME_SAMPLES) continue
                    val now = System.currentTimeMillis()
                    if (now - lastLevelAt >= 100) {
                        lastLevelAt = now
                        var sum = 0.0
                        for (s in frame) sum += (s / 32768.0) * (s / 32768.0)
                        _micLevel.value = sqrt((sum / frame.size).coerceIn(0.0, 1.0)).toFloat()
                    }
                    if (_muted.value) continue
                    if (!sendQueue.offer(frame.copyOf())) {
                        sendQueue.poll()
                        sendQueue.offer(frame.copyOf())
                    }
                }
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
            }, "hertz-call-capture").also { it.start() }

            playbackThread = Thread({
                val silence = ShortArray(CALL_FRAME_SAMPLES)
                while (audioRunning) {
                    val frame = playQueue.poll(40, TimeUnit.MILLISECONDS) ?: silence
                    runCatching { player.write(frame, 0, CALL_FRAME_SAMPLES) }
                }
                runCatching { player.stop() }
                runCatching { player.release() }
            }, "hertz-call-playback").also { it.start() }

            // One ordered sender: capture never blocks on the network, and the
            // network never reorders - frames go out in capture order.
            senderJob = scope.launch {
                while (audioRunning) {
                    val frame = sendQueue.poll(40, TimeUnit.MILLISECONDS) ?: continue
                    val active = _state.value as? CallState.Active ?: continue
                    if (active.callId != callId) continue
                    val bytes = ByteArray(CALL_FRAME_SAMPLES * 2)
                    for (i in frame.indices) {
                        bytes[i * 2] = (frame[i].toInt() and 0xFF).toByte()
                        bytes[i * 2 + 1] = ((frame[i].toInt() shr 8) and 0xFF).toByte()
                    }
                    runCatching {
                        p2pChatService.sendCallPayload(
                            contactId,
                            ChatPayload(
                                UUID.randomUUID().toString(),
                                System.currentTimeMillis(),
                                PayloadKind.CALL_AUDIO,
                                callId = callId,
                                audioBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                                audioSeq = audioSeq++,
                            ),
                        )
                    }
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun stopAudio() {
        audioRunning = false
        senderJob?.cancel()
        senderJob = null
        runCatching { captureThread?.join(500) }
        runCatching { playbackThread?.join(500) }
        captureThread = null
        playbackThread = null
        runCatching {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        }
    }

    // --- Ring vibration ---

    private fun vibrator(): Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Vibrator::class.java)
        }
    }.getOrNull()

    private fun startVibration() {
        runCatching {
            vibrator()?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 400), 0))
        }
    }

    private fun stopVibration() {
        runCatching { vibrator()?.cancel() }
    }
}
