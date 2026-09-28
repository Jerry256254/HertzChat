package cz.kuclab.hertzchat.data.model

import java.util.concurrent.atomic.AtomicLong

/**
 * The sender-side per-thread message sequence, extracted pure (no Android
 * dependencies) so the JVM unit tests pin down the exact ordering the thread
 * list relies on.
 *
 * A sequence value packs the send time in the high bits and a counter in the
 * low 20: two messages in the same millisecond still order correctly, and a
 * restart can't collide with (or reorder against) older rows because
 * wall-clock time only moves forward - no persistence needed.
 */
object MessageSequencer {

    private val counter = AtomicLong(0)

    fun next(sentAtMs: Long): Long =
        (sentAtMs shl 20) or (counter.incrementAndGet() and 0xFFFFF)

    /** Tests only - the app never resets, the counter is the whole point. */
    internal fun resetForTest() {
        counter.set(0)
    }
}
