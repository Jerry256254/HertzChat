package cz.kuclab.hertzchat.network.p2p

import kotlinx.serialization.Serializable

/**
 * Everything needed to run the X3DH handshake against a peer we've never
 * talked to before, plus the relay key they're reachable at. Sealed to the
 * recipient's identity key (see NostrCrypto) - never sent in the clear.
 */
@Serializable
data class PreKeyBundleWire(
    val registrationId: Int,
    val deviceId: Int,
    val preKeyId: Int,
    val preKeyPublicBase64: String?,
    val signedPreKeyId: Int,
    val signedPreKeyPublicBase64: String,
    val signedPreKeySignatureBase64: String,
    val identityKeyBase64: String,
    val kyberPreKeyId: Int,
    val kyberPreKeyPublicBase64: String,
    val kyberPreKeySignatureBase64: String,
)

@Serializable
data class FriendRequestPayload(
    val nickname: String,
    val identityKeyBase64: String,
    val nostrPubkeyHex: String,
    val preKeyBundle: PreKeyBundleWire,
    /** Non-null when this request was auto-sent as a consequence of a mutual group invite - see P2pChatService group handling. */
    val viaGroupId: String? = null,
)

/**
 * [preKeyBundle] is what makes this symmetric with [FriendRequestPayload]. A Signal
 * session is one-directional to set up: whoever *processes* a bundle becomes the
 * initiator and can encrypt immediately, while the other side only gets their half
 * of the session the moment they successfully decrypt that initiator's first message.
 * Without a bundle here, only the person who *accepted* the request could ever send
 * first - the person who *sent* the request had nothing to process, so calling
 * encrypt() from that side threw NoSessionException the moment they tried, whether
 * that was a direct message or (mentioned as its own crash) the first message in a
 * group where the crasher was the one who'd sent the original request.
 */
@Serializable
data class FriendResponsePayload(
    val accepted: Boolean,
    val nickname: String,
    val identityKeyBase64: String,
    val nostrPubkeyHex: String,
    val preKeyBundle: PreKeyBundleWire? = null,
)

/**
 * The compact, shareable "Hertz ID" a user hands a friend out-of-band (QR
 * code, read aloud, sent through any other app) so that friend's device can
 * publish a sealed [FriendRequestPayload] to their relay key. There is no
 * directory to browse - like the relay keys themselves, you can only reach
 * an address you already have.
 *
 * [nostrPubkeyHex] defaults so that codes from pre-relay versions (which
 * carried `i2pDestination` instead) still *parse* and fail later with an
 * "update the app" message rather than an inscrutable JSON error.
 */
/**
 * The sealed envelope a friend request or response travels in over the relay:
 * AES-256-GCM to the recipient's identity key, so the relay (and any
 * bystander) sees only an anonymous blob. The recipient trial-opens every
 * such envelope with their own identity key - strangers have no pairwise tag
 * yet, so there is nothing cheaper to route on.
 */
@Serializable
data class SealedWire(
    val ephemeralPublicHex: String,
    val nonceBase64: String,
    val ciphertextBase64: String,
)

@Serializable
data class HertzId(
    val contactId: String,
    val nickname: String,
    val identityKeyBase64: String,
    val nostrPubkeyHex: String = "",
)
