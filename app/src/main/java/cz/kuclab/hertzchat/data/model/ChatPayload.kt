package cz.kuclab.hertzchat.data.model

import cz.kuclab.hertzchat.network.tor.HertzId
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
    /** Set when this message belongs to a group thread instead of a 1:1 one - the sender is whoever's session decrypted the envelope. */
    val groupId: String? = null,
    /** Only set for [PayloadKind.GROUP_INVITE]. */
    val groupName: String? = null,
    /** Only set for [PayloadKind.GROUP_INVITE] - every member of the new group (including the sender and the recipient), so the recipient can bootstrap sessions with members it doesn't already know. */
    val groupMembers: List<HertzId>? = null,
    /** True when this is Mistral's reply, relayed into the thread by whoever invoked @Mistral - rendered with the assistant's identity, not the relayer's. */
    val fromAssistant: Boolean = false,
    /** Only set for [PayloadKind.PREFERENCE_UPDATE] - broadcast to every contact whenever the local "let others use @Mistral on my messages" toggle changes. */
    val allowsMistralAccess: Boolean? = null,
)

@Serializable
enum class PayloadKind { TEXT, IMAGE, VIDEO, VOICE, FILE, AVATAR, DELIVERED_ACK, READ_ACK, TYPING, GROUP_INVITE, PREFERENCE_UPDATE }
