package cz.kuclab.hertzchat.data.model

import kotlinx.serialization.Serializable

/**
 * The logical content of one chat message, serialized to JSON and then
 * encrypted end-to-end via [cz.kuclab.hertzchat.crypto.MessageCipher] before
 * it ever leaves the device. Nothing in here is ever sent in the clear.
 */
@Serializable
data class ChatPayload(
    val messageId: String,
    val sentAt: Long,
    val kind: PayloadKind,
    val text: String? = null,
    val mediaMimeType: String? = null,
    val mediaFileName: String? = null,
    val mediaSizeBytes: Long? = null,
    val mediaDurationMs: Long? = null,
    /** base64-encoded bytes for small media / chunks; large media is fragmented across several envelopes sharing mediaTransferId. */
    val mediaChunkBase64: String? = null,
    val mediaTransferId: String? = null,
    val mediaChunkIndex: Int? = null,
    val mediaChunkCount: Int? = null,
)

@Serializable
enum class PayloadKind { TEXT, IMAGE, VIDEO, VOICE, FILE, DELIVERED_ACK, READ_ACK, TYPING }
