package cz.kuclab.hertzchat.ui.groupchat

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.crypto.IdentityKeyManager
import cz.kuclab.hertzchat.data.db.ContactDao
import cz.kuclab.hertzchat.data.db.ContactEntity
import cz.kuclab.hertzchat.data.db.GroupDao
import cz.kuclab.hertzchat.data.db.GroupMemberDao
import cz.kuclab.hertzchat.data.db.GroupMemberEntity
import cz.kuclab.hertzchat.data.db.MessageDao
import cz.kuclab.hertzchat.data.db.MessageEntity
import cz.kuclab.hertzchat.data.db.ThreadReadStateDao
import cz.kuclab.hertzchat.data.db.ThreadReadStateEntity
import cz.kuclab.hertzchat.data.model.PayloadKind
import cz.kuclab.hertzchat.data.repository.DraftStore
import cz.kuclab.hertzchat.data.repository.P2pChatService
import cz.kuclab.hertzchat.media.MediaStorage
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MentionSuggestion(val id: String, val label: String)

/** A member as the UI needs it - [GroupMemberEntity] itself doesn't carry a photo, since it's a denormalized roster snapshot, not the contact record. */
data class GroupMemberUi(val contactId: String, val nickname: String, val avatarPath: String?, val isSelf: Boolean = false)

