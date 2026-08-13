package cz.kuclab.hertzchat.ui.migration

import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
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
    private val reader = MultiFormatReader()
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

        val source = PlanarYUVLuminanceSource(
            data,
            image.width,
            image.height,
            0,
            0,
            image.width,
            image.height,
            false,
        )
        try {
            val result = reader.decode(BinaryBitmap(HybridBinarizer(source)))
            if (delivered.compareAndSet(false, true)) {
                mainHandler.post { onDecoded(result.text) }
            }
        } catch (_: NotFoundException) {
            // no QR code in this frame - expected most of the time
        } finally {
            image.close()
        }
    }
}
