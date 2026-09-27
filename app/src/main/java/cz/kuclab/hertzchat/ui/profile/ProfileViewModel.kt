package cz.kuclab.hertzchat.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.crypto.IdentityKeyManager
import cz.kuclab.hertzchat.data.repository.P2pChatService
import cz.kuclab.hertzchat.media.MediaStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val identityKeyManager: IdentityKeyManager,
    private val mediaStorage: MediaStorage,
    private val p2pChatService: P2pChatService,
) : ViewModel() {

    val contactId: String = identityKeyManager.contactId()

    private val _nickname = MutableStateFlow(identityKeyManager.nickname)
    val nickname: StateFlow<String> = _nickname

    fun avatarFile(): File? = mediaStorage.selfAvatarFile().takeIf { it.exists() }

    // Avatar picks overwrite the same stable file path, so this counter is
    // what actually drives recomposition/re-fetch of the image - the path
    // string alone never changes.
    private val _avatarVersion = MutableStateFlow(0)
    val avatarVersion: StateFlow<Int> = _avatarVersion

    private var nicknameBroadcastJob: Job? = null

    fun onNicknameChange(value: String) {
        _nickname.value = value
        identityKeyManager.nickname = value
        // Keystroke-level saves, but the network push waits until typing settles,
        // so every contact's copy of our nickname follows without per-letter spam.
        nicknameBroadcastJob?.cancel()
        nicknameBroadcastJob = viewModelScope.launch {
            delay(2000)
            if (value.isNotBlank()) p2pChatService.broadcastProfile()
        }
    }

    fun onAvatarPicked(jpegBytes: ByteArray) {
        p2pChatService.updateMyAvatar(jpegBytes)
        _avatarVersion.value += 1
    }
}
