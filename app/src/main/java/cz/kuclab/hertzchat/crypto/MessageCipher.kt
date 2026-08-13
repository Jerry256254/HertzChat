package cz.kuclab.hertzchat.crypto

import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.SignalProtocolStore

/** Wire framing for one encrypted envelope sent over the WebRTC data channel. */
data class EncryptedEnvelope(
    val isPreKeyMessage: Boolean,
    val ciphertext: ByteArray,
)

/**
 * Thin wrapper around the Signal Protocol (X3DH + Double Ratchet) for exactly
 * one contact. Every message is encrypted with a fresh ratchet key derived
 * from the previous one (forward secrecy) - even if a single message key is
 * ever compromised, past and future messages stay unreadable.
 */
class MessageCipher(
    private val store: SignalProtocolStore,
    private val contactId: String,
    private val remoteDeviceId: Int = 1,
) {
    private val address = SignalProtocolAddress(contactId, remoteDeviceId)
    private val cipher = SessionCipher(store, address)

    /** Call once, using a bundle the peer published while online, before the very first message to them. */
    fun establishSessionFromBundle(bundle: PreKeyBundle) {
        SessionBuilder(store, address).process(bundle)
    }

    fun hasSession(): Boolean = store.containsSession(address)

    fun encrypt(plaintext: ByteArray): EncryptedEnvelope {
        val message = cipher.encrypt(plaintext)
        return EncryptedEnvelope(
            isPreKeyMessage = message.type == CiphertextMessage.PREKEY_TYPE,
            ciphertext = message.serialize(),
        )
    }

    fun decrypt(envelope: EncryptedEnvelope): ByteArray = if (envelope.isPreKeyMessage) {
        cipher.decrypt(PreKeySignalMessage(envelope.ciphertext))
    } else {
        cipher.decrypt(SignalMessage(envelope.ciphertext))
    }
}
