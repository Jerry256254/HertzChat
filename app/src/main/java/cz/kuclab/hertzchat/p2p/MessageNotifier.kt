package cz.kuclab.hertzchat.p2p

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import cz.kuclab.hertzchat.MainActivity
import cz.kuclab.hertzchat.crypto.IdentityKeyManager
import cz.kuclab.hertzchat.data.db.ContactDao
import cz.kuclab.hertzchat.data.db.GroupDao
import cz.kuclab.hertzchat.data.db.MessageEntity
import cz.kuclab.hertzchat.data.db.MessageType
import cz.kuclab.hertzchat.data.repository.ChatServiceEvent
import cz.kuclab.hertzchat.data.repository.P2pChatService
import cz.kuclab.hertzchat.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val MESSAGE_CHANNEL_ID = "hertzchat_messages"

/** Turns incoming messages / friend requests into an actual heads-up notification - the foreground service alone is deliberately silent. */
@Singleton
class MessageNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactDao: ContactDao,
    private val groupDao: GroupDao,
    private val identityKeyManager: IdentityKeyManager,
    private val settingsRepository: SettingsRepository,
    private val activeChatTracker: ActiveChatTracker,
) {
    fun start(scope: CoroutineScope, p2pChatService: P2pChatService) {
        createChannelIfNeeded()
        scope.launch {
            p2pChatService.events.collect { event ->
                if (!settingsRepository.settings.first().notificationsEnabled) return@collect
                when (event) {
                    is ChatServiceEvent.MessageReceived -> notifyMessage(event.threadId, event.message)
                    is ChatServiceEvent.FriendRequestReceived -> notifyFriendRequest(event.request.nickname)
                }
            }
        }
    }

    private suspend fun notifyMessage(threadId: String, message: MessageEntity) {
        if (message.fromMe) return
        if (activeChatTracker.isThreadVisible(threadId)) return
        val group = groupDao.find(threadId)
        val senderLabel = when {
            message.fromAssistant -> "Mistral AI"
            group != null -> message.senderContactId?.let { contactDao.find(it)?.nickname } ?: "Neznámý"
            else -> contactDao.find(threadId)?.nickname ?: return
        }
        val preview = when (message.type) {
            MessageType.TEXT -> message.text.orEmpty()
            MessageType.IMAGE -> "📷 Obrázek"
            MessageType.VIDEO -> "🎥 Video"
            MessageType.VOICE -> "🎤 Hlasová zpráva"
            MessageType.FILE -> "📎 Soubor"
        }

        val mentionedMe = message.mentionedContactIds?.split(",")?.contains(identityKeyManager.contactId()) == true
        val title = when {
            mentionedMe && group != null -> "$senderLabel tě zmínil(a) v ${group.name}"
            group != null -> "$senderLabel · ${group.name}"
            else -> senderLabel
        }
        show(threadId.hashCode() xor message.messageId.hashCode(), title, preview)
    }

    private fun notifyFriendRequest(nickname: String) {
        show(("friend_request_$nickname").hashCode(), "Nová žádost o přátelství", "$nickname tě chce přidat")
    }

    private fun show(id: Int, title: String, text: String) {
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, id, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(id, notification)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(MESSAGE_CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(MESSAGE_CHANNEL_ID, "Zprávy a žádosti", NotificationManager.IMPORTANCE_HIGH),
        )
    }
}
