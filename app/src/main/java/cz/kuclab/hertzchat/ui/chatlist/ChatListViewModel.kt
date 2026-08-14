package cz.kuclab.hertzchat.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.crypto.IdentityKeyManager
import cz.kuclab.hertzchat.data.db.AssistantConversationDao
import cz.kuclab.hertzchat.data.db.AssistantMessageDao
import cz.kuclab.hertzchat.data.db.ContactDao
import cz.kuclab.hertzchat.data.db.GroupDao
import cz.kuclab.hertzchat.data.db.MessageDao
import cz.kuclab.hertzchat.mistral.MISTRAL_ASSISTANT_CONTACT_ID
import cz.kuclab.hertzchat.mistral.MistralKeyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ChatListItemKind { CONTACT, GROUP, ASSISTANT }

data class ChatListItem(
    val contactId: String,
    val nickname: String,
    val avatarPath: String?,
    val pinned: Boolean,
    val lastMessagePreview: String?,
    val lastMessageAt: Long?,
    val kind: ChatListItemKind = ChatListItemKind.CONTACT,
    /** True for the auto-added contact that is this device's own identity - see P2pChatService.ensureSelfContact(). */
    val isSelf: Boolean = false,
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val contactDao: ContactDao,
    private val messageDao: MessageDao,
    private val groupDao: GroupDao,
    private val assistantConversationDao: AssistantConversationDao,
    private val assistantMessageDao: AssistantMessageDao,
    private val mistralKeyStore: MistralKeyStore,
    private val identityKeyManager: IdentityKeyManager,
) : ViewModel() {

    val mistralEnabled = mistralKeyStore.enabled

    val items = combine(
        contactDao.observeContacts(),
        groupDao.observeGroups(),
        assistantConversationDao.observeConversations(),
        mistralKeyStore.showAssistantContact,
        mistralKeyStore.assistantPinned,
    ) { contacts, groups, conversations, showAssistant, assistantPinned ->
        val myContactId = identityKeyManager.contactId()
        val contactItems = contacts.map { contact ->
            val last = messageDao.lastMessage(contact.contactId)
            ChatListItem(
                contactId = contact.contactId,
                nickname = contact.nickname,
                avatarPath = contact.avatarPath,
                pinned = contact.pinned,
                lastMessagePreview = last?.text,
                lastMessageAt = last?.timestamp,
                isSelf = contact.contactId == myContactId,
            )
        }

        val groupItems = groups.map { group ->
            val last = messageDao.lastMessage(group.groupId)
            ChatListItem(
                contactId = group.groupId,
                nickname = group.name,
                avatarPath = null,
                pinned = group.pinned,
                lastMessagePreview = last?.text,
                lastMessageAt = last?.timestamp,
                kind = ChatListItemKind.GROUP,
            )
        }

        // Shown from a fresh install onward, not only once a conversation exists -
        // otherwise the assistant is invisible to exactly the people who haven't set
        // it up yet, which is who the entry point is for. Tapping it before it's
        // configured routes to Settings (see ChatListScreen).
        val assistantItem = if (showAssistant) {
            val latest = conversations.maxByOrNull { it.lastMessageAt }
            val lastText = latest?.let { assistantMessageDao.recentForConversation(it.conversationId, 1).firstOrNull()?.text }
            ChatListItem(
                contactId = MISTRAL_ASSISTANT_CONTACT_ID,
                nickname = "Mistral AI",
                avatarPath = null,
                pinned = assistantPinned,
                lastMessagePreview = lastText ?: "Asistent appky - klepnutím nastavíš přístup",
                lastMessageAt = latest?.lastMessageAt,
                kind = ChatListItemKind.ASSISTANT,
            )
        } else {
            null
        }

        (contactItems + groupItems + listOfNotNull(assistantItem))
            .sortedWith(compareByDescending<ChatListItem> { it.pinned }.thenByDescending { it.lastMessageAt ?: 0L })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun togglePin(item: ChatListItem) {
        viewModelScope.launch {
            when (item.kind) {
                ChatListItemKind.GROUP -> groupDao.setPinned(item.contactId, !item.pinned)
                ChatListItemKind.ASSISTANT -> mistralKeyStore.setAssistantPinned(!item.pinned)
                ChatListItemKind.CONTACT -> contactDao.setPinned(item.contactId, !item.pinned)
            }
        }
    }

    fun block(contactId: String) {
        viewModelScope.launch { contactDao.setBlocked(contactId, true) }
    }

    /** Mistral has no "block" (there's no other party to block) - hiding removes its row instead, reversible in Settings. */
    fun hideAssistant() {
        mistralKeyStore.setShowAssistantContact(false)
    }
}
