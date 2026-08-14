package cz.kuclab.hertzchat.ui.mistral

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.data.db.AssistantConversationDao
import cz.kuclab.hertzchat.data.db.AssistantMessageDao
import cz.kuclab.hertzchat.data.repository.DraftStore
import cz.kuclab.hertzchat.mistral.AssistantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class AssistantChatViewModel @Inject constructor(
    private val repository: AssistantRepository,
    private val draftStore: DraftStore,
    conversationDao: AssistantConversationDao,
    messageDao: AssistantMessageDao,
) : ViewModel() {

    val sending = repository.sending

    private val _draft = MutableStateFlow(draftStore.get(DRAFT_KEY))
    val draft: StateFlow<String> = _draft

    private val _chatsSheetOpen = MutableStateFlow(false)
    val chatsSheetOpen: StateFlow<Boolean> = _chatsSheetOpen

    val conversations = conversationDao.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val messages = repository.activeConversationId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else messageDao.observeMessages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeConversationId = repository.activeConversationId

    init {
        viewModelScope.launch { repository.ensureActiveConversation() }
    }

    fun onDraftChange(value: String) {
        _draft.value = value
        draftStore.set(DRAFT_KEY, value)
    }

    fun send() {
        val text = _draft.value.trim()
        if (text.isEmpty()) return
        _draft.value = ""
        draftStore.clear(DRAFT_KEY)
        when {
            text.equals("/new", ignoreCase = true) -> repository.newConversation()
            text.equals("/chats", ignoreCase = true) -> _chatsSheetOpen.value = true
            else -> repository.send(text)
        }
    }

    fun openChatsSheet() {
        _chatsSheetOpen.value = true
    }

    fun closeChatsSheet() {
        _chatsSheetOpen.value = false
    }

    fun switchConversation(conversationId: String) {
        repository.switchConversation(conversationId)
        _chatsSheetOpen.value = false
    }

    fun startNewConversation() {
        repository.newConversation()
        _chatsSheetOpen.value = false
    }

    fun deleteConversation(conversationId: String) {
        repository.deleteConversation(conversationId)
    }

    fun renameConversation(conversationId: String, title: String) {
        repository.renameConversation(conversationId, title)
    }

    private companion object {
        /** One shared draft for the assistant: switching between its conversations shouldn't lose what you typed. */
        const val DRAFT_KEY = "assistant"
    }
}
