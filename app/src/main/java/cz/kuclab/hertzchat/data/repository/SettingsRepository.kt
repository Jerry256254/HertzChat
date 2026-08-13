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

private val KEY_DISCOVERABLE = booleanPreferencesKey("discoverable")
private val KEY_AUTO_DOWNLOAD_MEDIA = booleanPreferencesKey("auto_download_media")
private val KEY_MEDIA_QUALITY = stringPreferencesKey("media_quality") // ORIGINAL | HIGH | BALANCED

data class AppSettings(
    val discoverable: Boolean = true,
    val autoDownloadMedia: Boolean = true,
    val mediaQuality: String = "ORIGINAL",
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            discoverable = prefs[KEY_DISCOVERABLE] ?: true,
            autoDownloadMedia = prefs[KEY_AUTO_DOWNLOAD_MEDIA] ?: true,
            mediaQuality = prefs[KEY_MEDIA_QUALITY] ?: "ORIGINAL",
        )
    }

    suspend fun setDiscoverable(value: Boolean) = context.settingsDataStore.edit { it[KEY_DISCOVERABLE] = value }
    suspend fun setAutoDownloadMedia(value: Boolean) = context.settingsDataStore.edit { it[KEY_AUTO_DOWNLOAD_MEDIA] = value }
    suspend fun setMediaQuality(value: String) = context.settingsDataStore.edit { it[KEY_MEDIA_QUALITY] = value }
}
