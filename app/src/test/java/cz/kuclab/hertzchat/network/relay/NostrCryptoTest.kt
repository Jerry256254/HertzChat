package cz.kuclab.hertzchat.network.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Schnorr implementation signs the bytes every message carries, so it is
 * checked against the official BIP-340 test vectors (bitcoin/bips), not just
 * against itself: signing with the vector's fixed aux_rand must reproduce the
 * vector's signature byte for byte.
 */
class NostrCryptoTest {

    private fun hex(s: String): ByteArray = NostrCrypto.hexToBytes(s)

    @Test
    fun `bip340 vector 0 signs deterministically`() {
        val sig = NostrCrypto.schnorrSign(
            secret = hex("0000000000000000000000000000000000000000000000000000000000000003"),
            message = hex("0000000000000000000000000000000000000000000000000000000000000000"),
            auxRand = hex("0000000000000000000000000000000000000000000000000000000000000000"),
        )
        assertEquals(
            "E907831F80848D1069A5371B402410364BDF1C5F8307B0084C55F1CE2DCA821525F66A4A85EA8B71E482A74F382D2CE5EBEEE8FDB2172F477DF4900D310536C0",
            NostrCrypto.bytesToHex(sig).uppercase(),
        )
        assertEquals(
            "F9308A019258C31049344F85F89D5229B531C845836F99B08601F113BCE036F9",
            NostrCrypto.bytesToHex(
                NostrCrypto.pubkeyFromSecret(hex("0000000000000000000000000000000000000000000000000000000000000003")),
            ).uppercase(),
        )
    }

    @Test
    fun `bip340 vector 1 signs deterministically`() {
        val sig = NostrCrypto.schnorrSign(
            secret = hex("B7E151628AED2A6ABF7158809CF4F3C762E7160F38B4DA56A784D9045190CFEF"),
            message = hex("243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89"),
            auxRand = hex("0000000000000000000000000000000000000000000000000000000000000001"),
        )
        assertEquals(
            "6896BD60EEAE296DB48A229FF71DFE071BDE413E6D43F917DC8DCF8C78DE33418906D11AC976ABCCB20B091292BFF4EA897EFCB639EA871CFA95F6DE339E4B0A",
            NostrCrypto.bytesToHex(sig).uppercase(),
        )
    }

    @Test
    fun `bip340 vector 2 signs deterministically`() {
        val sig = NostrCrypto.schnorrSign(
            secret = hex("C90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B14E5C9"),
            message = hex("7E2D58D8B3BCDF1ABADEC7829054F90DDA9805AAB56C77333024B9D0A508B75C"),
            auxRand = hex("C87AA53824B4D7AE2EB035A2B5BBBCCC080E76CDC6D1692C4B0B62D798E6D906"),
        )
        assertEquals(
            "5831AAEED7B44BB74E5EAB94BA9D4294C49BCF2A60728D8B4C200F50DD313C1BAB745879A5AD954A72C45A91C3A51D3C7ADEA98D82F8481E0E1E03674A6F3FB7",
            NostrCrypto.bytesToHex(sig).uppercase(),
        )
    }

    @Test
    fun `bip340 vector 4 verifies`() {
        assertTrue(
            NostrCrypto.verify(
                pubXOnly = hex("D69C3509BB99E412E68B0FE8544E72837DFA30746D8BE2AA65975F29D22DC7B9"),
                message = hex("4DF3C3F68FCC83B27E9D42C90431A72499F17875C81A599B566C9889B9696703"),
                sig = hex("00000000000000000000003B78CE563F89A0ED9414F5AA28AD0D96D6795F9C6376AFB1548AF603B3EB45C9F8207DEE1060CB71C04E80F593060B07D28308D7F4"),
            ),
        )
    }