@HiltViewModel
class GroupChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageDao: MessageDao,
    groupDao: GroupDao,
    groupMemberDao: GroupMemberDao,
    contactDao: ContactDao,
    identityKeyManager: IdentityKeyManager,
    private val mediaStorage: MediaStorage,
    private val readStateDao: ThreadReadStateDao,
    private val activeChatTracker: ActiveChatTracker,
    private val p2pChatService: P2pChatService,
    private val draftStore: DraftStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val groupId: String = checkNotNull(savedStateHandle["groupId"])
    private val myId = identityKeyManager.contactId()

    /** One-shot user-facing notices (cold-start deferred send, ...). */
    private val _userNotice = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userNotice: SharedFlow<String> = _userNotice
    private var coldStartNoticeShown = false

    /** Attachments staged in the input tray - sent together with the message, never alone. */
    private val _pending = MutableStateFlow<List<PendingAttachment>>(emptyList())
    val pending: StateFlow<List<PendingAttachment>> = _pending

    init {
        activeChatTracker.activeThreadId.value = groupId
        // Members dial while the user reads/types - the first send fans out over
        // live connections instead of paying a dial per member.
        p2pChatService.warmGroup(groupId)
        viewModelScope.launch {
            messageDao.observeMessages(groupId).collect { list ->
                list.maxOfOrNull { it.timestamp }?.let { newest ->
                    readStateDao.upsert(ThreadReadStateEntity(groupId, newest))
                }
            }
        }
    }

    override fun onCleared() {
        if (activeChatTracker.activeThreadId.value == groupId) activeChatTracker.activeThreadId.value = null
    }

    val groupName = groupDao.observeGroup(groupId)
        .map { it?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val isOwner = groupDao.observeGroup(groupId)
        .map { it != null && (it.ownerId.isBlank() || it.ownerId == myId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val members = groupMemberDao.observeMembers(groupId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<GroupMemberEntity>())

    /** Real photos where we have them - see the (Ty) row for why the self entry reads its avatar straight off disk instead of through a contact row. */
    val membersUi: StateFlow<List<GroupMemberUi>> = combine(members, contactDao.observeContacts()) { list, contacts ->
        val byId = contacts.associateBy { it.contactId }
        val self = GroupMemberUi(myId, identityKeyManager.nickname, mediaStorage.selfAvatarFile().takeIf { it.exists() }?.absolutePath, isSelf = true)
        listOf(self) + list.map { m -> GroupMemberUi(m.contactId, m.nickname, byId[m.contactId]?.avatarPath) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Existing 1:1 contacts not already in this group - candidates for [addMembers]. */
    val addableContacts: StateFlow<List<ContactEntity>> = combine(contactDao.observeContacts(), members) { contacts, list ->
        val memberIds = list.map { it.contactId }.toSet()
        contacts.filter { it.contactId != myId && it.contactId !in memberIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addMembers(contactIds: List<String>) = p2pChatService.addGroupMembers(groupId, contactIds)

    fun removeMember(contactId: String) = p2pChatService.removeGroupMember(groupId, contactId)

    val messages = messageDao.observeMessages(groupId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _draft = MutableStateFlow(draftStore.get(groupId))
    val draft: StateFlow<String> = _draft

    /** The `@partial` token currently being typed at the end of the draft, if any - drives the mention suggestion popup. */
    val mentionQuery: StateFlow<String?> = _draft.map { text ->
        val at = text.lastIndexOf('@')
        if (at == -1) return@map null
        val token = text.substring(at + 1)
        if (token.contains(' ') || token.contains('\n')) null else token
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun mentionSuggestions(): List<MentionSuggestion> {
        val query = mentionQuery.value ?: return emptyList()
        return members.value
            .filter { it.nickname.startsWith(query, ignoreCase = true) }
            .map { MentionSuggestion(it.contactId, it.nickname) }
    }

    fun onDraftChange(value: String) {
        _draft.value = value
        draftStore.set(groupId, value)
    }

    fun selectMention(suggestion: MentionSuggestion) {
        val text = _draft.value
        val at = text.lastIndexOf('@')
        if (at == -1) return
        _draft.value = text.substring(0, at) + "@" + suggestion.label + " "
    }

    fun send() {
        val text = _draft.value.trim()
        val staged = _pending.value
        if (text.isEmpty() && staged.isEmpty()) return
        warnIfOffline()
        if (text.isNotEmpty()) p2pChatService.sendGroupText(groupId, text)
        // Staged files stream straight off disk - a video or APK never sits in RAM.
        viewModelScope.launch(Dispatchers.IO) {
            staged.forEach { p2pChatService.sendGroupMedia(groupId, it.file, it.mimeType, it.kind, it.fileName) }
            clearPending()
        }
        _draft.value = ""
        draftStore.clear(groupId)
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

    /**
     * Stages a picked document/video straight off its content stream - copying to
     * the pending file in 64KB blocks, so a large attachment never sits in RAM.
     * (Edited photos still arrive as bytes via [stageAttachment]; those already
     * went through an in-memory editor and are JPEG-capped.)
     */
    fun stageAttachmentUri(uri: Uri, kind: PayloadKind) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(uri.toString().substringAfterLast('.', ""))
                ?: "application/octet-stream"
            val dir = File(context.cacheDir, "pending").apply { mkdirs() }
            val ext = mediaStorage.extensionFor(mimeType)
            val file = File(dir, "pending_${System.currentTimeMillis()}_${(0..9999).random()}.$ext")
            val staged = runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Nelze otevřít vybraný soubor")
            }.isSuccess
            if (!staged || file.length() <= 0) {
                runCatching { file.delete() }
                return@launch
            }
            _pending.value = _pending.value + PendingAttachment(file, mimeType, kind, displayNameOf(uri).takeIf { kind == PayloadKind.FILE })
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
            p2pChatService.sendGroupMedia(groupId, file, "audio/mp4", PayloadKind.VOICE, file.name, durationMs)
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
        return messageDao.searchInThread(groupId, escaped)
    }

    /** Wipes this group's conversation locally only - membership stays untouched. */
    fun clearChat() {
        viewModelScope.launch { messageDao.deleteAllForContact(groupId) }
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

    private fun warnIfOffline() {
        if (p2pChatService.i2pState.value == I2pState.CONNECTED) {
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

    fun leaveGroup() {
        p2pChatService.leaveGroup(groupId)
    }

    fun deleteGroup() {
        p2pChatService.deleteGroup(groupId)
    }
}
