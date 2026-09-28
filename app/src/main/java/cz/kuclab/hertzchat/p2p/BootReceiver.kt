package cz.kuclab.hertzchat.p2p

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import cz.kuclab.hertzchat.crypto.IdentityKeyManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * After a reboot the I2P router would otherwise only start when the user next
 * opens the app, making the first message pay the full cold bootstrap (tunnel
 * builds plus netDb exploration, easily a minute or two). Starting the
 * foreground service at boot means the network is already warm when they do.
 * Best-effort by design: newer Androids may refuse a foreground-service start
 * from the background, in which case the periodic [RetryWakeWorker] (which
 * WorkManager persists across reboots) picks the service up within ~15 minutes
 * instead - either way strictly better than waiting for the next app open.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var identityKeyManager: IdentityKeyManager

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!identityKeyManager.hasIdentity) return
        // The service itself honours the "Být dosažitelný" toggle (stops when off).
        runCatching {
            val service = Intent(context, P2pForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(service)
            } else {
                context.startService(service)
            }
        }
    }
}
