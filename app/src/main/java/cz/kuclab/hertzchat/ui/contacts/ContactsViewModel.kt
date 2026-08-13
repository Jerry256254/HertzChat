package cz.kuclab.hertzchat.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.crypto.IdentityKeyManager
import cz.kuclab.hertzchat.data.db.ContactDao
import cz.kuclab.hertzchat.data.repository.IncomingFriendRequest
import cz.kuclab.hertzchat.data.repository.P2pChatService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DiscoveredPeer(val contactId: String, val nickname: String, val alreadyContact: Boolean)

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val p2pChatService: P2pChatService,
    contactDao: ContactDao,
    val identityKeyManager: IdentityKeyManager,
) : ViewModel() {

    val discoveredPeers = combine(p2pChatService.onlinePresence, contactDao.observeContacts()) { presence, contacts ->
        val known = contacts.map { it.contactId }.toSet()
        presence.map { DiscoveredPeer(it.contactId, it.nickname, it.contactId in known) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomingRequests = p2pChatService.incomingRequests

    fun refresh() {
        p2pChatService.start()
    }

    fun sendFriendRequest(contactId: String) {
        p2pChatService.sendFriendRequest(contactId)
    }

    fun respond(request: IncomingFriendRequest, accept: Boolean) {
        p2pChatService.respondFriendRequest(request, accept)
    }
}
