package cz.kuclab.hertzchat.p2p

import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Safety net for the case Android has killed [P2pForegroundService] outright
 * (severe memory pressure, user force-stopped the app, etc.) - fires roughly
 * every 15 minutes (the shortest interval WorkManager allows for periodic
 * work) and simply restarts the service, whose own retry loop then takes
 * over redelivering anything still queued. Normal operation doesn't depend
 * on this - the foreground service's internal loop retries far more often
 * while it's alive.
 */
@HiltWorker
class RetryWakeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val intent = android.content.Intent(applicationContext, P2pForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(intent)
        } else {
            applicationContext.startService(intent)
        }
        return Result.success()
    }
}
