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
private val KEY_AUTO_ACCEPT_REQUESTS = booleanPreferencesKey("auto_accept_requests")
private val KEY_LANGUAGE_CODE = stringPreferencesKey("language_code")
private val KEY_SHOW_ASSISTANT_CONTACT = booleanPreferencesKey("show_assistant_contact")
private val KEY_ASSISTANT_PINNED = booleanPreferencesKey("assistant_pinned")
private val KEY_RELAY_URLS = stringPreferencesKey("relay_urls")

data class AppSettings(
    val discoverable: Boolean = true,
    val mediaQuality: String = "ORIGINAL",
    val notificationsEnabled: Boolean = true,
    val themeMode: String = "SYSTEM",
    val autoAcceptFriendRequests: Boolean = false,
    val languageCode: String = cz.kuclab.hertzchat.locale.LANGUAGE_SYSTEM,
    /** The Hertz web assistant isn't a real contact, so its chat-list row visibility and pinned state live here instead of the contacts table. */
    val showAssistantContact: Boolean = true,
    val assistantPinned: Boolean = false,
    /**
     * Relay WebSocket URLs, one per line in storage. Empty means "use the
     * built-in free defaults" - the user only touches this to point the app
     * at their own relay (see server/hertz-relay).
     */
    val relayUrls: List<String> = emptyList(),
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            discoverable = prefs[KEY_DISCOVERABLE] ?: true,
            mediaQuality = prefs[KEY_MEDIA_QUALITY] ?: "ORIGINAL",
            notificationsEnabled = prefs[KEY_NOTIFICATIONS_ENABLED] ?: true,
            themeMode = prefs[KEY_THEME_MODE] ?: "SYSTEM",
            autoAcceptFriendRequests = prefs[KEY_AUTO_ACCEPT_REQUESTS] ?: false,
            languageCode = prefs[KEY_LANGUAGE_CODE] ?: cz.kuclab.hertzchat.locale.LANGUAGE_SYSTEM,
            showAssistantContact = prefs[KEY_SHOW_ASSISTANT_CONTACT] ?: true,
            assistantPinned = prefs[KEY_ASSISTANT_PINNED] ?: false,
            relayUrls = prefs[KEY_RELAY_URLS]?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
        )
    }

    suspend fun setDiscoverable(value: Boolean) = context.settingsDataStore.edit { it[KEY_DISCOVERABLE] = value }
    suspend fun setMediaQuality(value: String) = context.settingsDataStore.edit { it[KEY_MEDIA_QUALITY] = value }
    suspend fun setNotificationsEnabled(value: Boolean) = context.settingsDataStore.edit { it[KEY_NOTIFICATIONS_ENABLED] = value }
    suspend fun setThemeMode(value: String) = context.settingsDataStore.edit { it[KEY_THEME_MODE] = value }
    suspend fun setAutoAcceptFriendRequests(value: Boolean) = context.settingsDataStore.edit { it[KEY_AUTO_ACCEPT_REQUESTS] = value }
    suspend fun setLanguageCode(value: String) = context.settingsDataStore.edit { it[KEY_LANGUAGE_CODE] = value }
    suspend fun setShowAssistantContact(value: Boolean) = context.settingsDataStore.edit { it[KEY_SHOW_ASSISTANT_CONTACT] = value }
    suspend fun setAssistantPinned(value: Boolean) = context.settingsDataStore.edit { it[KEY_ASSISTANT_PINNED] = value }
    suspend fun setRelayUrls(value: List<String>) = context.settingsDataStore.edit { it[KEY_RELAY_URLS] = value.joinToString("\n") }
}
