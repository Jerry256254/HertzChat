package cz.kuclab.hertzchat.p2p

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the ntfy wake-up path: the poll parser must pick the newest
 * message id out of a server body without ever trusting it (malformed lines
 * and non-message events are skipped), and generated topics must be
 * unguessable random ids in ntfy's alphabet. A wrong id here either wakes
 * the phone for nothing or - worse - sleeps through a real ping.
 */
class NtfyPollTest {

    @Test
    fun `empty or blank body has no id`() {
        assertNull(NtfyPing.latestIdFromPollBody(""))
        assertNull(NtfyPing.latestIdFromPollBody("\n  \n"))
    }

    @Test
    fun `single message yields its id`() {
        val body = """{"id":"m1","time":1700000000,"event":"message","topic":"abc","message":""}"""
        assertEquals("m1", NtfyPing.latestIdFromPollBody(body))
    }

    @Test
    fun `newest of several messages wins`() {
        val body = listOf(
            """{"id":"m1","event":"message","message":""}""",
            """{"id":"m2","event":"message","message":""}""",
            """{"id":"m3","event":"message","message":""}""",
        ).joinToString("\n")
        assertEquals("m3", NtfyPing.latestIdFromPollBody(body))
    }

    @Test
    fun `non-message events and garbage lines are skipped`() {
        val body = listOf(
            """{"id":"open1","event":"open","topic":"abc"}""",
            """{"id":"m1","event":"message","message":""}""",
            """not json at all""",
            """{"id":"k1","event":"keepalive"}""",
            """{"id":"","event":"message"}""",
            """{"event":"message"}""",
        ).joinToString("\n")
        assertEquals("m1", NtfyPing.latestIdFromPollBody(body))
    }

    @Test
    fun `generated topics are 128-bit random ids`() {
        val a = NtfyPing.newTopic()
        val b = NtfyPing.newTopic()
        assertEquals(32, a.length)
        assertTrue(NtfyPing.isValidTopic(a))
        assertNotEquals(a, b)
    }

    @Test
    fun `topic validation rejects injection`() {
        assertTrue(!NtfyPing.isValidTopic(""))
        assertTrue(!NtfyPing.isValidTopic("../etc"))
        assertTrue(!NtfyPing.isValidTopic("a/b"))
        assertTrue(!NtfyPing.isValidTopic("a?poll=1"))
        assertTrue(!NtfyPing.isValidTopic("x".repeat(65)))
    }
}
