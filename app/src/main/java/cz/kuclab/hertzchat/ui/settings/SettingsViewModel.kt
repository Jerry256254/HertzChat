package cz.kuclab.hertzchat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.data.db.ContactDao
import cz.kuclab.hertzchat.data.repository.AppSettings
import cz.kuclab.hertzchat.data.repository.SettingsRepository
import cz.kuclab.hertzchat.media.MediaStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val contactDao: ContactDao,
    private val mediaStorage: MediaStorage,
) : ViewModel() {

    val settings = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val blockedContacts = contactDao.observeBlocked().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _mediaBytes = MutableStateFlow(mediaStorage.mediaStorageBytes())
    val mediaBytes: StateFlow<Long> = _mediaBytes

    fun setDiscoverable(value: Boolean) = viewModelScope.launch { settingsRepository.setDiscoverable(value) }
    fun setMediaQuality(value: String) = viewModelScope.launch { settingsRepository.setMediaQuality(value) }
    fun setNotificationsEnabled(value: Boolean) = viewModelScope.launch { settingsRepository.setNotificationsEnabled(value) }
    fun setThemeMode(value: String) = viewModelScope.launch { settingsRepository.setThemeMode(value) }
    fun unblock(contactId: String) = viewModelScope.launch { contactDao.setBlocked(contactId, false) }

    fun clearMediaCache() {
        mediaStorage.clearMedia()
        _mediaBytes.value = mediaStorage.mediaStorageBytes()
    }
}
