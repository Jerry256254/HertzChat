package cz.kuclab.hertzchat.ui.profile

import androidx.lifecycle.ViewModel
import cz.kuclab.hertzchat.crypto.IdentityKeyManager
import cz.kuclab.hertzchat.data.repository.P2pChatService
import cz.kuclab.hertzchat.media.MediaStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

    fun onNicknameChange(value: String) {
        _nickname.value = value
        identityKeyManager.nickname = value
    }

    fun onAvatarPicked(jpegBytes: ByteArray) {
        p2pChatService.updateMyAvatar(jpegBytes)
        _avatarVersion.value += 1
    }
}
