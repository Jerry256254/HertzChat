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
import kotlinx.coroutines.flow.firstOrNull
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
private val KEY_PUSH_WAKE_ENABLED = booleanPreferencesKey("push_wake_enabled")
private val KEY_NTFY_SERVER_URL = stringPreferencesKey("ntfy_server_url")
private val KEY_PUSH_TOPIC = stringPreferencesKey("push_topic")
private val KEY_PUSH_LAST_SEEN_ID = stringPreferencesKey("push_last_seen_id")

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
    /** Google-free wake-up pings over ntfy - an empty "you have mail" signal, never content. */
    val pushWakeEnabled: Boolean = true,
    /** Self-hostable ntfy server base URL (default https://ntfy.sh). */
    val ntfyServerUrl: String = cz.kuclab.hertzchat.p2p.NtfyPing.DEFAULT_SERVER,
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
            pushWakeEnabled = prefs[KEY_PUSH_WAKE_ENABLED] ?: true,
            ntfyServerUrl = prefs[KEY_NTFY_SERVER_URL] ?: cz.kuclab.hertzchat.p2p.NtfyPing.DEFAULT_SERVER,
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
    suspend fun setPushWakeEnabled(value: Boolean) = context.settingsDataStore.edit { it[KEY_PUSH_WAKE_ENABLED] = value }
    suspend fun setNtfyServerUrl(value: String) = context.settingsDataStore.edit { it[KEY_NTFY_SERVER_URL] = value }

    /**
     * This device's random inbound ntfy topic, generated once and shared with
     * contacts over P2P - whoever knows it can wake us with an empty ping.
     */
    suspend fun pushTopic(): String {
        context.settingsDataStore.data.map { it[KEY_PUSH_TOPIC] }.firstOrNull()?.let { return it }
        val fresh = cz.kuclab.hertzchat.p2p.NtfyPing.newTopic()
        context.settingsDataStore.edit { it[KEY_PUSH_TOPIC] = fresh }
        return context.settingsDataStore.data.map { it[KEY_PUSH_TOPIC] }.firstOrNull() ?: fresh
    }

    suspend fun pushLastSeenId(): String? = context.settingsDataStore.data.map { it[KEY_PUSH_LAST_SEEN_ID] }.firstOrNull()
    suspend fun setPushLastSeenId(value: String) = context.settingsDataStore.edit { it[KEY_PUSH_LAST_SEEN_ID] = value }
}
