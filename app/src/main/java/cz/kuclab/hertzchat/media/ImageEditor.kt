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

    /**
     * The starting crop rectangle for an aspect choice: the largest centered rect of
     * that ratio, or a centered 90% rect for free-form ([aspect] <= 0). Bitmap pixels.
     */
    fun defaultCropRect(width: Int, height: Int, aspect: Float): android.graphics.Rect {
        if (aspect <= 0f) {
            val w = (width * 0.9f).toInt()
            val h = (height * 0.9f).toInt()
            return android.graphics.Rect((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
        }
        val currentAspect = width.toFloat() / height.toFloat()
        val (cropWidth, cropHeight) = if (currentAspect > aspect) {
            (height * aspect).toInt() to height
        } else {
            width to (width / aspect).toInt()
        }
        val x = (width - cropWidth) / 2
        val y = (height - cropHeight) / 2
        return android.graphics.Rect(x.coerceAtLeast(0), y.coerceAtLeast(0), (x + cropWidth).coerceAtMost(width), (y + cropHeight).coerceAtMost(height))
    }

    /** Crops to an interactive rectangle (bitmap pixels), clamped defensively into the bitmap. */
    fun cropToRect(bitmap: Bitmap, rect: android.graphics.Rect): Bitmap {
        val left = rect.left.coerceIn(0, bitmap.width - 1)
        val top = rect.top.coerceIn(0, bitmap.height - 1)
        val right = rect.right.coerceIn(left + 1, bitmap.width)
        val bottom = rect.bottom.coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    /** Quality 95 keeps this visually indistinguishable from the source while still compressing meaningfully - matches the "best quality" sharing requirement without needlessly bloating the transfer. */
    fun toJpegBytes(bitmap: Bitmap, quality: Int = 95): ByteArray =
        ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            stream.toByteArray()
        }
}
