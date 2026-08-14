package cz.kuclab.hertzchat.ui.migration

import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Decodes QR codes straight from the CameraX Y-plane luminance data - no
 * bitmap conversion needed. Fires [onDecoded] at most once per instance
 * (camera analysis runs on a background executor at several frames per
 * second, and every frame containing the same code would otherwise
 * re-trigger it) and always on the main thread, since callers update
 * Compose state / navigate from it.
 */
class QrCodeScannerAnalyzer(private val onDecoded: (String) -> Unit) : ImageAnalysis.Analyzer {
    /**
     * Restricted to QR and asked to try hard, both deliberately.
     *
     * Left unrestricted, the reader also runs every 1D barcode decoder over each frame,
     * and the dense stripes inside a large QR can satisfy one of their checksums by
     * accident - it then returns that "barcode" as a line of nonsense instead of failing,
     * which is what reached the parser as a scanned code that was never JSON at all.
     * TRY_HARDER matters because a Hertz ID is a big payload (a ~670-character JSON), so
     * the code carries many small modules and needs the more thorough scan.
     */
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }
    private val delivered = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun analyze(image: ImageProxy) {
        if (delivered.get()) {
            image.close()
            return
        }
        val plane = image.planes.firstOrNull()
        if (plane == null) {
            image.close()
            return
        }
        val data = ByteArray(plane.buffer.remaining())
        plane.buffer.get(data)

        // Camera frames are usually padded: a row occupies rowStride bytes, of which only
        // the first `width` are pixels. Treating the buffer as if rows were exactly `width`
        // apart shears the image progressively down the frame, so the finder patterns no
        // longer line up and nothing ever decodes on the devices that pad. rowStride is the
        // buffer's real row width; the crop rectangle is what limits it to actual pixels.
        val rowStride = plane.rowStride.takeIf { it >= image.width } ?: image.width
        val rows = minOf(image.height, data.size / rowStride)
        if (rows <= 0) {
            image.close()
            return
        }
        val source = PlanarYUVLuminanceSource(
            data,
            rowStride,
            rows,
            0,
            0,
            image.width,
            rows,
            false,
        )
        try {
            val result = reader.decode(BinaryBitmap(HybridBinarizer(source)))
            if (delivered.compareAndSet(false, true)) {
                mainHandler.post { onDecoded(result.text) }
            }
        } catch (_: ReaderException) {
            // No readable code in this frame: NotFoundException most of the time (there
            // simply isn't one), but a half-covered or motion-blurred code raises Checksum/
            // FormatException just as routinely - and those escaping the analyzer would tear
            // down the camera's analysis pipeline mid-scan.
        } finally {
            image.close()
        }
    }
}
