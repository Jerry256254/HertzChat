package cz.kuclab.hertzchat.p2p

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Lets [MessageNotifier] tell "the user is already looking at this" from "the user is
 * somewhere else" - without it, a message notification fired for every incoming message
 * unconditionally, including the thread already open on screen.
 *
 * Two independent pieces of state, both required to suppress a notification: the thread
 * has to be the one currently composed *and* the app has to actually be in front of the
 * user. Composables aren't disposed just because the app is backgrounded (the Activity
 * keeps its Compose tree while paused), so tracking the open thread alone would keep
 * suppressing notifications for a chat left open behind the lock screen.
 */
@Singleton
class ActiveChatTracker @Inject constructor() {
    val activeThreadId = MutableStateFlow<String?>(null)
    val appInForeground = MutableStateFlow(false)

    fun isThreadVisible(threadId: String): Boolean = appInForeground.value && activeThreadId.value == threadId
}
