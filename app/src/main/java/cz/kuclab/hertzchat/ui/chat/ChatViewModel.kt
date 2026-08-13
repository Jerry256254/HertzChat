package cz.kuclab.hertzchat.ui.chat

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.data.db.ContactDao
import cz.kuclab.hertzchat.data.db.MessageDao
import cz.kuclab.hertzchat.data.model.PayloadKind
import cz.kuclab.hertzchat.data.repository.P2pChatService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    messageDao: MessageDao,
    contactDao: ContactDao,
    private val p2pChatService: P2pChatService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val contactId: String = checkNotNull(savedStateHandle["contactId"])

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft

    private val _contactNickname = MutableStateFlow("")
    val contactNickname: StateFlow<String> = _contactNickname

    init {
        viewModelScope.launch { _contactNickname.value = contactDao.find(contactId)?.nickname.orEmpty() }
    }

    val uiState = messageDao.observeMessages(contactId)
        .map { messages -> ChatUiState(messages = messages) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    fun onDraftChange(value: String) {
        _draft.value = value
    }

    fun send() {
        val text = _draft.value.trim()
        if (text.isEmpty()) return
        p2pChatService.sendText(contactId, text)
        _draft.value = ""
    }

    fun sendVideo(uri: Uri) = sendPickedMedia(uri, PayloadKind.VIDEO)

    fun sendImageBytes(bytes: ByteArray) {
        p2pChatService.sendMedia(contactId, bytes, "image/jpeg", PayloadKind.IMAGE, fileName = null)
    }

    fun sendVoice(file: File, durationMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = file.readBytes()
            p2pChatService.sendMedia(contactId, bytes, "audio/mp4", PayloadKind.VOICE, file.name, durationMs)
            file.delete()
        }
    }

    private fun sendPickedMedia(uri: Uri, kind: PayloadKind) {
        viewModelScope.launch {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(uri.toString().substringAfterLast('.', ""))
                ?: "application/octet-stream"
            val bytes = withContext(Dispatchers.IO) {
                resolver.openInputStream(uri)?.use { it.readBytes() }
            } ?: return@launch
            p2pChatService.sendMedia(contactId, bytes, mimeType, kind, fileName = null)
        }
    }
}

data class ChatUiState(
    val messages: List<cz.kuclab.hertzchat.data.db.MessageEntity> = emptyList(),
)
