package cz.kuclab.hertzchat.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.BuildConfig
import cz.kuclab.hertzchat.data.db.ContactDao
import cz.kuclab.hertzchat.data.repository.AppSettings
import cz.kuclab.hertzchat.data.repository.SettingsRepository
import cz.kuclab.hertzchat.media.MediaStorage
import cz.kuclab.hertzchat.p2p.P2pForegroundService
import cz.kuclab.hertzchat.update.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data object UpToDate : UpdateCheckState
    data class Available(val version: String, val url: String) : UpdateCheckState
    data class Error(val message: String) : UpdateCheckState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val contactDao: ContactDao,
    private val mediaStorage: MediaStorage,
    private val updateChecker: UpdateChecker,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val settings = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val blockedContacts = contactDao.observeBlocked().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _mediaBytes = MutableStateFlow(mediaStorage.mediaStorageBytes())
    val mediaBytes: StateFlow<Long> = _mediaBytes

    val currentVersion: String = BuildConfig.VERSION_NAME

    private val _updateCheckState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState

    fun setDiscoverable(value: Boolean) {
        viewModelScope.launch { settingsRepository.setDiscoverable(value) }
        if (value) {
            // The foreground service stops itself the moment discoverable goes false, so
            // turning it back on here needs to actively restart it, not just flip a flag.
            val intent = Intent(context, P2pForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun setMediaQuality(value: String) = viewModelScope.launch { settingsRepository.setMediaQuality(value) }
    fun setNotificationsEnabled(value: Boolean) = viewModelScope.launch { settingsRepository.setNotificationsEnabled(value) }
    fun setThemeMode(value: String) = viewModelScope.launch { settingsRepository.setThemeMode(value) }
    fun setAutoAcceptFriendRequests(value: Boolean) = viewModelScope.launch { settingsRepository.setAutoAcceptFriendRequests(value) }
    fun unblock(contactId: String) = viewModelScope.launch { contactDao.setBlocked(contactId, false) }

    fun clearMediaCache() {
        mediaStorage.clearMedia()
        _mediaBytes.value = mediaStorage.mediaStorageBytes()
    }

    fun checkForUpdates() {
        _updateCheckState.value = UpdateCheckState.Checking
        viewModelScope.launch {
            updateChecker.checkLatestVersion().fold(
                onSuccess = { info ->
                    _updateCheckState.value = if (isNewerVersion(info.latestVersion, currentVersion)) {
                        UpdateCheckState.Available(info.latestVersion, info.releaseUrl)
                    } else {
                        UpdateCheckState.UpToDate
                    }
                },
                onFailure = { e ->
                    _updateCheckState.value = UpdateCheckState.Error(e.message ?: "Kontrolu se nepodařilo provést")
                },
            )
        }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val localParts = local.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }
}
