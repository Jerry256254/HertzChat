package cz.kuclab.hertzchat.data.model

import cz.kuclab.hertzchat.network.p2p.HertzId
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
    /** Set for [PayloadKind.GROUP_INVITE] and [PayloadKind.GROUP_ROSTER_UPDATE] - only this contactId may add or remove members; every recipient checks the sender against it rather than trusting the update on its own. */
    val groupOwnerId: String? = null,
    /**
     * Only set for [PayloadKind.GROUP_ROSTER_UPDATE] - the *complete* membership after an
     * add or remove, not a delta. A recipient replaces its whole local member list with
     * this one; if its own contactId is missing, that's how it learns it was removed.
     */
    val groupRoster: List<HertzId>? = null,
    /** Only set for [PayloadKind.DELIVERED_ACK] - the messageId this receipt confirms. */
    val ackForMessageId: String? = null,
    /** Only set for [PayloadKind.PROFILE_UPDATE] - the sender's current nickname; the avatar (if any) follows separately through the AVATAR media flow. */
    val profileNickname: String? = null,
    /**
     * Stamped on *every* outgoing payload (see P2pChatService): the sender's current
     * nickname plus a hash of their current avatar. The receiver applies the nickname
     * directly and pulls the avatar only when the hash differs - so profiles sync
     * through ordinary traffic and an offline peer catches up on the next message
     * instead of missing a one-shot broadcast forever.
     */
    val senderNickname: String? = null,
    /** SHA-256 hex of the sender's downscaled avatar, or null when they have none. */
    val senderAvatarHash: String? = null,
    /** Set for every CALL_* kind - the call this signaling or audio packet belongs to. */
    val callId: String? = null,
    /** Only set for [PayloadKind.CALL_AUDIO] - base64 of one 20ms 8kHz mono PCM frame. */
    val audioBase64: String? = null,
    /** Only set for [PayloadKind.CALL_AUDIO] - sender-side sequence, so the player can drop late packets. */
    val audioSeq: Long? = null,
)

// Note: older peers may still send `fromAssistant`, `allowsMistralAccess` or a
// PREFERENCE_UPDATE kind from the removed Mistral assistant - all safely ignored
// (unknown fields via ignoreUnknownKeys, unknown kinds via the runCatching around decode).

@Serializable
enum class PayloadKind { TEXT, IMAGE, VIDEO, VOICE, FILE, AVATAR, DELIVERED_ACK, READ_ACK, TYPING, PING, GROUP_INVITE, GROUP_ROSTER_UPDATE, GROUP_DELETE, PROFILE_UPDATE, PROFILE_REQUEST, CALL_OFFER, CALL_ANSWER, CALL_REJECT, CALL_HANGUP, CALL_AUDIO }
