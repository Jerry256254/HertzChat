package cz.kuclab.hertzchat.p2p

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Glare rule for two phones ringing each other at once: the smaller call id
 * wins, so both sides agree on the surviving call with no extra round trip.
 */
class CallGlareTest {

    @Test
    fun `smaller incoming id wins`() {
        assertTrue(incomingOfferWins("b-call", "a-call"))
    }

    @Test
    fun `larger incoming id loses`() {
        assertFalse(incomingOfferWins("a-call", "b-call"))
    }

    @Test
    fun `rule is antisymmetric`() {
        val local = "call-1"
        val remote = "call-2"
        assertTrue(incomingOfferWins(local, remote) != incomingOfferWins(remote, local))
    }
}
