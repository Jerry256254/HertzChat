package cz.kuclab.hertzchat.network.relay

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.asn1.x9.ECNamedCurveTable
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.math.ec.ECPoint

/**
 * Everything the relay transport needs cryptographically, in one pure-Kotlin
 * place with no Android dependencies (so the JVM unit tests exercise the
 * exact same code the app runs):
 *
 * - BIP-340 Schnorr signatures over secp256k1: every Nostr event must carry
 *   one. Implemented directly against BouncyCastle's EC primitives and
 *   checked against the official BIP-340 test vectors (see NostrCryptoTest) -
 *   there is no need for a whole Bitcoin library just to sign events.
 * - X25519 ECDH over the *Signal identity keys* both sides already have: the
 *   shared secret derives the pairwise routing tag (so the relay routes
 *   without ever learning who either side is) and seals friend requests (so a
 *   request to a stranger leaks nothing but "somebody contacted this key").
 */
object NostrCrypto {

    private val random = SecureRandom()

    // --- secp256k1 / BIP-340 ---

    private val curveParams = ECNamedCurveTable.getByName("secp256k1")
    private val curve = curveParams.curve
    private val G: ECPoint = curveParams.g
    private val n: BigInteger = curveParams.n
    private val p: BigInteger = (curve.field.characteristic
        ?: error("secp256k1 must be a prime field"))

    fun sha256(vararg parts: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach { digest.update(it) }
        return digest.digest()
    }

    /** BIP-340 tagged hash: SHA256(SHA256(tag) || SHA256(tag) || msg...). */
    private fun taggedHash(tag: String, vararg msgs: ByteArray): ByteArray {
        val tagHash = sha256(tag.encodeToByteArray())
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(tagHash)
        digest.update(tagHash)
        msgs.forEach { digest.update(it) }
        return digest.digest()
    }

    fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Odd-length hex" }
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private fun BigInteger.to32Bytes(): ByteArray {
        val raw = toByteArray()
        return when {
            raw.size == 32 -> raw
            raw.size == 33 && raw[0] == 0.toByte() -> raw.copyOfRange(1, 33)
            raw.size < 32 -> ByteArray(32 - raw.size) + raw
            else -> error("Scalar out of range")
        }
    }

    private fun bytesToInt(bytes: ByteArray): BigInteger = BigInteger(1, bytes)

    private fun hasEvenY(point: ECPoint): Boolean {
        val norm = point.normalize()
        return !norm.affineYCoord.toBigInteger().testBit(0)
    }

    /** Lifts an x-only pubkey to the full point (always the even-Y one), or null when x is not on the curve. */
    private fun liftX(xBytes: ByteArray): ECPoint? {
        val x = bytesToInt(xBytes)
        if (x >= p) return null
        // p % 4 == 3 for secp256k1, so sqrt is a single exponentiation.
        val c = (x.modPow(BigInteger.valueOf(3), p) + BigInteger.valueOf(7)).mod(p)
        var y = c.modPow((p + BigInteger.ONE).shiftRight(2), p)
        if ((y.multiply(y)).mod(p) != c) return null
        if (y.testBit(0)) y = p - y
        return runCatching { curve.createPoint(x, y) }.getOrNull()
    }

    /** Fresh random secret key (32 bytes, 1..n-1). */
    fun generateSecretKey(): ByteArray {
        while (true) {
            val candidate = ByteArray(32).also { random.nextBytes(it) }
            val scalar = bytesToInt(candidate)
            if (scalar > BigInteger.ZERO && scalar < n) return candidate
        }
    }

    /** X-only (32-byte) public key for a secret key. */
    fun pubkeyFromSecret(secret: ByteArray): ByteArray {
        var d = bytesToInt(secret)
        require(d > BigInteger.ZERO && d < n) { "Secret key out of range" }
        var point = G.multiply(d).normalize()
        if (!hasEvenY(point)) {
            d = n - d
            point = G.multiply(d).normalize()
        }
        return point.affineXCoord.toBigInteger().to32Bytes()
    }

    /**
     * BIP-340 sign. [auxRand] should be 32 fresh random bytes per signature
     * (it exists precisely to make equal messages sign differently); the test
     * vectors pass fixed values instead.
     */
    fun schnorrSign(secret: ByteArray, message: ByteArray, auxRand: ByteArray = ByteArray(32).also { random.nextBytes(it) }): ByteArray {
        require(message.size == 32) { "BIP-340 signs a 32-byte hash" }
        require(auxRand.size == 32) { "aux_rand must be 32 bytes" }
        var d = bytesToInt(secret)
        require(d > BigInteger.ZERO && d < n) { "Secret key out of range" }
        if (!hasEvenY(G.multiply(d).normalize())) d = n - d
        val pub = G.multiply(d).normalize().affineXCoord.toBigInteger().to32Bytes()

        // Note the aux hash: the spec xors d with hash("BIP0340/aux", aux),
        // not with aux directly - skipping it still signs validly but
        // non-conformingly (caught by the BIP-340 vectors, not by round-trips).
        val t = d.to32Bytes().xorBytes(taggedHash("BIP0340/aux", auxRand))
        val rand = taggedHash("BIP0340/nonce", t, pub, message)
        var k = bytesToInt(rand).mod(n)
        require(k != BigInteger.ZERO) { "Nonce is zero - retry with fresh aux_rand" }
        if (!hasEvenY(G.multiply(k).normalize())) k = n - k

        val r = G.multiply(k).normalize().affineXCoord.toBigInteger().to32Bytes()
        val e = bytesToInt(taggedHash("BIP0340/challenge", r, pub, message)).mod(n)
        val sig = (r.toList() + ((k + e.multiply(d)).mod(n).to32Bytes().toList())).toByteArray()
        require(verify(pub, message, sig)) { "Produced an invalid signature" }
        return sig
    }

