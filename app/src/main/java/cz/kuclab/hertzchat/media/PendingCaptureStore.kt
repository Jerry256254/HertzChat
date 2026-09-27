package cz.kuclab.hertzchat.media

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A photo taken by the in-app camera, waiting for the chat screen to pick it up into the editor. */
data class PendingCapture(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean = other is PendingCapture && bytes.contentEquals(other.bytes)
    override fun hashCode(): Int = bytes.contentHashCode()
}

/**
 * One-shot handoff from [cz.kuclab.hertzchat.ui.camera.CameraScreen] back to whichever
 * chat opened it: the camera publishes the JPEG bytes and pops itself, the chat screen
 * consumes them into the photo editor. Only one chat is ever composed at a time, so a
 * single slot (rather than a queue) is enough - a newer capture simply replaces an
 * unconsumed older one.
 */
@Singleton
class PendingCaptureStore @Inject constructor() {
    private val _pendingCapture = MutableStateFlow<PendingCapture?>(null)
    val pendingCapture: StateFlow<PendingCapture?> = _pendingCapture

    fun publish(bytes: ByteArray) {
        _pendingCapture.value = PendingCapture(bytes)
    }

    fun consume() {
        _pendingCapture.value = null
    }
}
