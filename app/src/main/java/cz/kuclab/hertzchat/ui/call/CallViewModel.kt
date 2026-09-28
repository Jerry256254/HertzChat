package cz.kuclab.hertzchat.ui.call

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.data.db.ContactDao
import cz.kuclab.hertzchat.p2p.CallManager
import cz.kuclab.hertzchat.p2p.CallState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class CallViewModel @Inject constructor(
    val calls: CallManager,
    contactDao: ContactDao,
) : ViewModel() {
    val state: StateFlow<CallState> = calls.state
    val muted: StateFlow<Boolean> = calls.muted
    val speaker: StateFlow<Boolean> = calls.speaker
    val micLevel: StateFlow<Float> = calls.micLevel

    /** Live peer row for the current call - name and photo, whatever side started it. */
    val peerNickname: StateFlow<String> = calls.state.map { state ->
        val contactId = when (state) {
            is CallState.Outgoing -> state.contactId
            is CallState.Incoming -> state.contactId
            is CallState.Active -> state.contactId
            is CallState.Ended -> state.contactId
            CallState.Idle -> null
        }
        contactId?.let { contactDao.find(it)?.nickname }.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val peerAvatarPath: StateFlow<String?> = calls.state.map { state ->
        val contactId = when (state) {
            is CallState.Outgoing -> state.contactId
            is CallState.Incoming -> state.contactId
            is CallState.Active -> state.contactId
            is CallState.Ended -> state.contactId
            CallState.Idle -> null
        }
        contactId?.let { contactDao.find(it)?.avatarPath }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
