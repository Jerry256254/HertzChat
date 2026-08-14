package cz.kuclab.hertzchat.ui.migration

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * [EncodeHintType.CHARACTER_SET] is not optional here. Left to itself the encoder
 * writes byte mode as ISO-8859-1, which cannot represent most Czech letters
 * (š, č, ř, ž, ě, ů) or anything outside Latin-1 - every one of them comes back
 * from a scan as "?", silently corrupting whatever nickname the payload carried.
 * Asking for UTF-8 makes the encoder emit an ECI marker that tells the scanner
 * which charset to decode with, and the text survives the round trip intact.
 */
fun generateQrBitmap(content: String, sizePx: Int = 800): Bitmap {
    val hints = mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}