    private fun ByteArray.xorBytes(other: ByteArray): ByteArray {
        require(size == other.size) { "xor length mismatch" }
        return ByteArray(size) { i -> (this[i].toInt() xor other[i].toInt()).toByte() }
    }

    /** BIP-340 verify. False on any malformed input - never throws for attacker-controlled bytes. */
    fun verify(pubXOnly: ByteArray, message: ByteArray, sig: ByteArray): Boolean {
        if (pubXOnly.size != 32 || message.size != 32 || sig.size != 64) return false
        val point = runCatching { liftX(pubXOnly.copyOfRange(0, 32)) }.getOrNull() ?: return false
        val r = bytesToInt(sig.copyOfRange(0, 32))
        val s = bytesToInt(sig.copyOfRange(32, 64))
        if (r >= p || s >= n) return false
        val e = bytesToInt(taggedHash("BIP0340/challenge", sig.copyOfRange(0, 32), pubXOnly, message)).mod(n)
        val minusE = (n - e).mod(n)
        val rPoint = G.multiply(s).add(point.multiply(minusE)).normalize()
        if (rPoint.isInfinity) return false
        if (!hasEvenY(rPoint)) return false
        return rPoint.affineXCoord.toBigInteger() == r
    }

    // --- X25519 over the Signal identity keys ---

    /**
     * Raw X25519 shared secret between our private identity key and a peer's
     * public identity key (both as libsignal serializes them: the public key
     * carries a one-byte 0x05 type prefix, the private key is raw 32 bytes).
     */
    fun identityAgreement(ownPrivateSerialized: ByteArray, peerPublicSerialized: ByteArray): ByteArray {
        require(ownPrivateSerialized.size == 32) { "Identity private key must be 32 bytes" }
        val peerRaw = when (peerPublicSerialized.size) {
            33 -> {
                require(peerPublicSerialized[0] == 0x05.toByte()) { "Not an X25519 identity key" }
                peerPublicSerialized.copyOfRange(1, 33)
            }
            32 -> peerPublicSerialized
            else -> error("Identity public key must be 32 or 33 bytes")
        }
        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(ownPrivateSerialized))
        val out = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(peerRaw), out, 0)
        return out
    }

    /**
     * The routing tag for one contact pair: both sides derive the identical
     * tag from the same ECDH secret, so a message carries only this opaque
     * tag - the relay learns "something for this tag" and can neither name
     * the sender nor link the tag to any long-term identity.
     */
    fun pairRoutingTag(ownPrivateSerialized: ByteArray, peerPublicSerialized: ByteArray): String {
        val shared = identityAgreement(ownPrivateSerialized, peerPublicSerialized)
        return bytesToHex(sha256("hertz-route-v1".encodeToByteArray(), shared)).take(32)
    }

    // --- Sealed friend requests ---

    data class SealedRequest(val ephemeralPublicHex: String, val nonceBase64: String, val ciphertextBase64: String)

    /**
     * Seals a friend-request (or response) JSON to a stranger's identity key:
     * ECDH with a fresh ephemeral key, then AES-256-GCM. The relay sees only
     * an anonymous blob for the recipient's key - no sender, no nickname, no
     * prekeys.
     */
    fun sealToIdentityKey(plaintext: ByteArray, recipientPublicSerialized: ByteArray): SealedRequest {
        val ephemeralPrivate = ByteArray(32).also { random.nextBytes(it) }
        val ephemeralPublic = ByteArray(32)
        org.bouncycastle.math.ec.rfc7748.X25519.scalarMultBase(ephemeralPrivate, 0, ephemeralPublic, 0)
        val peerRaw = if (recipientPublicSerialized.size == 33) recipientPublicSerialized.copyOfRange(1, 33) else recipientPublicSerialized
        require(peerRaw.size == 32) { "Identity public key must be 32 or 33 bytes" }
        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(ephemeralPrivate))
        val shared = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(peerRaw), shared, 0)
        val aesKey = sha256("hertz-fr-v1".encodeToByteArray(), shared)
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, nonce))
        }
        val ciphertext = cipher.doFinal(plaintext)
        return SealedRequest(
            ephemeralPublicHex = bytesToHex(ephemeralPublic),
            nonceBase64 = java.util.Base64.getEncoder().encodeToString(nonce),
            ciphertextBase64 = java.util.Base64.getEncoder().encodeToString(ciphertext),
        )
    }

    /** Opens a [sealToIdentityKey] blob with our own identity private key. Null when it wasn't sealed to us (or is corrupt). */
    fun openSealedRequest(sealed: SealedRequest, ownPrivateSerialized: ByteArray): ByteArray? = runCatching {
        require(ownPrivateSerialized.size == 32) { "Identity private key must be 32 bytes" }
        val ephemeralPublic = hexToBytes(sealed.ephemeralPublicHex)
        require(ephemeralPublic.size == 32) { "Bad ephemeral key" }
        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(ownPrivateSerialized))
        val shared = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(ephemeralPublic), shared, 0)
        val aesKey = sha256("hertz-fr-v1".encodeToByteArray(), shared)
        val nonce = java.util.Base64.getDecoder().decode(sealed.nonceBase64)
        val ciphertext = java.util.Base64.getDecoder().decode(sealed.ciphertextBase64)
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, nonce))
        }.doFinal(ciphertext)
    }.getOrNull()
}
