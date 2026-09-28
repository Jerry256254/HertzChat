package cz.kuclab.hertzchat.ui.chatlist

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contract for the chat-list order: pinned rows float above everything in
 * their hand-set order, unpinned rows follow by recency. A regression here
 * silently buries pinned chats or scrambles a hand-arranged section.
 */
class ChatListSortTest {

    private fun item(id: String, pinned: Boolean = false, pinOrder: Int = 0, lastMessageAt: Long? = null) =
        ChatListItem(
            contactId = id,
            nickname = id,
            avatarPath = null,
            pinned = pinned,
            pinOrder = pinOrder,
            lastMessagePreview = null,
            lastMessageAt = lastMessageAt,
        )

    @Test
    fun `pinned rows come first in pinOrder regardless of recency`() {
        val sorted = sortChatListItems(
            listOf(
                item("recent-unpinned", lastMessageAt = 300L),
                item("pin-second", pinned = true, pinOrder = 2, lastMessageAt = 100L),
                item("pin-first", pinned = true, pinOrder = 0, lastMessageAt = 50L),
                item("old-unpinned", lastMessageAt = 10L),
            ),
        ).map { it.contactId }
        assertEquals(listOf("pin-first", "pin-second", "recent-unpinned", "old-unpinned"), sorted)
    }

    @Test
    fun `unpinned rows sort by recency with idle threads last`() {
        val sorted = sortChatListItems(
            listOf(
                item("never", lastMessageAt = null),
                item("old", lastMessageAt = 5L),
                item("new", lastMessageAt = 99L),
            ),
        ).map { it.contactId }
        assertEquals(listOf("new", "old", "never"), sorted)
    }

    @Test
    fun `equal pinOrder falls back to recency`() {
        val sorted = sortChatListItems(
            listOf(
                item("pin-old", pinned = true, pinOrder = 0, lastMessageAt = 10L),
                item("pin-new", pinned = true, pinOrder = 0, lastMessageAt = 90L),
            ),
        ).map { it.contactId }
        assertEquals(listOf("pin-new", "pin-old"), sorted)
    }
}
