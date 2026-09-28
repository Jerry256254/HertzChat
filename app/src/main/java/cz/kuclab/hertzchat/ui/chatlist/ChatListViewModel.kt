package cz.kuclab.hertzchat.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.crypto.IdentityKeyManager
import cz.kuclab.hertzchat.data.db.ContactDao
import cz.kuclab.hertzchat.data.db.GroupDao
import cz.kuclab.hertzchat.data.db.MessageDao
import cz.kuclab.hertzchat.data.db.ThreadReadStateDao
import cz.kuclab.hertzchat.data.repository.IncomingFriendRequest
import cz.kuclab.hertzchat.data.repository.P2pChatService
import cz.kuclab.hertzchat.media.MediaStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ChatListItemKind { CONTACT, GROUP }

data class ChatListItem(
    val contactId: String,
    val nickname: String,
    val avatarPath: String?,
    val pinned: Boolean,
    val pinOrder: Int = 0,
    val lastMessagePreview: String?,
    val lastMessageAt: Long?,
    val kind: ChatListItemKind = ChatListItemKind.CONTACT,
    /** True for the auto-added contact that is this device's own identity - see P2pChatService.ensureSelfContact(). */
    val isSelf: Boolean = false,
    /** Incoming messages newer than the last-seen watermark - drives the unread dot. */
    val unreadCount: Int = 0,
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val contactDao: ContactDao,
    private val messageDao: MessageDao,
    private val groupDao: GroupDao,
    private val readStateDao: ThreadReadStateDao,
    private val p2pChatService: P2pChatService,
    private val identityKeyManager: IdentityKeyManager,
    private val mediaStorage: MediaStorage,
) : ViewModel() {

    val relayState = p2pChatService.relayState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val relayCount = p2pChatService.relayCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Own photo for the profile button - re-read whenever contacts change so a new avatar appears without restart. */
    val myAvatarPath = contactDao.observeContacts()
        .map { mediaStorage.selfAvatarFile().takeIf { it.exists() }?.absolutePath }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), mediaStorage.selfAvatarFile().takeIf { it.exists() }?.absolutePath)

    val incomingRequests = p2pChatService.incomingRequests

    fun respond(request: IncomingFriendRequest, accept: Boolean) {
        p2pChatService.respondFriendRequest(request, accept)
    }

    val items = combine(
        contactDao.observeContacts(),
        groupDao.observeGroups(),
        messageDao.observeRecent(),
        readStateDao.observeAll(),
    ) { contacts, groups, recent, readStates ->
        val seenByThread = readStates.associate { it.threadId to it.lastSeenAt }
        val unreadByThread = recent
            .filter { !it.fromMe && (seenByThread[it.contactId] ?: 0L) < it.timestamp }
            .groupingBy { it.contactId }
            .eachCount()
        // Newest message per thread, straight from the observed recent list - no
        // per-row queries, and outgoing sends refresh the preview the same way
        // incoming ones do.
        val lastByThread = recent.groupBy { it.contactId }
            .mapValues { (_, messages) -> messages.maxByOrNull { it.timestamp } }

        val myContactId = identityKeyManager.contactId()
        val selfAvatarPath = mediaStorage.selfAvatarFile().takeIf { it.exists() }?.absolutePath
        val contactItems = contacts.map { contact ->
            val last = lastByThread[contact.contactId]
            val isSelf = contact.contactId == myContactId
            ChatListItem(
                contactId = contact.contactId,
                nickname = contact.nickname,
                // Own photo is already on this device - showing it never depends on the network round-tripping
                // round-tripping an AVATAR transfer to yourself.
                avatarPath = if (isSelf) selfAvatarPath else contact.avatarPath,
                pinned = contact.pinned,
                pinOrder = contact.pinOrder,
                lastMessagePreview = last?.let { previewFor(it) },
                lastMessageAt = last?.timestamp,
                isSelf = isSelf,
                unreadCount = unreadByThread[contact.contactId] ?: 0,
            )
        }

        val groupItems = groups.map { group ->
            val last = lastByThread[group.groupId]
            ChatListItem(
                contactId = group.groupId,
                nickname = group.name,
                avatarPath = null,
                pinned = group.pinned,
                pinOrder = group.pinOrder,
                lastMessagePreview = last?.let { previewFor(it) },
                lastMessageAt = last?.timestamp,
                kind = ChatListItemKind.GROUP,
                unreadCount = unreadByThread[group.groupId] ?: 0,
            )
        }

        // Pinned first (in hand-set order), then most-recently-active (incoming or
        // outgoing alike) - a new message always floats its thread to the top,
        // just under pinned rows.
        // (The Hertz Agent lives in its own button above "Nový chat", not as a row.)
        sortChatListItems(contactItems + groupItems)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun togglePin(item: ChatListItem) {
        viewModelScope.launch {
            if (!item.pinned) {
                // A fresh pin lands at the bottom of the pinned section - the user
                // moves it up from there if they want it higher.
                val bottom = items.value.filter { it.pinned }.maxOfOrNull { it.pinOrder }?.plus(1) ?: 0
                setPinOrder(item, bottom)
            }
            when (item.kind) {
                ChatListItemKind.GROUP -> groupDao.setPinned(item.contactId, !item.pinned)
                ChatListItemKind.CONTACT -> contactDao.setPinned(item.contactId, !item.pinned)
            }
        }
    }

    /** Swaps a pinned row with the pinned neighbour above/below it; no-op at the section edges. */
    fun movePinned(item: ChatListItem, up: Boolean) {
        viewModelScope.launch {
            val pinned = items.value.filter { it.pinned }
            val index = pinned.indexOfFirst { it.contactId == item.contactId && it.kind == item.kind }
            val neighbour = pinned.getOrNull(if (up) index - 1 else index + 1) ?: return@launch
            if (index == -1) return@launch
            setPinOrder(item, neighbour.pinOrder)
            setPinOrder(neighbour, item.pinOrder)
        }
    }

    private suspend fun setPinOrder(item: ChatListItem, order: Int) {
        when (item.kind) {
            ChatListItemKind.GROUP -> groupDao.setPinOrder(item.contactId, order)
            ChatListItemKind.CONTACT -> contactDao.setPinOrder(item.contactId, order)
        }
    }

    fun block(contactId: String) {
        viewModelScope.launch { contactDao.setBlocked(contactId, true) }
    }
}

