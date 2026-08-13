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
    /**
     * The attachment itself never travels through the Signal ratchet - only
     * this per-attachment AES-256-GCM key does. The encrypted bytes are sent
     * separately as raw chunks over the data channel, tagged with
     * [mediaTransferId], and re-assembled/decrypted using this key.
     */
    val mediaTransferId: String? = null,
    val mediaKeyBase64: String? = null,
    val mediaNonceSaltBase64: String? = null,
    val mediaChunkCount: Int? = null,
)

@Serializable
enum class PayloadKind { TEXT, IMAGE, VIDEO, VOICE, FILE, AVATAR, DELIVERED_ACK, READ_ACK, TYPING }
