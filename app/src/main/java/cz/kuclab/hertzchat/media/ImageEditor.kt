package cz.kuclab.hertzchat.media

import android.graphics.Bitmap
import android.graphics.Matrix
import java.io.ByteArrayOutputStream

object ImageEditor {
    /** null = keep the original aspect ratio (no crop, only rotation is applied). */
    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun cropToAspect(bitmap: Bitmap, aspect: Float?): Bitmap {
        if (aspect == null) return bitmap
        val currentAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        val (cropWidth, cropHeight) = if (currentAspect > aspect) {
            (bitmap.height * aspect).toInt() to bitmap.height
        } else {
            bitmap.width to (bitmap.width / aspect).toInt()
        }
        val x = (bitmap.width - cropWidth) / 2
        val y = (bitmap.height - cropHeight) / 2
        return Bitmap.createBitmap(bitmap, x.coerceAtLeast(0), y.coerceAtLeast(0), cropWidth.coerceAtMost(bitmap.width), cropHeight.coerceAtMost(bitmap.height))
    }

    /** Quality 95 keeps this visually indistinguishable from the source while still compressing meaningfully - matches the "best quality" sharing requirement without needlessly bloating the transfer. */
    fun toJpegBytes(bitmap: Bitmap, quality: Int = 95): ByteArray =
        ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            stream.toByteArray()
        }
}
