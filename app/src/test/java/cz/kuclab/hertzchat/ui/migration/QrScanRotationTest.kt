package cz.kuclab.hertzchat.ui.migration

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the camera-frame preprocessing [QrCodeScannerAnalyzer] depends on: Y-plane
 * rotation for all four sensor orientations, plus a full encode -> Y-plane ->
 * decode round trip through the same reader configuration the scanner uses.
 * The rotation cases are the regression net for "scan does nothing on a
 * portrait-held phone" - frames arrive rotated and must be stood upright first.
 */
class QrScanRotationTest {

    private fun pattern(width: Int, height: Int, stride: Int): ByteArray {
        val data = ByteArray(stride * height) { -1 }
        var value = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                data[y * stride + x] = (value++ % 251).toByte()
            }
        }
        return data
    }

    @Test
    fun `rotation 0 unpacks padded rows`() {
        val upright = rotateYuvUpright(pattern(3, 2, 4), 3, 2, 4, 0)!!
        assertEquals(3, upright.width)
        assertEquals(2, upright.height)
        assertArrayEquals(byteArrayOf(0, 1, 2, 3, 4, 5), upright.bytes)
    }

    @Test
    fun `rotation 180 flips both axes`() {
        // 3x2: [0 1 2] -> [5 4 3]
        //      [3 4 5]    [2 1 0]
        val upright = rotateYuvUpright(pattern(3, 2, 3), 3, 2, 3, 180)!!
        assertEquals(3, upright.width)
        assertEquals(2, upright.height)
        assertArrayEquals(byteArrayOf(5, 4, 3, 2, 1, 0), upright.bytes)
    }

    @Test
    fun `rotation 90 swaps dimensions clockwise`() {
        // 3x2: [0 1 2]  -> 2x3: [3 0]
        //      [3 4 5]          [4 1]
        //                       [5 2]
        val upright = rotateYuvUpright(pattern(3, 2, 3), 3, 2, 3, 90)!!
        assertEquals(2, upright.width)
        assertEquals(3, upright.height)
        assertArrayEquals(byteArrayOf(3, 0, 4, 1, 5, 2), upright.bytes)
    }

    @Test
    fun `rotation 270 swaps dimensions counter-clockwise`() {
        // 3x2: [0 1 2]  -> 2x3: [2 5]
        //      [3 4 5]          [1 4]
        //                       [0 3]
        val upright = rotateYuvUpright(pattern(3, 2, 3), 3, 2, 3, 270)!!
        assertEquals(2, upright.width)
        assertEquals(3, upright.height)
        assertArrayEquals(byteArrayOf(2, 5, 1, 4, 0, 3), upright.bytes)
    }

    @Test
    fun `short buffer and bogus rotation return null`() {
        assertNull(rotateYuvUpright(ByteArray(4), 3, 2, 3, 0))
        assertNull(rotateYuvUpright(pattern(3, 2, 3), 3, 2, 3, 45))
    }

    @Test
    fun `dense qr payload survives encode to y-plane decode`() {
        // Same shape as a real HertzId QR: ~200 JSON chars (the relay key is far
        // shorter than the old network address), plus padding to keep the dense
        // many-small-modules stress on the decoder.
        val payload = """{"contactId":"${"a".repeat(22)}","nickname":"Testβ","identityKeyBase64":"${"b".repeat(44)}","nostrPubkeyHex":"${"c".repeat(64)}","pad":"${"d".repeat(440)}"}"""
        val size = 400
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, mapOf(com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8"))
        val yuv = ByteArray(size * size) { i ->
            val x = i % size
            val y = i / size
            if (matrix[x, y]) 0 else -1
        }
        val reader = MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
        }
        val source = PlanarYUVLuminanceSource(yuv, size, size, 0, 0, size, size, false)
        val text = reader.decode(BinaryBitmap(HybridBinarizer(source))).text
        assertEquals(payload, text)
    }

    @Test
    fun `rotated dense qr decodes after upright rotation`() {
        val payload = "hertz-rotation-probe-270"
        val size = 200
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, mapOf(com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8"))
        // Simulate a 90-degree-rotated sensor frame of the upright code.
        val upright = ByteArray(size * size) { i -> if (matrix[i % size, i / size]) 0 else -1 }
        val rotated = ByteArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                rotated[x * size + (size - 1 - y)] = upright[y * size + x]
            }
        }
        // Standing it back upright must restore the exact original bytes...
        val fixed = rotateYuvUpright(rotated, size, size, size, 270)!!
        assertArrayEquals(upright, fixed.bytes)
        // ...and the restored plane must decode.
        val reader = MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
        }
        val source = PlanarYUVLuminanceSource(fixed.bytes, size, size, 0, 0, size, size, false)
        assertEquals(payload, reader.decode(BinaryBitmap(HybridBinarizer(source))).text)
    }
}
