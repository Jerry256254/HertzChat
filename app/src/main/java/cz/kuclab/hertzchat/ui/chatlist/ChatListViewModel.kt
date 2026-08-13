package cz.kuclab.hertzchat.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.data.db.ContactDao
import cz.kuclab.hertzchat.data.db.MessageDao
import cz.kuclab.hertzchat.data.repository.P2pChatService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatListItem(
    val contactId: String,
    val nickname: String,
    val pinned: Boolean,
    val online: Boolean,
    val lastMessagePreview: String?,
    val lastMessageAt: Long?,
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val contactDao: ContactDao,
    private val messageDao: MessageDao,
    private val p2pChatService: P2pChatService,
) : ViewModel() {

    val items = combine(contactDao.observeContacts(), p2pChatService.onlinePresence) { contacts, presence ->
        val onlineIds = presence.map { it.contactId }.toSet()
        contacts.map { contact ->
            val last = messageDao.lastMessage(contact.contactId)
            ChatListItem(
                contactId = contact.contactId,
                nickname = contact.nickname,
                pinned = contact.pinned,
                online = contact.contactId in onlineIds,
                lastMessagePreview = last?.text,
                lastMessageAt = last?.timestamp,
            )
        }.sortedWith(compareByDescending<ChatListItem> { it.pinned }.thenByDescending { it.lastMessageAt ?: 0L })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun togglePin(item: ChatListItem) {
        viewModelScope.launch { contactDao.setPinned(item.contactId, !item.pinned) }
    }

    fun block(contactId: String) {
        viewModelScope.launch { contactDao.setBlocked(contactId, true) }
    }
}
