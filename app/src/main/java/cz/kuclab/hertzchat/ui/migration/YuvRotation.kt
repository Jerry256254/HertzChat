package cz.kuclab.hertzchat.ui.migration

/** One Y-plane remapped to display-upright, tightly packed (stride == width). Null when the buffer can't hold the claimed frame. */
internal data class UprightYuv(val bytes: ByteArray, val width: Int, val height: Int)

/**
 * Pure rotation for camera Y-plane bytes - kept free of CameraX/Android types so the
 * [QrScanRotationTest] can pin all four orientations on the JVM. Rotation is
 * clockwise, matching [androidx.camera.core.ImageInfo.getRotationDegrees].
 */
internal fun rotateYuvUpright(data: ByteArray, width: Int, height: Int, rowStride: Int, rotationDegrees: Int): UprightYuv? {
    if (width <= 0 || height <= 0 || rowStride < width) return null
    if (data.size < rowStride * (height - 1) + width) return null
    val rotation = ((rotationDegrees % 360) + 360) % 360
    if (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270) return null
    if (rotation == 0) {
        return if (rowStride == width) {
            UprightYuv(data.copyOf(width * height), width, height)
        } else {
            val out = ByteArray(width * height)
            for (y in 0 until height) data.copyInto(out, y * width, y * rowStride, y * rowStride + width)
            UprightYuv(out, width, height)
        }
    }
    if (rotation == 180) {
        val out = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                out[(height - 1 - y) * width + (width - 1 - x)] = data[y * rowStride + x]
            }
        }
        return UprightYuv(out, width, height)
    }
    val out = ByteArray(width * height)
    val outWidth = height
    for (y in 0 until height) {
        for (x in 0 until width) {
            val dest = if (rotation == 90) {
                // Source (x, y) -> dest (h-1-y, x): dest row x, col h-1-y.
                x * outWidth + (height - 1 - y)
            } else {
                // 270 CW: source (x, y) -> dest (y, w-1-x).
                (width - 1 - x) * outWidth + y
            }
            out[dest] = data[y * rowStride + x]
        }
    }
    return UprightYuv(out, outWidth, width)
}
