package cz.kuclab.hertzchat.media

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** All media lives only in this app's private storage - never in shared/public directories, and never anywhere off-device. */
@Singleton
class MediaStorage @Inject constructor(@ApplicationContext private val context: Context) {

    private val root: File by lazy { File(context.filesDir, "media").apply { mkdirs() } }

    fun newOutgoingCopy(sourceBytes: ByteArray, extension: String): File {
        val file = File(root, "out_${System.currentTimeMillis()}_${(0..9999).random()}.$extension")
        file.writeBytes(sourceBytes)
        return file
    }

    fun fileFor(transferId: String, extension: String): File = File(root, "$transferId.$extension")

    private val avatarsRoot: File by lazy { File(context.filesDir, "avatars").apply { mkdirs() } }

    fun selfAvatarFile(): File = File(avatarsRoot, "self.jpg")

    fun contactAvatarFile(contactId: String): File = File(avatarsRoot, "$contactId.jpg")

    /** Total bytes used by received/sent media (not counting avatars, which are tiny). */
    fun mediaStorageBytes(): Long = root.listFiles()?.sumOf { it.length() } ?: 0L

    /** Deletes locally cached media files - the messages that referenced them remain, just without a viewable attachment anymore. */
    fun clearMedia() {
        root.listFiles()?.forEach { it.delete() }
    }

    fun extensionFor(mimeType: String): String = when {
        mimeType.contains("jpeg") -> "jpg"
        mimeType.contains("png") -> "png"
        mimeType.contains("webp") -> "webp"
        mimeType.contains("mp4") -> "mp4"
        mimeType.contains("3gpp") -> "3gp"
        mimeType.contains("ogg") -> "ogg"
        mimeType.contains("m4a") || mimeType.contains("mp4a") -> "m4a"
        else -> "bin"
    }
}
