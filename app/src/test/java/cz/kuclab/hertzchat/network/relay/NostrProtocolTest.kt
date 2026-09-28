package cz.kuclab.hertzchat.network.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Nostr wire codec must build events any relay accepts and reject anything forged or corrupt. */
class NostrProtocolTest {

    @Test
    fun `built event verifies and survives a publish round-trip`() {
        val secret = NostrCrypto.generateSecretKey()
        val event = NostrCrypto.let {
            NostrProtocol.buildSignedEvent(
                senderSecret = secret,
                kind = NostrProtocol.HERTZ_KIND,
                tags = listOf(listOf("p", "ab".repeat(32)), listOf("x", "0123456789abcdef0123456789abcdef")),
                content = "aGVsbG8=",
                createdAtSec = 1_728_000_000L,
            )
        }
        assertTrue(NostrProtocol.verifyEvent(event))

        // What the relay echoes back inside ["EVENT", subId, {...}] must parse and still verify.
        val echoed = "[\"EVENT\",\"hertz-v1\"," + NostrProtocol.eventToJson(event) + "]"
        val parsed = NostrProtocol.parseServerMessage(echoed)
        assertTrue(parsed is NostrProtocol.ServerMessage.Event)
        val roundTripped = (parsed as NostrProtocol.ServerMessage.Event).event
        assertEquals(event.idHex, roundTripped.idHex)
        assertEquals("ab".repeat(32), roundTripped.tagValue("p"))
        assertEquals("0123456789abcdef0123456789abcdef", roundTripped.tagValue("x"))
        assertTrue(NostrProtocol.verifyEvent(roundTripped))
    }

    @Test
    fun `tampered event fails verification`() {
        val event = NostrProtocol.buildSignedEvent(
            senderSecret = NostrCrypto.generateSecretKey(),
            kind = NostrProtocol.HERTZ_KIND,
            tags = listOf(listOf("p", "ab".repeat(32))),
            content = "aGVsbG8=",
        )
        assertTrue(NostrProtocol.verifyEvent(event))
        assertFalse(NostrProtocol.verifyEvent(event.copy(content = "aGVsbG90")))
        assertFalse(NostrProtocol.verifyEvent(event.copy(tags = listOf(listOf("p", "cd".repeat(32))))))
        assertFalse(NostrProtocol.verifyEvent(event.copy(kind = 1)))
    }

    @Test
    fun `server messages parse`() {
        val eose = NostrProtocol.parseServerMessage("[\"EOSE\",\"hertz-v1\"]")
        assertTrue(eose is NostrProtocol.ServerMessage.EndOfStored)

        val notice = NostrProtocol.parseServerMessage("[\"NOTICE\",\"slow down\"]")
        assertTrue(notice is NostrProtocol.ServerMessage.Notice)

        val closed = NostrProtocol.parseServerMessage("[\"CLOSED\",\"hertz-v1\",\"auth-required\"]")
        assertTrue(closed is NostrProtocol.ServerMessage.Closed)

        val ok = NostrProtocol.parseServerMessage("[\"OK\",\"deadbeef\",true,\"\"]")
        assertTrue(ok is NostrProtocol.ServerMessage.Ok && (ok as NostrProtocol.ServerMessage.Ok).accepted)

        // Garbage never throws, it just reads as unknown.
        assertTrue(NostrProtocol.parseServerMessage("not json") is NostrProtocol.ServerMessage.Unknown)
        assertTrue(NostrProtocol.parseServerMessage("[\"EVENT\"]") is NostrProtocol.ServerMessage.Unknown)
        assertTrue(NostrProtocol.parseServerMessage("[\"FROBNICATE\",1]") is NostrProtocol.ServerMessage.Unknown)
    }

    @Test
    fun `subscribe message has the right shape`() {
        val sub = NostrProtocol.clientSubscribeJson("hertz-v1", listOf(NostrProtocol.HERTZ_KIND), listOf("ab".repeat(32)))
        assertTrue(sub.startsWith("[\"REQ\",\"hertz-v1\","))
        assertTrue(sub.contains("\"kinds\":[${NostrProtocol.HERTZ_KIND}]"))
        assertTrue(sub.contains("\"#p\":[\"${"ab".repeat(32)}\"]"))
    }
}
