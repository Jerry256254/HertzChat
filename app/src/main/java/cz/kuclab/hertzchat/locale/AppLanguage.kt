package cz.kuclab.hertzchat.locale

/** "system" means "no override - follow the device locale" (falls back to Czech if the device locale isn't one of these). */
const val LANGUAGE_SYSTEM = "system"

data class AppLanguage(val code: String, val nativeName: String)

val SUPPORTED_LANGUAGES = listOf(
    AppLanguage(LANGUAGE_SYSTEM, "Podle systému / System"),
    AppLanguage("cs", "Čeština"),
    AppLanguage("en", "English"),
    AppLanguage("de", "Deutsch"),
    AppLanguage("es", "Español"),
    AppLanguage("fr", "Français"),
    AppLanguage("uk", "Українська"),
    AppLanguage("ru", "Русский"),
)
