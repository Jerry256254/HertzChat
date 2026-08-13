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
private val KEY_MEDIA_QUALITY = stringPreferencesKey("media_quality") // ORIGINAL | HIGH | BALANCED
private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
private val KEY_THEME_MODE = stringPreferencesKey("theme_mode") // SYSTEM | LIGHT | DARK

data class AppSettings(
    val discoverable: Boolean = true,
    val mediaQuality: String = "ORIGINAL",
    val notificationsEnabled: Boolean = true,
    val themeMode: String = "SYSTEM",
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            discoverable = prefs[KEY_DISCOVERABLE] ?: true,
            mediaQuality = prefs[KEY_MEDIA_QUALITY] ?: "ORIGINAL",
            notificationsEnabled = prefs[KEY_NOTIFICATIONS_ENABLED] ?: true,
            themeMode = prefs[KEY_THEME_MODE] ?: "SYSTEM",
        )
    }

    suspend fun setDiscoverable(value: Boolean) = context.settingsDataStore.edit { it[KEY_DISCOVERABLE] = value }
    suspend fun setMediaQuality(value: String) = context.settingsDataStore.edit { it[KEY_MEDIA_QUALITY] = value }
    suspend fun setNotificationsEnabled(value: Boolean) = context.settingsDataStore.edit { it[KEY_NOTIFICATIONS_ENABLED] = value }
    suspend fun setThemeMode(value: String) = context.settingsDataStore.edit { it[KEY_THEME_MODE] = value }
}