/**
 * One-line preview of a thread's newest message: the text itself, or a label for
 * media (which carries no text) - prefixed with who it came from.
 */
internal fun previewFor(message: cz.kuclab.hertzchat.data.db.MessageEntity): String {
    val body = when (message.type) {
        cz.kuclab.hertzchat.data.db.MessageType.TEXT -> message.text.orEmpty().lines().firstOrNull().orEmpty()
        cz.kuclab.hertzchat.data.db.MessageType.IMAGE -> "Fotka"
        cz.kuclab.hertzchat.data.db.MessageType.VIDEO -> "Video"
        cz.kuclab.hertzchat.data.db.MessageType.VOICE -> "Hlasová zpráva"
        cz.kuclab.hertzchat.data.db.MessageType.FILE -> message.mediaFileName ?: "Soubor"
    }
    return if (message.fromMe) "Ty: $body" else body
}

/**
 * Chat-list order, extracted pure so the contract stays pinned by [ChatListSortTest]:
 * pinned rows first in hand-set [ChatListItem.pinOrder], then most-recently-active.
 */
internal fun sortChatListItems(items: List<ChatListItem>): List<ChatListItem> =
    items.sortedWith(
        compareByDescending<ChatListItem> { it.pinned }
            .thenBy { it.pinOrder }
            .thenByDescending { it.lastMessageAt ?: 0L },
    )
