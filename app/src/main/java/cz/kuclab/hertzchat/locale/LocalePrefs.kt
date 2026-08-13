package cz.kuclab.hertzchat.locale

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

private const val PREFS_NAME = "hertzchat_locale_prefs"
private const val KEY_LANGUAGE_CODE = "language_code"

/**
 * Fast, synchronous read/write for the chosen app language - separate from
 * [cz.kuclab.hertzchat.data.repository.SettingsRepository]'s DataStore
 * (which is async) because [android.app.Activity.attachBaseContext] needs
 * the value *before* any suspend function could possibly run.
 */
object LocalePrefs {
    fun getLanguageCode(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE_CODE, null)?.takeIf { it != LANGUAGE_SYSTEM }
    }

    fun setLanguageCode(context: Context, code: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_CODE, code)
            .apply()
    }

    /** Wraps [context] so its resources resolve to the stored language, if one was chosen - otherwise returns [context] unchanged (device locale applies). */
    fun wrap(context: Context): Context {
        val code = getLanguageCode(context) ?: return context
        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
