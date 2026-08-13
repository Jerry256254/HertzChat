package cz.kuclab.hertzchat.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.data.db.AssistantConversationDao
import cz.kuclab.hertzchat.data.db.AssistantMessageDao
import cz.kuclab.hertzchat.data.db.ContactDao
import cz.kuclab.hertzchat.data.db.MessageDao
import cz.kuclab.hertzchat.mistral.MISTRAL_ASSISTANT_CONTACT_ID
import cz.kuclab.hertzchat.mistral.MistralKeyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ChatListItemKind { CONTACT, ASSISTANT }

data class ChatListItem(
    val contactId: String,
    val nickname: String,
    val avatarPath: String?,
    val pinned: Boolean,
    val lastMessagePreview: String?,
    val lastMessageAt: Long?,
    val kind: ChatListItemKind = ChatListItemKind.CONTACT,
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val contactDao: ContactDao,
    private val messageDao: MessageDao,
    private val assistantConversationDao: AssistantConversationDao,
    private val assistantMessageDao: AssistantMessageDao,
    private val mistralKeyStore: MistralKeyStore,
) : ViewModel() {

    val mistralEnabled = mistralKeyStore.enabled

    val items = combine(
        contactDao.observeContacts(),
        assistantConversationDao.observeConversations(),
        mistralKeyStore.showAssistantContact,
    ) { contacts, conversations, showAssistant ->
        val contactItems = contacts.map { contact ->
            val last = messageDao.lastMessage(contact.contactId)
            ChatListItem(
                contactId = contact.contactId,
                nickname = contact.nickname,
                avatarPath = contact.avatarPath,
                pinned = contact.pinned,
                lastMessagePreview = last?.text,
                lastMessageAt = last?.timestamp,
            )
        }

        val assistantItem = if (showAssistant && conversations.isNotEmpty()) {
            val latest = conversations.maxByOrNull { it.lastMessageAt }
            val lastText = latest?.let { assistantMessageDao.recentForConversation(it.conversationId, 1).firstOrNull()?.text }
            ChatListItem(
                contactId = MISTRAL_ASSISTANT_CONTACT_ID,
                nickname = "Mistral AI",
                avatarPath = null,
                pinned = false,
                lastMessagePreview = lastText,
                lastMessageAt = latest?.lastMessageAt,
                kind = ChatListItemKind.ASSISTANT,
            )
        } else {
            null
        }

        (contactItems + listOfNotNull(assistantItem))
            .sortedWith(compareByDescending<ChatListItem> { it.pinned }.thenByDescending { it.lastMessageAt ?: 0L })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun togglePin(item: ChatListItem) {
        viewModelScope.launch { contactDao.setPinned(item.contactId, !item.pinned) }
    }

    fun block(contactId: String) {
        viewModelScope.launch { contactDao.setBlocked(contactId, true) }
    }
}
