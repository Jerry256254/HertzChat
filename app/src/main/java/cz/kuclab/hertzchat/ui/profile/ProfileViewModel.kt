package cz.kuclab.hertzchat.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.crypto.IdentityKeyManager
import cz.kuclab.hertzchat.data.repository.P2pChatService
import cz.kuclab.hertzchat.media.MediaStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

    /** Last saved nickname - the draft in [_nickname] only commits on explicit save. */
    private val _committedNickname = MutableStateFlow(identityKeyManager.nickname)
    val committedNickname: StateFlow<String> = _committedNickname

    private val json = Json { ignoreUnknownKeys = true }

    val i2pError = p2pChatService.i2pError.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun retryI2p() = p2pChatService.retryI2p()

    /** Null until I2P has opened our destination - the QR/ID isn't shareable before that. */
    val myHertzIdQrText: StateFlow<String?> = p2pChatService.i2pDestination
        .map { it?.let { json.encodeToString(p2pChatService.myHertzId()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Draft only - typing never writes through, so half-typed names can't leak to contacts. */
    fun onNicknameChange(value: String) {
        _nickname.value = value
    }

    /** Commits the draft locally and pushes it to every contact in one sync. */
    fun saveNickname() {
        val value = _nickname.value.trim()
        if (value.isEmpty() || value == _committedNickname.value) return
        p2pChatService.updateMyNickname(value)
        _nickname.value = value
        _committedNickname.value = value
    }

    fun onAvatarPicked(jpegBytes: ByteArray) {
        p2pChatService.updateMyAvatar(jpegBytes)
        _avatarVersion.value += 1
    }
}
