package cz.kuclab.hertzchat.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dotted-version compare behind both update prompts (settings and cold start):
 * newer remote triggers the offer, equal or older stays quiet.
 */
class VersionCompareTest {

    @Test
    fun `newer remote wins`() {
        assertTrue(isNewerVersion("0.39.0", "0.38.0"))
        assertTrue(isNewerVersion("0.38.1", "0.38.0"))
        assertTrue(isNewerVersion("1.0.0", "0.99.9"))
    }

    @Test
    fun `equal or older stays quiet`() {
        assertFalse(isNewerVersion("0.38.0", "0.38.0"))
        assertFalse(isNewerVersion("0.37.9", "0.38.0"))
        assertFalse(isNewerVersion("garbage", "0.38.0"))
    }
}
