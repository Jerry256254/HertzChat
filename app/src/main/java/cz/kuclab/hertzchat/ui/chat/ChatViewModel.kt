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
import cz.kuclab.hertzchat.data.db.MessageEntity
import cz.kuclab.hertzchat.data.db.ThreadReadStateDao
import cz.kuclab.hertzchat.data.db.ThreadReadStateEntity
import cz.kuclab.hertzchat.data.model.PayloadKind
import cz.kuclab.hertzchat.data.repository.DraftStore
import cz.kuclab.hertzchat.data.repository.P2pChatService
import cz.kuclab.hertzchat.data.repository.SettingsRepository
import cz.kuclab.hertzchat.media.MediaStorage
import cz.kuclab.hertzchat.media.PendingCaptureStore
import cz.kuclab.hertzchat.network.p2p.I2pState
import cz.kuclab.hertzchat.p2p.ActiveChatTracker
import cz.kuclab.hertzchat.ui.common.PendingAttachment
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
    private val messageDao: MessageDao,
    private val contactDao: ContactDao,
    private val readStateDao: ThreadReadStateDao,
    private val p2pChatService: P2pChatService,
    private val settingsRepository: SettingsRepository,
    private val draftStore: DraftStore,
    identityKeyManager: IdentityKeyManager,
    private val mediaStorage: MediaStorage,
    private val activeChatTracker: ActiveChatTracker,
    private val captureStore: PendingCaptureStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val contactId: String = checkNotNull(savedStateHandle["contactId"])
    val isSelf: Boolean = contactId == identityKeyManager.contactId()

    init {
        // Suppresses the notification MessageNotifier would otherwise fire for a message
        // arriving in the exact thread already open on screen.
        activeChatTracker.activeThreadId.value = contactId
        // While this screen is alive everything shown is "seen" - the watermark follows
        // the newest visible message, which clears the chat-list unread dot live.
        viewModelScope.launch {
            messageDao.observeMessages(contactId).collect { list ->
                list.maxOfOrNull { it.timestamp }?.let { newest ->
                    readStateDao.upsert(ThreadReadStateEntity(contactId, newest))
                }
            }
        }
    }

    override fun onCleared() {
        if (activeChatTracker.activeThreadId.value == contactId) activeChatTracker.activeThreadId.value = null
    }

    private val _draft = MutableStateFlow(draftStore.get(contactId))
    val draft: StateFlow<String> = _draft

    private val _contactNickname = MutableStateFlow("")
    val contactNickname: StateFlow<String> = _contactNickname

    private val _contactAvatarPath = MutableStateFlow<String?>(null)
    val contactAvatarPath: StateFlow<String?> = _contactAvatarPath

    private val _imageJpegQuality = MutableStateFlow(95)
    val imageJpegQuality: StateFlow<Int> = _imageJpegQuality

    /** One-shot user-facing notices (cold-start deferred send, ...). */
    private val _userNotice = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userNotice: SharedFlow<String> = _userNotice
    private var coldStartNoticeShown = false

    /** Attachments staged in the input tray - sent together with the message, never alone. */
    private val _pending = MutableStateFlow<List<PendingAttachment>>(emptyList())
    val pending: StateFlow<List<PendingAttachment>> = _pending

    /** A photo fresh from the in-app camera, waiting to be opened in the editor. */
    val cameraCapture = captureStore.pendingCapture

    fun consumeCapture() = captureStore.consume()

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
        val staged = _pending.value
        if (text.isEmpty() && staged.isEmpty()) return
        warnIfOffline()
        if (text.isNotEmpty()) p2pChatService.sendText(contactId, text)
        staged.forEach { p2pChatService.sendMedia(contactId, it.file.readBytes(), it.mimeType, it.kind, it.fileName) }
        clearPending()
        _draft.value = ""
        draftStore.clear(contactId)
    }

    /** Stages picked bytes without sending - they go out with [send] together with the message. */
    fun stageAttachment(bytes: ByteArray, mimeType: String, kind: PayloadKind, fileName: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = File(context.cacheDir, "pending").apply { mkdirs() }
            val ext = mediaStorage.extensionFor(mimeType)
            val file = File(dir, "pending_${System.currentTimeMillis()}_${(0..9999).random()}.$ext")
            file.writeBytes(bytes)
            _pending.value = _pending.value + PendingAttachment(file, mimeType, kind, fileName)
        }
    }

    fun stageAttachmentUri(uri: Uri, kind: PayloadKind) {
        viewModelScope.launch {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(uri.toString().substringAfterLast('.', ""))
                ?: "application/octet-stream"
            val bytes = withContext(Dispatchers.IO) {
                resolver.openInputStream(uri)?.use { it.readBytes() }
            } ?: return@launch
            stageAttachment(bytes, mimeType, kind, fileName = displayNameOf(uri).takeIf { kind == PayloadKind.FILE })
        }
    }

    fun removePending(attachment: PendingAttachment) {
        _pending.value = _pending.value - attachment
        viewModelScope.launch(Dispatchers.IO) { runCatching { attachment.file.delete() } }
    }

    private fun clearPending() {
        val staged = _pending.value
        _pending.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) { staged.forEach { runCatching { it.file.delete() } } }
    }

    fun sendVoice(file: File, durationMs: Long) {
        warnIfOffline()
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = file.readBytes()
            p2pChatService.sendMedia(contactId, bytes, "audio/mp4", PayloadKind.VOICE, file.name, durationMs)
            file.delete()
        }
    }

    /** In-chat find - LIKE wildcards in the query are escaped so they match literally. */
    suspend fun searchInChat(rawQuery: String): List<MessageEntity> {
        val escaped = rawQuery.trim()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        if (escaped.isEmpty()) return emptyList()
        return messageDao.searchInThread(contactId, escaped)
    }

    /** Wipes this conversation locally only - the contact, trust and Signal session stay untouched. */
    fun clearChat() {
        viewModelScope.launch { messageDao.deleteAllForContact(contactId) }
    }

    /** Saves an attachment to shared storage (Gallery/Downloads) and reports where it landed. */
    fun downloadMessage(message: MessageEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = message.mediaPath?.let { File(it) }
            if (file == null || !file.exists()) {
                _userNotice.emit("Soubor už není k dispozici")
                return@launch
            }
            mediaStorage.saveToPublic(file, message.mediaMimeType, message.mediaFileName ?: file.name)
                .onSuccess { location -> _userNotice.emit("Uloženo do $location") }
                .onFailure { _userNotice.emit("Uložení selhalo") }
        }
    }

    fun blockContact() {
        viewModelScope.launch { contactDao.setBlocked(contactId, true) }
    }

    /**
     * Sending during a cold start (I2P not connected yet) queues the message for later -
     * correct, but silent. The first such send per screen shows a notice so "nothing
     * happened" doesn't read as broken.
     */
    private fun warnIfOffline() {
        if (isSelf || p2pChatService.i2pState.value == I2pState.CONNECTED) {
            coldStartNoticeShown = false
            return
        }
        if (!coldStartNoticeShown) {
            coldStartNoticeShown = true
            _userNotice.tryEmit("Zpráva se odešle po připojení k I2P")
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
