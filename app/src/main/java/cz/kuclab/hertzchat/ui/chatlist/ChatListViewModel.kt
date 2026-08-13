package cz.kuclab.hertzchat.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.data.db.ContactDao
import cz.kuclab.hertzchat.data.db.MessageDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatListItem(
    val contactId: String,
    val nickname: String,
    val avatarPath: String?,
    val pinned: Boolean,
    val lastMessagePreview: String?,
    val lastMessageAt: Long?,
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val contactDao: ContactDao,
    private val messageDao: MessageDao,
) : ViewModel() {

    val items = contactDao.observeContacts().map { contacts ->
        contacts.map { contact ->
            val last = messageDao.lastMessage(contact.contactId)
            ChatListItem(
                contactId = contact.contactId,
                nickname = contact.nickname,
                avatarPath = contact.avatarPath,
                pinned = contact.pinned,
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
