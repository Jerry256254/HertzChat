package cz.kuclab.hertzchat.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for collapsing long messages: 20+ lines (or a 1500+ character wall)
 * start collapsed behind an expander, anything shorter renders whole.
 */
class MessageCollapseTest {

    @Test
    fun `short messages never collapse`() {
        assertFalse(shouldCollapseMessage(""))
        assertFalse(shouldCollapseMessage("Ahoj"))
        assertFalse(shouldCollapseMessage((1..19).joinToString("\n") { "radek $it" }))
        assertFalse(shouldCollapseMessage("a".repeat(1499)))
    }

    @Test
    fun `twenty lines collapse`() {
        assertTrue(shouldCollapseMessage((1..20).joinToString("\n") { "radek $it" }))
        assertTrue(shouldCollapseMessage((1..50).joinToString("\n") { "radek $it" }))
    }

    @Test
    fun `long single block collapses by characters`() {
        assertTrue(shouldCollapseMessage("a".repeat(1500)))
    }

    @Test
    fun `preview is shorter than the original and marked`() {
        val long = (1..30).joinToString("\n") { "radek $it s nejakym textem" }
        val preview = collapsedPreview(long)
        assertTrue(preview.length < long.length)
        assertTrue(preview.endsWith("…"))
    }

    @Test
    fun `preview cuts a char wall at a word boundary`() {
        val wall = List(400) { "slovo" }.joinToString(" ")
        val preview = collapsedPreview(wall)
        assertTrue(preview.length < wall.length)
        assertTrue(preview.endsWith("…"))
        assertFalse(preview.dropLast(1).endsWith(" "))
    }
}
