package cz.kuclab.hertzchat.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-app updates: downloads the release APK straight into the app's cache and
 * hands it to the system installer - no browser, no GitHub visit. Android
 * still shows its own install confirmation screen (sideloaded updates can't
 * skip the user's tap), and enabling "install unknown apps" for Hertz Chat is
 * a one-time system toggle the flow below walks the user to.
 */
object UpdateInstaller {

    /** False until the user flips the one-time "install unknown apps" toggle for us (Android 8+). */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Opens our own page in the system's "install unknown apps" settings. */
    fun openUnknownSourcesSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Streams [url] into [dest]. [onProgress] gets 0..1 while the size is
     * known, negative while it isn't (indeterminate UI then). Deletes the
     * partial file on failure so a retry never resumes a corrupt half-APK.
     */
    suspend fun downloadApk(url: String, dest: File, onProgress: (Float) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            dest.parentFile?.mkdirs()
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "HertzChat-Android")
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    error("Server odpověděl kódem ${connection.responseCode}")
                }
                val total = connection.contentLengthLong
                var received = 0L
                var lastEmitted = 0f
                connection.inputStream.use { input ->
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            received += read
                            if (total > 0) {
                                val progress = (received.toFloat() / total).coerceIn(0f, 1f)
                                // A 233MB download emits thousands of chunks - only
                                // push progress uphill in visible steps.
                                if (progress - lastEmitted >= 0.005f || progress >= 1f) {
                                    lastEmitted = progress
                                    onProgress(progress)
                                }
                            } else {
                                onProgress(-1f)
                            }
                        }
                    }
                }
                if (total > 0 && dest.length() != total) error("Stažení se přerušilo, zkus to znovu")
                dest
            } catch (e: Exception) {
                runCatching { dest.delete() }
                throw e
            } finally {
                connection.disconnect()
            }
        }
    }

    /** Hands [apk] to the system installer via a temporary read grant (FileProvider cache path). */
    fun installApk(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
