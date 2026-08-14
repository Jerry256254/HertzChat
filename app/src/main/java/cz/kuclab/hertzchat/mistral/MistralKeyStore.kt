package cz.kuclab.hertzchat.mistral

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val PREFS_NAME = "hertzchat_mistral_prefs"
private const val KEY_ENABLED = "enabled"
private const val KEY_CONSENT_GIVEN = "consent_given"
private const val KEY_MODEL = "model"
private const val KEY_API_KEYS = "api_keys"
private const val KEY_SHOW_ASSISTANT_CONTACT = "show_assistant_contact"
private const val KEY_ASSISTANT_PINNED = "assistant_pinned"

/** Stable synthetic id for the assistant's row in [cz.kuclab.hertzchat.ui.chatlist.ChatListScreen] - it isn't a real [cz.kuclab.hertzchat.data.db.ContactEntity]. */
const val MISTRAL_ASSISTANT_CONTACT_ID = "mistral-ai-assistant"

const val MISTRAL_MODEL_SMALL = "mistral-small-latest"
const val MISTRAL_MODEL_MEDIUM = "mistral-medium-latest"
const val MISTRAL_MODEL_LARGE = "mistral-large-latest"

/**
 * Mistral API keys are secrets the user pastes in themselves - they get the
 * same at-rest protection (Keystore-wrapped EncryptedSharedPreferences) as
 * the Signal identity key in [cz.kuclab.hertzchat.crypto.IdentityKeyManager],
 * not the plain DataStore used for [cz.kuclab.hertzchat.data.repository.AppSettings].
 *
 * Values are cached in StateFlows (initialized from the encrypted prefs at
 * construction) so screens like Contacts/ChatList/Settings all observe the
 * same live state instead of needing a restart to see changes made elsewhere.
 */
@Singleton
class MistralKeyStore @Inject constructor(@ApplicationContext context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _enabled by lazy { MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false)) }
    val enabled: StateFlow<Boolean> get() = _enabled

    private val _consentGiven by lazy { MutableStateFlow(prefs.getBoolean(KEY_CONSENT_GIVEN, false)) }
    val consentGiven: StateFlow<Boolean> get() = _consentGiven

    private val _showAssistantContact by lazy { MutableStateFlow(prefs.getBoolean(KEY_SHOW_ASSISTANT_CONTACT, true)) }
    val showAssistantContact: StateFlow<Boolean> get() = _showAssistantContact

    /** The assistant isn't a real [cz.kuclab.hertzchat.data.db.ContactEntity], so its pinned state can't live in that table - it lives here instead. */
    private val _assistantPinned by lazy { MutableStateFlow(prefs.getBoolean(KEY_ASSISTANT_PINNED, false)) }
    val assistantPinned: StateFlow<Boolean> get() = _assistantPinned

    fun setAssistantPinned(value: Boolean) {
        prefs.edit().putBoolean(KEY_ASSISTANT_PINNED, value).apply()
        _assistantPinned.value = value
    }

    private val _model by lazy { MutableStateFlow(prefs.getString(KEY_MODEL, null) ?: MISTRAL_MODEL_SMALL) }
    val model: StateFlow<String> get() = _model

    private val _keys by lazy { MutableStateFlow(readKeys()) }
    val keys: StateFlow<List<String>> get() = _keys

    private fun readKeys(): List<String> =
        prefs.getString(KEY_API_KEYS, null)?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() } ?: emptyList()

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }

    fun setConsentGiven(value: Boolean) {
        prefs.edit().putBoolean(KEY_CONSENT_GIVEN, value).apply()
        _consentGiven.value = value
    }

    fun setShowAssistantContact(value: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_ASSISTANT_CONTACT, value).apply()
        _showAssistantContact.value = value
    }

    fun setModel(value: String) {
        prefs.edit().putString(KEY_MODEL, value).apply()
        _model.value = value
    }

    /** Current snapshot for callers that just need a one-shot read (e.g. [MistralApiClient]). */
    val currentKeys: List<String> get() = _keys.value

    fun addKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return
        writeKeys(_keys.value + trimmed)
    }

    fun removeKey(index: Int) {
        writeKeys(_keys.value.filterIndexed { i, _ -> i != index })
    }

    private fun writeKeys(newKeys: List<String>) {
        prefs.edit().putString(KEY_API_KEYS, json.encodeToString(newKeys)).apply()
        _keys.value = newKeys
    }

    /** One-shot snapshot for [MistralApiClient], which doesn't need to react to live changes mid-request. */
    val currentModel: String get() = _model.value
}
