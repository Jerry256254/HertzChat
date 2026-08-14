package cz.kuclab.hertzchat.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.data.db.ContactDao
import cz.kuclab.hertzchat.data.repository.IncomingFriendRequest
import cz.kuclab.hertzchat.data.repository.P2pChatService
import cz.kuclab.hertzchat.mistral.MistralKeyStore
import cz.kuclab.hertzchat.network.p2p.HertzId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val p2pChatService: P2pChatService,
    contactDao: ContactDao,
    mistralKeyStore: MistralKeyStore,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    val mistralEnabled = mistralKeyStore.enabled
    val showMistralContact = mistralKeyStore.showAssistantContact
    val contacts = contactDao.observeContacts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createGroup(name: String, memberContactIds: List<String>) {
        if (name.isBlank() || memberContactIds.isEmpty()) return
        p2pChatService.createGroup(name.trim(), memberContactIds)
    }

    val incomingRequests = p2pChatService.incomingRequests
    val i2pState = p2pChatService.i2pState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val bootstrapPercent = p2pChatService.bootstrapPercent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val i2pError = p2pChatService.i2pError.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val i2pDiagnostics = p2pChatService.i2pDiagnostics.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val lanPeerCount = p2pChatService.lanPeerCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun retryI2p() = p2pChatService.retryI2p()

    /** Null until I2P has opened our destination - the QR/ID isn't shareable before that. */
    val myHertzIdQrText: StateFlow<String?> = p2pChatService.i2pDestination
        .map { it?.let { json.encodeToString(p2pChatService.myHertzId()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _addError = MutableStateFlow<String?>(null)
    val addError: StateFlow<String?> = _addError

    private val _addSuccess = MutableStateFlow(false)
    val addSuccess: StateFlow<Boolean> = _addSuccess

    fun addByHertzId(text: String) {
        val id = runCatching { json.decodeFromString(HertzId.serializer(), text.trim()) }.getOrNull()
        if (id == null) {
            _addError.value = "Neplatné Hertz ID"
            return
        }
        _addError.value = null
        viewModelScope.launch {
            p2pChatService.sendFriendRequest(id).fold(
                onSuccess = {
                    _addSuccess.value = true
                    _addError.value = null
                },
                onFailure = { error -> _addError.value = error.message ?: "Žádost se nepodařilo odeslat" },
            )
        }
    }

    fun clearAddSuccess() {
        _addSuccess.value = false
    }

    fun respond(request: IncomingFriendRequest, accept: Boolean) {
        p2pChatService.respondFriendRequest(request, accept)
    }
}
