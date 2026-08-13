package cz.kuclab.hertzchat.ui.navigation

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

@HiltViewModel
class RootViewModel @Inject constructor(
    private val identityKeyManager: IdentityKeyManager,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination

    init {
        viewModelScope.launch {
            val hasIdentity = withContext(Dispatchers.IO) { identityKeyManager.hasIdentity }
            _startDestination.value = if (hasIdentity) Routes.CHAT_LIST else Routes.ONBOARDING
        }
    }
}
