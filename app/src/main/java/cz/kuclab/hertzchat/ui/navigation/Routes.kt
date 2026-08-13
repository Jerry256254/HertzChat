package cz.kuclab.hertzchat.ui.navigation

object Routes {
    const val ONBOARDING = "onboarding"
    const val CHAT_LIST = "chat_list"
    const val CHAT = "chat/{contactId}"
    const val CONTACTS = "contacts"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val QR_EXPORT = "migration/export"
    const val QR_IMPORT = "migration/import"

    fun chat(contactId: String) = "chat/$contactId"
}
