package cz.kuclab.hertzchat.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.crypto.IdentityKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OnboardingState(
    val nickname: String = "",
    val acceptedTerms: Boolean = false,
    val creating: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val identityKeyManager: IdentityKeyManager,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state

    fun onNicknameChange(value: String) {
        _state.value = _state.value.copy(nickname = value)
    }

    fun onAcceptTermsChange(value: Boolean) {
        _state.value = _state.value.copy(acceptedTerms = value)
    }

    fun createIdentity(onFinished: () -> Unit) {
        if (_state.value.creating) return
        _state.value = _state.value.copy(creating = true)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                identityKeyManager.ensureIdentityAndPreKeys(_state.value.nickname)
            }
            _state.value = _state.value.copy(creating = false)
            onFinished()
        }
    }
}
