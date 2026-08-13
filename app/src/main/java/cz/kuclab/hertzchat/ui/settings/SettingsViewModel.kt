package cz.kuclab.hertzchat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.data.db.ContactDao
import cz.kuclab.hertzchat.data.repository.AppSettings
import cz.kuclab.hertzchat.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val contactDao: ContactDao,
) : ViewModel() {

    val settings = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val blockedContacts = contactDao.observeBlocked().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setDiscoverable(value: Boolean) = viewModelScope.launch { settingsRepository.setDiscoverable(value) }
    fun setAutoDownloadMedia(value: Boolean) = viewModelScope.launch { settingsRepository.setAutoDownloadMedia(value) }
    fun setMediaQuality(value: String) = viewModelScope.launch { settingsRepository.setMediaQuality(value) }
    fun unblock(contactId: String) = viewModelScope.launch { contactDao.setBlocked(contactId, false) }
}
