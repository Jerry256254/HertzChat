package cz.kuclab.hertzchat.network.signaling

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PresenceEntry(val contactId: String, val nickname: String)

@Serializable
data class PresenceUpdate(val online: List<PresenceEntry> = emptyList())

/** Generic envelope relayed blindly by the server between two online clients. */
@Serializable
data class RelayEnvelope(val from: String = "", val payload: JsonElement)

@Serializable
data class SdpPayload(val kind: String, val sdp: String) // kind: "offer" | "answer"

@Serializable
data class IceCandidatePayload(val sdpMid: String?, val sdpMLineIndex: Int, val candidate: String)

@Serializable
data class FriendRequestPayload(
    val nickname: String,
    val identityKeyBase64: String,
    val preKeyBundle: PreKeyBundleWire,
)

@Serializable
data class FriendResponsePayload(val accepted: Boolean, val nickname: String, val identityKeyBase64: String)

/** Everything needed to run the X3DH handshake against a peer we've never talked to before. */
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
