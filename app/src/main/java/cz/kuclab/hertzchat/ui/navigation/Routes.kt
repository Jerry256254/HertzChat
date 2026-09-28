package cz.kuclab.hertzchat.ui.navigation

object Routes {
    const val ONBOARDING = "onboarding"
    const val CHAT_LIST = "chat_list"
    const val CHAT = "chat/{contactId}"
    const val GROUP_CHAT = "group_chat/{groupId}"
    const val CONTACTS = "contacts"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val QR_EXPORT = "migration/export"
    const val QR_IMPORT = "migration/import"
    const val ASSISTANT_CHAT = "assistant_chat"
    const val FILE_VIEWER = "file_viewer/{messageId}"
    const val CALL = "call/{contactId}"

    fun chat(contactId: String) = "chat/$contactId"
    fun groupChat(groupId: String) = "group_chat/$groupId"
    fun fileViewer(messageId: String) = "file_viewer/$messageId"
    fun call(contactId: String) = "call/$contactId"
}
