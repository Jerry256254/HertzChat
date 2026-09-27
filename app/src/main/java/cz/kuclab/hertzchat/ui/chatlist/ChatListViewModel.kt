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

    val i2pState = p2pChatService.i2pState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val bootstrapPercent = p2pChatService.bootstrapPercent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val bootstrapLabel = p2pChatService.bootstrapLabel.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val i2pError = p2pChatService.i2pError.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun retryI2p() = p2pChatService.retryI2p()

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
        messageDao.observeRecentIncoming(),
        readStateDao.observeAll(),
    ) { contacts, groups, recentIncoming, readStates ->
        val seenByThread = readStates.associate { it.threadId to it.lastSeenAt }
        val unreadByThread = recentIncoming
            .filter { (seenByThread[it.contactId] ?: 0L) < it.timestamp }
            .groupingBy { it.contactId }
            .eachCount()

        val myContactId = identityKeyManager.contactId()
        val selfAvatarPath = mediaStorage.selfAvatarFile().takeIf { it.exists() }?.absolutePath
        val contactItems = contacts.map { contact ->
            val last = messageDao.lastMessage(contact.contactId)
            val isSelf = contact.contactId == myContactId
            ChatListItem(
                contactId = contact.contactId,
                nickname = contact.nickname,
                // Own photo is already on this device - showing it never depends on I2P
                // round-tripping an AVATAR transfer to yourself.
                avatarPath = if (isSelf) selfAvatarPath else contact.avatarPath,
                pinned = contact.pinned,
                lastMessagePreview = last?.text,
                lastMessageAt = last?.timestamp,
                isSelf = isSelf,
                unreadCount = unreadByThread[contact.contactId] ?: 0,
            )
        }

        val groupItems = groups.map { group ->
            val last = messageDao.lastMessage(group.groupId)
            ChatListItem(
                contactId = group.groupId,
                nickname = group.name,
                avatarPath = null,
                pinned = group.pinned,
                lastMessagePreview = last?.text,
                lastMessageAt = last?.timestamp,
                kind = ChatListItemKind.GROUP,
                unreadCount = unreadByThread[group.groupId] ?: 0,
            )
        }

        // Pinned first, then most-recently-active (incoming or outgoing alike) -
        // a new message always floats its thread to the top, just under pinned rows.
        // (The Hertz Agent lives in its own button above "Nový chat", not as a row.)
        (contactItems + groupItems)
            .sortedWith(compareByDescending<ChatListItem> { it.pinned }.thenByDescending { it.lastMessageAt ?: 0L })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun togglePin(item: ChatListItem) {
        viewModelScope.launch {
            when (item.kind) {
                ChatListItemKind.GROUP -> groupDao.setPinned(item.contactId, !item.pinned)
                ChatListItemKind.CONTACT -> contactDao.setPinned(item.contactId, !item.pinned)
            }
        }
    }

    fun block(contactId: String) {
        viewModelScope.launch { contactDao.setBlocked(contactId, true) }
    }
}
