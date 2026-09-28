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

    /**
     * Same as [newOutgoingCopy] but streams file-to-file - a video or APK staged
     * for sending must never be loaded into RAM just to make the local copy.
     * Must be called off the main thread.
     */
    fun newOutgoingCopyFromFile(src: File, extension: String): File {
        val file = File(root, "out_${System.currentTimeMillis()}_${(0..9999).random()}.$extension")
        src.inputStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        return file
    }

    fun fileFor(transferId: String, extension: String): File = File(root, "$transferId.$extension")

    private val avatarsRoot: File by lazy { File(context.filesDir, "avatars").apply { mkdirs() } }

    fun selfAvatarFile(): File = File(avatarsRoot, "self.jpg")

    fun contactAvatarFile(contactId: String): File = File(avatarsRoot, "$contactId.jpg")

    /**
     * A fresh path for every incoming avatar update: the path changing is what pushes
     * the new photo through every observing UI and past Coil's path-keyed caches.
     * The caller deletes the previous file once the contact row points at the new one.
     */
    fun newContactAvatarFile(contactId: String): File =
        File(avatarsRoot, "$contactId-${System.currentTimeMillis()}.jpg")

    /** Total bytes used by received/sent media (not counting avatars, which are tiny). */
    fun mediaStorageBytes(): Long = root.listFiles()?.sumOf { it.length() } ?: 0L

    /**
     * Copies a private attachment into shared storage (Gallery/Downloads) so the user can
     * keep it outside the app. Returns the display location on success. MediaStore on
     * API 29+ needs no permission; on older devices it falls back to the app-specific
     * external Downloads dir, which is visible over USB but needs no permission either.
     */
    fun saveToPublic(src: File, mimeType: String?, displayName: String): Result<String> = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            saveViaMediaStore(src, mimeType, displayName)
        } else {
            val dir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "HertzChat").apply { mkdirs() }
            val dest = File(dir, displayName)
            src.copyTo(dest, overwrite = true)
            dest.absolutePath
        }
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
    private fun saveViaMediaStore(src: File, mimeType: String?, displayName: String): String {
        val resolver = context.contentResolver
        val volume = android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
        val collection: android.net.Uri
        val relativePath: String
        if (mimeType != null && mimeType.startsWith("image/")) {
            collection = android.provider.MediaStore.Images.Media.getContentUri(volume)
            relativePath = "Pictures/HertzChat"
        } else if (mimeType != null && mimeType.startsWith("video/")) {
            collection = android.provider.MediaStore.Video.Media.getContentUri(volume)
            relativePath = "Movies/HertzChat"
        } else if (mimeType != null && mimeType.startsWith("audio/")) {
            collection = android.provider.MediaStore.Audio.Media.getContentUri(volume)
            relativePath = "Music/HertzChat"
        } else {
            collection = android.provider.MediaStore.Downloads.getContentUri(volume)
            relativePath = "Download/HertzChat"
        }
        val values = android.content.ContentValues()
        values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType ?: "application/octet-stream")
        values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        val uri = resolver.insert(collection, values) ?: error("MediaStore insert failed")
        try {
            val out = resolver.openOutputStream(uri) ?: error("MediaStore write failed")
            out.use { output -> src.inputStream().use { input -> input.copyTo(output) } }
            val done = android.content.ContentValues()
            done.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return relativePath
    }

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
