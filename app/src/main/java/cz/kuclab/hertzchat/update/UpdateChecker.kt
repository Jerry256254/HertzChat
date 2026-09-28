package cz.kuclab.hertzchat.update

import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class UpdateInfo(val latestVersion: String, val releaseUrl: String, val apkUrl: String?)

@Serializable
private data class GithubAsset(val name: String, val browser_download_url: String)

@Serializable
private data class GithubRelease(val tag_name: String, val html_url: String, val assets: List<GithubAsset> = emptyList())

private const val LATEST_RELEASE_API_URL = "https://api.github.com/repos/Jerry256254/HertzChat/releases/latest"

/** Dotted-version compare shared by the settings check and the cold-start check. */
internal fun isNewerVersion(remote: String, local: String): Boolean {
    val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
    val localParts = local.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
        val r = remoteParts.getOrElse(i) { 0 }
        val l = localParts.getOrElse(i) { 0 }
        if (r != l) return r > l
    }
    return false
}

/**
 * Checks GitHub Releases for the newest published version. There's no update
 * server of our own (there's no server of any kind in this app) - GitHub's
 * public API is just read, same as a browser would, no account/token needed.
 */
@Singleton
class UpdateChecker @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkLatestVersion(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(LATEST_RELEASE_API_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.useCaches = false
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    error("Server odpověděl kódem ${connection.responseCode}")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val release = json.decodeFromString(GithubRelease.serializer(), body)
                UpdateInfo(
                    latestVersion = release.tag_name.removePrefix("v"),
                    releaseUrl = release.html_url,
                    apkUrl = release.assets.firstOrNull { it.name.endsWith(".apk") }?.browser_download_url,
                )
            } finally {
                connection.disconnect()
            }
        }
    }
}
