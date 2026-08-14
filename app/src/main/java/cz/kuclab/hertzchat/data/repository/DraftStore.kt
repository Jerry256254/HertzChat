package cz.kuclab.hertzchat.data.repository

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers half-written messages per conversation, so leaving a chat (or the app being
 * killed in the background) doesn't throw away what you typed.
 *
 * Plain [android.content.SharedPreferences] rather than DataStore or Room on purpose:
 * the draft has to be readable *synchronously* while the chat screen's ViewModel is
 * being constructed, otherwise the field paints empty for a frame and then fills in,
 * which looks like the text was lost and then came back. Drafts are also not worth
 * putting in the encrypted database - anything actually sent lives there already, and a
 * draft is discarded the moment it is.
 */
@Singleton
class DraftStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("hertzchat_drafts", Context.MODE_PRIVATE)

    fun get(threadId: String): String = prefs.getString(threadId, "").orEmpty()

    fun set(threadId: String, text: String) {
        prefs.edit {
            if (text.isBlank()) remove(threadId) else putString(threadId, text)
        }
    }

    fun clear(threadId: String) = set(threadId, "")
}
