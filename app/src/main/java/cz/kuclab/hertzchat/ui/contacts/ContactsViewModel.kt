package cz.kuclab.hertzchat.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.data.repository.IncomingFriendRequest
import cz.kuclab.hertzchat.data.repository.P2pChatService
import cz.kuclab.hertzchat.mistral.MistralKeyStore
import cz.kuclab.hertzchat.network.tor.HertzId
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
    mistralKeyStore: MistralKeyStore,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    val mistralEnabled = mistralKeyStore.enabled
    val showMistralContact = mistralKeyStore.showAssistantContact

    val incomingRequests = p2pChatService.incomingRequests
    val torState = p2pChatService.torState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val bootstrapPercent = p2pChatService.bootstrapPercent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val torError = p2pChatService.torError.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun retryTor() = p2pChatService.retryTor()

    /** Null until Tor has published our onion service - the QR/ID isn't shareable before that. */
    val myHertzIdQrText: StateFlow<String?> = p2pChatService.onionAddress
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
