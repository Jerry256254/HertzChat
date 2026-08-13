package cz.kuclab.hertzchat.ui.migration

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/** Decodes QR codes straight from the CameraX Y-plane luminance data - no bitmap conversion needed. */
class QrCodeScannerAnalyzer(private val onDecoded: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader()

    override fun analyze(image: ImageProxy) {
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
            onDecoded(result.text)
        } catch (_: NotFoundException) {
            // no QR code in this frame - expected most of the time
        } finally {
            image.close()
        }
    }
}
