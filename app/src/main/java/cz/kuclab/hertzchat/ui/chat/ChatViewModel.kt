package cz.kuclab.hertzchat.ui.chat

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.crypto.IdentityKeyManager
import cz.kuclab.hertzchat.data.db.ContactDao
import cz.kuclab.hertzchat.data.db.MessageDao
import cz.kuclab.hertzchat.data.model.PayloadKind
import cz.kuclab.hertzchat.data.repository.DraftStore
import cz.kuclab.hertzchat.data.repository.P2pChatService
import cz.kuclab.hertzchat.data.repository.SettingsRepository
import cz.kuclab.hertzchat.media.MediaStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    messageDao: MessageDao,
    private val contactDao: ContactDao,
    private val p2pChatService: P2pChatService,
    private val settingsRepository: SettingsRepository,
    private val draftStore: DraftStore,
    identityKeyManager: IdentityKeyManager,
    private val mediaStorage: MediaStorage,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val contactId: String = checkNotNull(savedStateHandle["contactId"])
    val isSelf: Boolean = contactId == identityKeyManager.contactId()

    private val _draft = MutableStateFlow(draftStore.get(contactId))
    val draft: StateFlow<String> = _draft

    private val _contactNickname = MutableStateFlow("")
    val contactNickname: StateFlow<String> = _contactNickname

    private val _contactAvatarPath = MutableStateFlow<String?>(null)
    val contactAvatarPath: StateFlow<String?> = _contactAvatarPath

    private val _imageJpegQuality = MutableStateFlow(95)
    val imageJpegQuality: StateFlow<Int> = _imageJpegQuality

    init {
        viewModelScope.launch {
            val contact = contactDao.find(contactId)
            _contactNickname.value = contact?.nickname.orEmpty()
            // Own photo is already on this device - showing it never depends on I2P
            // round-tripping an AVATAR transfer to yourself.
            _contactAvatarPath.value = if (isSelf) {
                mediaStorage.selfAvatarFile().takeIf { it.exists() }?.absolutePath
            } else {
                contact?.avatarPath
            }
        }
        viewModelScope.launch {
            _imageJpegQuality.value = when (settingsRepository.settings.first().mediaQuality) {
                "HIGH" -> 85
                "BALANCED" -> 70
                else -> 95
            }
        }
    }

    val uiState = messageDao.observeMessages(contactId)
        .map { messages -> ChatUiState(messages = messages) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    fun onDraftChange(value: String) {
        _draft.value = value
        draftStore.set(contactId, value)
    }

    fun send() {
        val text = _draft.value.trim()
        if (text.isEmpty()) return
        p2pChatService.sendText(contactId, text)
        _draft.value = ""
        draftStore.clear(contactId)
    }

    fun sendVideo(uri: Uri) = sendPickedMedia(uri, PayloadKind.VIDEO)

    fun sendFile(uri: Uri) = sendPickedMedia(uri, PayloadKind.FILE)

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

    fun blockContact() {
        viewModelScope.launch { contactDao.setBlocked(contactId, true) }
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
            p2pChatService.sendMedia(contactId, bytes, mimeType, kind, fileName = displayNameOf(uri).takeIf { kind == PayloadKind.FILE })
        }
    }

    /** The user-facing filename behind a content:// uri, so a received file arrives named as it was sent. */
    private fun displayNameOf(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
}

data class ChatUiState(
    val messages: List<cz.kuclab.hertzchat.data.db.MessageEntity> = emptyList(),
)