    @Test
    fun `bip340 vectors 5 and 6 fail verification`() {
        // Public key not on the curve.
        assertFalse(
            NostrCrypto.verify(
                pubXOnly = hex("EEFDEA4CDB677750A420FEE807EACF21EB9898AE79B9768766E4FAA04A2D4A34"),
                message = hex("243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89"),
                sig = hex("6CFF5C3BA86C69EA4B7376F31A9BCB4F74C1976089B2D9963DA2E5543E17776969E89B4C5564D00349106B8497785DD7D1D713A8AE82B32FA79D5F7FC407D39B"),
            ),
        )
        // has_even_y(R) is false.
        assertFalse(
            NostrCrypto.verify(
                pubXOnly = hex("DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659"),
                message = hex("243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89"),
                sig = hex("FFF97BD5755EEEA420453A14355235D382F6472F8568A18B2F057A14602975563CC27944640AC607CD107AE10923D9EF7A73C643E166BE5EBEAFA34B1AC553E2"),
            ),
        )
    }

    @Test
    fun `fresh keys round-trip and tampering fails`() {
        val secret = NostrCrypto.generateSecretKey()
        val pub = NostrCrypto.pubkeyFromSecret(secret)
        val message = NostrCrypto.sha256("hello hertz".encodeToByteArray())
        val sig = NostrCrypto.schnorrSign(secret, message)
        assertTrue(NostrCrypto.verify(pub, message, sig))

        val tamperedSig = sig.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertFalse(NostrCrypto.verify(pub, message, tamperedSig))

        val tamperedMsg = message.copyOf().also { it[31] = (it[31].toInt() xor 1).toByte() }
        assertFalse(NostrCrypto.verify(pub, tamperedMsg, sig))

        val otherPub = NostrCrypto.pubkeyFromSecret(NostrCrypto.generateSecretKey())
        assertFalse(NostrCrypto.verify(otherPub, message, sig))

        // Garbage never throws, it just fails.
        assertFalse(NostrCrypto.verify(ByteArray(31), message, sig))
        assertFalse(NostrCrypto.verify(pub, ByteArray(10), sig))
        assertFalse(NostrCrypto.verify(pub, message, ByteArray(64)))
    }

    @Test
    fun `pair routing tag is symmetric and stable`() {
        // Two X25519 keypairs (raw 32-byte form, as libsignal serializes them minus the 0x05 prefix).
        val alicePriv = NostrCrypto.hexToBytes("77076D0A7318A003B9595F22F0D48AA9525F429AE671027153012A6A116AFF9C")
        val alicePub = ByteArray(33).also {
            it[0] = 0x05
            org.bouncycastle.math.ec.rfc7748.X25519.scalarMultBase(alicePriv, 0, it, 1)
        }
        val bobPriv = NostrCrypto.hexToBytes("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val bobPub = ByteArray(33).also {
            it[0] = 0x05
            org.bouncycastle.math.ec.rfc7748.X25519.scalarMultBase(bobPriv, 0, it, 1)
        }

        val fromAlice = NostrCrypto.pairRoutingTag(alicePriv, bobPub)
        val fromBob = NostrCrypto.pairRoutingTag(bobPriv, alicePub)
        assertEquals(fromAlice, fromBob)
        assertEquals(32, fromAlice.length)
        // And stable across calls.
        assertEquals(fromAlice, NostrCrypto.pairRoutingTag(alicePriv, bobPub))
    }

    @Test
    fun `sealed request round-trips and fails closed`() {
        val recipientPriv = NostrCrypto.hexToBytes("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val recipientPub = ByteArray(33).also {
            it[0] = 0x05
            org.bouncycastle.math.ec.rfc7748.X25519.scalarMultBase(recipientPriv, 0, it, 1)
        }
        val plaintext = """{"nickname":"Testovič","contactId":"abc"}""".encodeToByteArray()

        val sealed = NostrCrypto.sealToIdentityKey(plaintext, recipientPub)
        val opened = NostrCrypto.openSealedRequest(sealed, recipientPriv)
        assertTrue(opened != null && opened.contentEquals(plaintext))

        // Same plaintext seals differently every time (fresh ephemeral key + nonce).
        val sealed2 = NostrCrypto.sealToIdentityKey(plaintext, recipientPub)
        assertTrue(sealed.ciphertextBase64 != sealed2.ciphertextBase64)

        // Wrong key fails closed (null, no exception).
        val wrongPriv = NostrCrypto.hexToBytes("77076D0A7318A003B9595F22F0D48AA9525F429AE671027153012A6A116AFF9C")
        assertEquals(null, NostrCrypto.openSealedRequest(sealed, wrongPriv))
    }
}
