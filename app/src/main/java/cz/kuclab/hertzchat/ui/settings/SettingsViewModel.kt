package cz.kuclab.hertzchat.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.BuildConfig
import cz.kuclab.hertzchat.data.repository.AppSettings
import cz.kuclab.hertzchat.data.repository.SettingsRepository
import cz.kuclab.hertzchat.locale.LocalePrefs
import cz.kuclab.hertzchat.media.MediaStorage
import cz.kuclab.hertzchat.p2p.P2pForegroundService
import cz.kuclab.hertzchat.update.UpdateChecker
import cz.kuclab.hertzchat.update.UpdateInstaller
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
    data class Available(val version: String, val url: String, val apkUrl: String?) : UpdateCheckState
    /** APK is streaming in; [progress] is 0..1, negative while the size is unknown. */
    data class Downloading(val version: String, val progress: Float) : UpdateCheckState
    /** The one-time "install unknown apps" toggle is still off - nothing was downloaded yet. */
    data class InstallBlocked(val version: String, val apkUrl: String) : UpdateCheckState
    data class Error(val message: String) : UpdateCheckState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val mediaStorage: MediaStorage,
    private val updateChecker: UpdateChecker,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val settings = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

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

    /** Persists the choice to both stores - the reactive DataStore copy and the fast synchronous one [MainActivity][cz.kuclab.hertzchat.MainActivity] reads at cold start. The caller is responsible for recreating the Activity to apply it immediately. */
    fun setLanguageCode(value: String) {
        LocalePrefs.setLanguageCode(context, value)
        viewModelScope.launch { settingsRepository.setLanguageCode(value) }
    }

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
                        UpdateCheckState.Available(info.latestVersion, info.releaseUrl, info.apkUrl)
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

    fun downloadAndInstall(apkUrl: String, version: String) {
        if (!UpdateInstaller.canInstall(context)) {
            _updateCheckState.value = UpdateCheckState.InstallBlocked(version, apkUrl)
            return
        }
        _updateCheckState.value = UpdateCheckState.Downloading(version, 0f)
        viewModelScope.launch {
            val dest = java.io.File(java.io.File(context.cacheDir, "updates").apply { mkdirs() }, "hertzchat-$version.apk")
            UpdateInstaller.downloadApk(apkUrl, dest) { progress ->
                _updateCheckState.value = UpdateCheckState.Downloading(version, progress)
            }.fold(
                onSuccess = { file ->
                    UpdateInstaller.installApk(context, file)
                    _updateCheckState.value = UpdateCheckState.Idle
                },
                onFailure = { e ->
                    _updateCheckState.value = UpdateCheckState.Error("Stažení selhalo: ${e.message ?: "neznámá chyba"}")
                },
            )
        }
    }

    fun openInstallSettings() = UpdateInstaller.openUnknownSourcesSettings(context)

    private fun isNewerVersion(remote: String, local: String): Boolean =
        cz.kuclab.hertzchat.update.isNewerVersion(remote, local)
}
