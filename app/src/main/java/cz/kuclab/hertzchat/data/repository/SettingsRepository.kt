package cz.kuclab.hertzchat.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "hertzchat_settings")

private val KEY_RELAY_URL = stringPreferencesKey("relay_url")
private val KEY_TURN_URL = stringPreferencesKey("turn_url")
private val KEY_TURN_USERNAME = stringPreferencesKey("turn_username")
private val KEY_TURN_PASSWORD = stringPreferencesKey("turn_password")
private val KEY_DISCOVERABLE = booleanPreferencesKey("discoverable")
private val KEY_AUTO_DOWNLOAD_MEDIA = booleanPreferencesKey("auto_download_media")
private val KEY_MEDIA_QUALITY = stringPreferencesKey("media_quality") // ORIGINAL | HIGH | BALANCED

const val DEFAULT_RELAY_URL = "wss://relay.hertzchat.example/ws"

data class AppSettings(
    val relayUrl: String = DEFAULT_RELAY_URL,
    val turnUrl: String = "",
    val turnUsername: String = "",
    val turnPassword: String = "",
    val discoverable: Boolean = true,
    val autoDownloadMedia: Boolean = true,
    val mediaQuality: String = "ORIGINAL",
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            relayUrl = prefs[KEY_RELAY_URL] ?: DEFAULT_RELAY_URL,
            turnUrl = prefs[KEY_TURN_URL] ?: "",
            turnUsername = prefs[KEY_TURN_USERNAME] ?: "",
            turnPassword = prefs[KEY_TURN_PASSWORD] ?: "",
            discoverable = prefs[KEY_DISCOVERABLE] ?: true,
            autoDownloadMedia = prefs[KEY_AUTO_DOWNLOAD_MEDIA] ?: true,
            mediaQuality = prefs[KEY_MEDIA_QUALITY] ?: "ORIGINAL",
        )
    }

    suspend fun setRelayUrl(url: String) = context.settingsDataStore.edit { it[KEY_RELAY_URL] = url }
    suspend fun setTurnServer(url: String, username: String, password: String) =
        context.settingsDataStore.edit {
            it[KEY_TURN_URL] = url
            it[KEY_TURN_USERNAME] = username
            it[KEY_TURN_PASSWORD] = password
        }
    suspend fun setDiscoverable(value: Boolean) = context.settingsDataStore.edit { it[KEY_DISCOVERABLE] = value }
    suspend fun setAutoDownloadMedia(value: Boolean) = context.settingsDataStore.edit { it[KEY_AUTO_DOWNLOAD_MEDIA] = value }
    suspend fun setMediaQuality(value: String) = context.settingsDataStore.edit { it[KEY_MEDIA_QUALITY] = value }
}
