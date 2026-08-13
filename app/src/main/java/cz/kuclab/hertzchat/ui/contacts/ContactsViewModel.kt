package cz.kuclab.hertzchat.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.data.repository.IncomingFriendRequest
import cz.kuclab.hertzchat.data.repository.P2pChatService
import cz.kuclab.hertzchat.network.tor.HertzId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val p2pChatService: P2pChatService,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    val incomingRequests = p2pChatService.incomingRequests
    val torState = p2pChatService.torState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val bootstrapPercent = p2pChatService.bootstrapPercent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _addError = MutableStateFlow<String?>(null)
    val addError: StateFlow<String?> = _addError

    fun myHertzIdQrText(): String = json.encodeToString(p2pChatService.myHertzId())

    fun addByHertzId(text: String) {
        val id = runCatching { json.decodeFromString(HertzId.serializer(), text.trim()) }.getOrNull()
        if (id == null) {
            _addError.value = "Neplatné Hertz ID"
            return
        }
        _addError.value = null
        p2pChatService.sendFriendRequest(id)
    }

    fun respond(request: IncomingFriendRequest, accept: Boolean) {
        p2pChatService.respondFriendRequest(request, accept)
    }
}
