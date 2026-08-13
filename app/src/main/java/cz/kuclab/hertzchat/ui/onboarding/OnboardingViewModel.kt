package cz.kuclab.hertzchat.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.crypto.IdentityKeyManager
import cz.kuclab.hertzchat.data.repository.SettingsRepository
import cz.kuclab.hertzchat.locale.LANGUAGE_SYSTEM
import cz.kuclab.hertzchat.locale.LocalePrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val languageCode: String = LANGUAGE_SYSTEM,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val identityKeyManager: IdentityKeyManager,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state

    init {
        _state.value = _state.value.copy(languageCode = LocalePrefs.getLanguageCode(context) ?: LANGUAGE_SYSTEM)
    }

    fun onNicknameChange(value: String) {
        _state.value = _state.value.copy(nickname = value)
    }

    fun onAcceptTermsChange(value: Boolean) {
        _state.value = _state.value.copy(acceptedTerms = value)
    }

    /** Persists to both stores - caller (the Activity) is responsible for recreating to apply it immediately. */
    fun onLanguageChange(code: String) {
        _state.value = _state.value.copy(languageCode = code)
        LocalePrefs.setLanguageCode(context, code)
        viewModelScope.launch { settingsRepository.setLanguageCode(code) }
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
