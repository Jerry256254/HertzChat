package cz.kuclab.hertzchat.network.p2p

import kotlinx.serialization.Serializable

/**
 * Everything needed to run the X3DH handshake against a peer we've never
 * talked to before, plus the I2P destination they're reachable at. Exchanged
 * only over an already-established connection to that same destination -
 * never sent anywhere else.
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
    val i2pDestination: String,
    val preKeyBundle: PreKeyBundleWire,
    /** Non-null when this request was auto-sent as a consequence of a mutual group invite - see P2pChatService group handling. */
    val viaGroupId: String? = null,
    val allowsMistralAccess: Boolean = true,
)

@Serializable
data class FriendResponsePayload(
    val accepted: Boolean,
    val nickname: String,
    val identityKeyBase64: String,
    val i2pDestination: String,
    val allowsMistralAccess: Boolean = true,
)

/**
 * The compact, shareable "Hertz ID" a user hands a friend out-of-band (QR
 * code, read aloud, sent through any other app) so that friend's device can
 * reach out over I2P and send a [FriendRequestPayload]. There is no
 * directory to browse - like I2P destinations themselves, you can only
 * connect to an address you already have.
 */
@Serializable
data class HertzId(
    val contactId: String,
    val nickname: String,
    val identityKeyBase64: String,
    val i2pDestination: String,
)
