package cz.kuclab.hertzchat.update

import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class UpdateInfo(val latestVersion: String, val releaseUrl: String)

@Serializable
private data class GithubRelease(val tag_name: String, val html_url: String)

private const val LATEST_RELEASE_API_URL = "https://api.github.com/repos/Jerry256254/HertzChat/releases/latest"

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
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    error("Server odpověděl kódem ${connection.responseCode}")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val release = json.decodeFromString(GithubRelease.serializer(), body)
                UpdateInfo(latestVersion = release.tag_name.removePrefix("v"), releaseUrl = release.html_url)
            } finally {
                connection.disconnect()
            }
        }
    }
}
