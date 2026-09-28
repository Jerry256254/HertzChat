package cz.kuclab.hertzchat.ui.chatlist

import cz.kuclab.hertzchat.data.db.DeliveryState
import cz.kuclab.hertzchat.data.db.MessageEntity
import cz.kuclab.hertzchat.data.db.MessageType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contract for the chat-list row preview: the newest message of each thread,
 * incoming or outgoing, with media rendered as a label (it carries no text).
 */
class ChatListPreviewTest {

    private fun message(fromMe: Boolean, type: MessageType, text: String? = null, fileName: String? = null) =
        MessageEntity(
            messageId = "m",
            contactId = "c",
            fromMe = fromMe,
            type = type,
            text = text,
            mediaFileName = fileName,
            timestamp = 1L,
            deliveryState = DeliveryState.DELIVERED,
        )

    @Test
    fun `incoming text shows raw`() {
        assertEquals("Ahoj", previewFor(message(fromMe = false, type = MessageType.TEXT, text = "Ahoj")))
    }

    @Test
    fun `outgoing text is prefixed`() {
        assertEquals("Ty: Ahoj", previewFor(message(fromMe = true, type = MessageType.TEXT, text = "Ahoj")))
    }

    @Test
    fun `multiline text previews its first line`() {
        assertEquals("Ty: jedna", previewFor(message(fromMe = true, type = MessageType.TEXT, text = "jedna\ndva")))
    }

    @Test
    fun `media shows a label`() {
        assertEquals("Fotka", previewFor(message(fromMe = false, type = MessageType.IMAGE)))
        assertEquals("Ty: Video", previewFor(message(fromMe = true, type = MessageType.VIDEO)))
        assertEquals("Hlasová zpráva", previewFor(message(fromMe = false, type = MessageType.VOICE)))
        assertEquals("Ty: dok.pdf", previewFor(message(fromMe = true, type = MessageType.FILE, fileName = "dok.pdf")))
        assertEquals("Soubor", previewFor(message(fromMe = false, type = MessageType.FILE)))
    }
}
