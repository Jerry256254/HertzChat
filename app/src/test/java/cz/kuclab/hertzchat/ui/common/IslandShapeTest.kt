package cz.kuclab.hertzchat.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the composer island shape: short drafts stay a pill, longer
 * text switches to a fixed-radius sheet so a paragraph never stretches the
 * pill into a broken sausage.
 */
class IslandShapeTest {

    @Test
    fun `short single line stays a pill`() {
        assertFalse(isSheetIsland(""))
        assertFalse(isSheetIsland("Ahoj"))
        assertFalse(isSheetIsland("a".repeat(60)))
    }

    @Test
    fun `line break or long text switches to sheet`() {
        assertTrue(isSheetIsland("prvni\nradek"))
        assertTrue(isSheetIsland("a".repeat(61)))
    }
}
