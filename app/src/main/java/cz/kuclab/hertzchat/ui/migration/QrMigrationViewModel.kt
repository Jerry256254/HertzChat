package cz.kuclab.hertzchat.ui.migration

import android.content.Context
import androidx.lifecycle.ViewModel
import cz.kuclab.hertzchat.crypto.IdentityKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltViewModel
class QrMigrationViewModel @Inject constructor(
    private val identityKeyManager: IdentityKeyManager,
) : ViewModel() {

    fun exportPayload(): String = identityKeyManager.exportIdentityJson()

    fun importPayload(json: String): Boolean = runCatching {
        identityKeyManager.importIdentityJson(json)
    }.isSuccess

    fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        exitProcess(0)
    }
}
