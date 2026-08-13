package cz.kuclab.hertzchat.p2p

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import cz.kuclab.hertzchat.data.repository.P2pChatService
import cz.kuclab.hertzchat.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "hertzchat_p2p"
private const val NOTIFICATION_ID = 1

/**
 * Keeps the signaling connection (and any active WebRTC data channels) alive
 * while the app is in use, so friends can reach you and in-flight messages
 * don't get dropped because Android suspended the process in the background.
 */
@AndroidEntryPoint
class P2pForegroundService : Service() {

    @Inject lateinit var p2pChatService: P2pChatService
    @Inject lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
        startForeground(NOTIFICATION_ID, buildNotification())
        scope.launch {
            val settings = settingsRepository.settings.first()
            if (!settings.discoverable) return@launch
            p2pChatService.relayUrl = settings.relayUrl
            p2pChatService.turnUrl = settings.turnUrl.takeIf { it.isNotBlank() }
            p2pChatService.turnUsername = settings.turnUsername
            p2pChatService.turnPassword = settings.turnPassword
            p2pChatService.start()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        p2pChatService.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hertz Chat je online")
            .setContentText("Naslouchá příchozím zprávám a žádostem o přátelství")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "P2P připojení", NotificationManager.IMPORTANCE_MIN),
        )
    }
}
