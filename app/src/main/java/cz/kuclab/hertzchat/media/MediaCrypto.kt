package cz.kuclab.hertzchat.media

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Media (images/video/voice) is too large to push through the Signal
 * Double Ratchet chunk-by-chunk without wasting ratchet steps, so instead a
 * random AES-256-GCM key is generated per attachment, the raw bytes are
 * split into chunks and each chunk is independently AEAD-encrypted with
 * that key (so a receiver can decrypt and write to disk as chunks arrive,
 * without buffering the whole file in memory). The key itself is only ever
 * delivered inside a normal Signal-Protocol-encrypted [cz.kuclab.hertzchat.data.model.ChatPayload],
 * so it still has full end-to-end forward secrecy - nothing about the
 * attachment is ever visible to the P2P transport or signaling relay.
 */
object MediaCrypto {
    const val CHUNK_SIZE = 16 * 1024
    private const val GCM_TAG_BITS = 128
    private const val NONCE_SALT_SIZE = 8

    fun generateKey(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }
    fun generateNonceSalt(): ByteArray = ByteArray(NONCE_SALT_SIZE).also { SecureRandom().nextBytes(it) }

    private fun nonceFor(nonceSalt: ByteArray, chunkIndex: Int): ByteArray =
        ByteBuffer.allocate(12).put(nonceSalt).putInt(chunkIndex).array()

    fun encryptChunk(key: ByteArray, nonceSalt: ByteArray, chunkIndex: Int, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonceFor(nonceSalt, chunkIndex)),
        )
        return cipher.doFinal(plaintext)
    }

    fun decryptChunk(key: ByteArray, nonceSalt: ByteArray, chunkIndex: Int, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonceFor(nonceSalt, chunkIndex)),
        )
        return cipher.doFinal(ciphertext)
    }
}
