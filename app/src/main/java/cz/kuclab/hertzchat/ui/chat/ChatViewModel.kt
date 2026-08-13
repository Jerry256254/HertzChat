package cz.kuclab.hertzchat.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.data.db.MessageDao
import cz.kuclab.hertzchat.data.repository.P2pChatService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    messageDao: MessageDao,
    private val p2pChatService: P2pChatService,
) : ViewModel() {

    val contactId: String = checkNotNull(savedStateHandle["contactId"])

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft

    val uiState = combine(
        messageDao.observeMessages(contactId),
        p2pChatService.onlinePresence,
    ) { messages, presence ->
        ChatUiState(
            messages = messages,
            peerOnline = presence.any { it.contactId == contactId },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    fun onDraftChange(value: String) {
        _draft.value = value
    }

    fun send() {
        val text = _draft.value.trim()
        if (text.isEmpty()) return
        p2pChatService.sendText(contactId, text)
        _draft.value = ""
    }
}

data class ChatUiState(
    val messages: List<cz.kuclab.hertzchat.data.db.MessageEntity> = emptyList(),
    val peerOnline: Boolean = false,
)
