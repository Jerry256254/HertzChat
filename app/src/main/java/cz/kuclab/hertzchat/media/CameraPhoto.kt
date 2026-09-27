package cz.kuclab.hertzchat.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * A fresh FileProvider URI in private cache for the *system* camera (TakePicture)
 * to write into. This app needs no CAMERA permission of its own - the camera app
 * holds that, and the finished photo comes back through the existing editor flow.
 */
fun newCameraPhotoUri(context: Context): Uri {
    val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
