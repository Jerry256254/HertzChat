package cz.kuclab.hertzchat.ui.groupchat

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.data.db.GroupDao
import cz.kuclab.hertzchat.data.db.GroupMemberDao
import cz.kuclab.hertzchat.data.db.GroupMemberEntity
import cz.kuclab.hertzchat.data.db.MessageDao
import cz.kuclab.hertzchat.data.model.PayloadKind
import cz.kuclab.hertzchat.data.repository.DraftStore
import cz.kuclab.hertzchat.data.repository.P2pChatService
import cz.kuclab.hertzchat.mistral.MISTRAL_ASSISTANT_CONTACT_ID
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

data class MentionSuggestion(val id: String, val label: String)

@HiltViewModel
class GroupChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    messageDao: MessageDao,
    groupDao: GroupDao,
    groupMemberDao: GroupMemberDao,
    private val p2pChatService: P2pChatService,
    private val draftStore: DraftStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val groupId: String = checkNotNull(savedStateHandle["groupId"])

    val groupName = groupDao.observeGroup(groupId)
        .map { it?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val members = groupMemberDao.observeMembers(groupId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<GroupMemberEntity>())

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
        val fromMembers = members.value
            .filter { it.nickname.startsWith(query, ignoreCase = true) }
            .map { MentionSuggestion(it.contactId, it.nickname) }
        val mistral = listOf(MentionSuggestion(MISTRAL_ASSISTANT_CONTACT_ID, "Mistral"))
            .filter { it.label.startsWith(query, ignoreCase = true) }
        return fromMembers + mistral
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
        if (text.isEmpty()) return
        p2pChatService.sendGroupText(groupId, text)
        _draft.value = ""
        draftStore.clear(groupId)
    }

    fun sendVideo(uri: Uri) = sendPickedMedia(uri, PayloadKind.VIDEO)

    fun sendFile(uri: Uri) = sendPickedMedia(uri, PayloadKind.FILE)

    fun sendImageBytes(bytes: ByteArray) {
        p2pChatService.sendGroupMedia(groupId, bytes, "image/jpeg", PayloadKind.IMAGE, fileName = null)
    }

    fun sendVoice(file: File, durationMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = file.readBytes()
            p2pChatService.sendGroupMedia(groupId, bytes, "audio/mp4", PayloadKind.VOICE, file.name, durationMs)
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
            p2pChatService.sendGroupMedia(groupId, bytes, mimeType, kind, fileName = displayNameOf(uri).takeIf { kind == PayloadKind.FILE })
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
}
