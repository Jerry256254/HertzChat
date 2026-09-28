package cz.kuclab.hertzchat.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.crypto.IdentityKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class RootViewModel @Inject constructor(
    private val identityKeyManager: IdentityKeyManager,
    callManager: cz.kuclab.hertzchat.p2p.CallManager,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination

    /** A ringing incoming call, if any - navigation surfaces the call screen for it on its own. */
    val incomingCall: StateFlow<cz.kuclab.hertzchat.p2p.CallState.Incoming?> =
        callManager.state.map { it as? cz.kuclab.hertzchat.p2p.CallState.Incoming }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            val hasIdentity = withContext(Dispatchers.IO) { identityKeyManager.hasIdentity }
            _startDestination.value = if (hasIdentity) Routes.CHAT_LIST else Routes.ONBOARDING
        }
    }
}
