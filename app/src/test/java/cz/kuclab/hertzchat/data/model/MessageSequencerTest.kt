package cz.kuclab.hertzchat.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The thread ordering stands on this sequence - pin its guarantees down. */
class MessageSequencerTest {

    @Before
    fun reset() {
        MessageSequencer.resetForTest()
    }

    @Test
    fun `same millisecond orders by call order`() {
        val first = MessageSequencer.next(1_700_000_000_000L)
        val second = MessageSequencer.next(1_700_000_000_000L)
        val third = MessageSequencer.next(1_700_000_000_000L)
        assertTrue(first < second)
        assertTrue(second < third)
    }

    @Test
    fun `later wall-clock always wins over the counter`() {
        // Burn a big counter at an old timestamp...
        repeat(100_000) { MessageSequencer.next(1_700_000_000_000L) }
        // ...a message 2ms later still sorts after, not before.
        val old = MessageSequencer.next(1_700_000_000_000L)
        val newer = MessageSequencer.next(1_700_000_000_002L)
        assertTrue(newer > old)
    }

    @Test
    fun `values are unique across a burst`() {
        val seen = mutableSetOf<Long>()
        repeat(10_000) { seen += MessageSequencer.next(1_700_000_000_000L) }
        assertEquals(10_000, seen.size)
    }

    @Test
    fun `high bits carry the timestamp`() {
        val seq = MessageSequencer.next(1_700_000_000_123L)
        assertEquals(1_700_000_000_123L, seq shr 20)
    }
}
