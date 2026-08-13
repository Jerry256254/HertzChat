package cz.kuclab.hertzchat.crypto

import android.util.Base64
import cz.kuclab.hertzchat.network.p2p.PreKeyBundleWire
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.state.PreKeyBundle

private fun ByteArray.b64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
private fun String.fromB64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

fun PreKeyBundle.toWire(): PreKeyBundleWire = PreKeyBundleWire(
    registrationId = registrationId,
    deviceId = deviceId,
    preKeyId = preKeyId,
    preKeyPublicBase64 = preKey?.serialize()?.b64(),
    signedPreKeyId = signedPreKeyId,
    signedPreKeyPublicBase64 = signedPreKey.serialize().b64(),
    signedPreKeySignatureBase64 = signedPreKeySignature.b64(),
    identityKeyBase64 = identityKey.serialize().b64(),
    kyberPreKeyId = kyberPreKeyId,
    kyberPreKeyPublicBase64 = kyberPreKey.serialize().b64(),
    kyberPreKeySignatureBase64 = kyberPreKeySignature.b64(),
)

fun PreKeyBundleWire.toPreKeyBundle(): PreKeyBundle = PreKeyBundle(
    registrationId,
    deviceId,
    preKeyId,
    preKeyPublicBase64?.let { ECPublicKey(it.fromB64()) },
    signedPreKeyId,
    ECPublicKey(signedPreKeyPublicBase64.fromB64()),
    signedPreKeySignatureBase64.fromB64(),
    IdentityKey(identityKeyBase64.fromB64()),
    kyberPreKeyId,
    KEMPublicKey(kyberPreKeyPublicBase64.fromB64()),
    kyberPreKeySignatureBase64.fromB64(),
)
