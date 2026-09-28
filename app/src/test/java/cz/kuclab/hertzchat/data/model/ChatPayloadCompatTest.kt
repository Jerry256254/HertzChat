package cz.kuclab.hertzchat.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire-compat contract for [ChatPayload]: old (0.25.x) and new peers exchange frames
 * without shared code, so decoding must stay graceful in both directions. This pins the
 * behavior [cz.kuclab.hertzchat.data.repository.P2pChatService.onEnvelopeReceived] relies on
 * (unknown fields ignored, unknown kinds dropped via runCatching/getOrNull).
 */
class ChatPayloadCompatTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `old payload with removed mistral fields still decodes`() {
        val oldStyle = """{"messageId":"m1","sentAt":1,"kind":"TEXT","text":"ahoj","fromAssistant":true,"allowsMistralAccess":false}"""
        val decoded = json.decodeFromString(ChatPayload.serializer(), oldStyle)
        assertEquals("m1", decoded.messageId)
        assertEquals("ahoj", decoded.text)
    }

    @Test
    fun `removed PREFERENCE_UPDATE kind fails decode and is dropped like onEnvelopeReceived does`() {
        val stale = """{"messageId":"m2","sentAt":1,"kind":"PREFERENCE_UPDATE","allowsMistralAccess":true}"""
        val decoded = runCatching { json.decodeFromString(ChatPayload.serializer(), stale) }.getOrNull()
        assertNull(decoded)
    }

    @Test
    fun `new ack and profile fields round-trip`() {
        val ack = ChatPayload("m3", 1L, PayloadKind.DELIVERED_ACK, ackForMessageId = "orig-1")
        val profile = ChatPayload("m4", 1L, PayloadKind.PROFILE_UPDATE, profileNickname = "Jerry")
        assertEquals("orig-1", json.decodeFromString(ChatPayload.serializer(), json.encodeToString(ChatPayload.serializer(), ack)).ackForMessageId)
        assertEquals("Jerry", json.decodeFromString(ChatPayload.serializer(), json.encodeToString(ChatPayload.serializer(), profile)).profileNickname)
    }

    @Test
    fun `profile request kind round-trips`() {
        val request = ChatPayload("m6", 1L, PayloadKind.PROFILE_REQUEST)
        val decoded = json.decodeFromString(ChatPayload.serializer(), json.encodeToString(ChatPayload.serializer(), request))
        assertEquals(PayloadKind.PROFILE_REQUEST, decoded.kind)
    }

    @Test
    fun `payloads without new fields default them to null`() {
        val minimal = """{"messageId":"m5","sentAt":1,"kind":"TEXT","text":"x"}"""
        val decoded = json.decodeFromString(ChatPayload.serializer(), minimal)
        assertNull(decoded.ackForMessageId)
        assertNull(decoded.profileNickname)
        assertTrue(decoded.groupId == null)
    }

    @Test
    fun `call signaling and audio round-trip`() {
        val offer = ChatPayload("c1", 1L, PayloadKind.CALL_OFFER, callId = "call-1")
        val audio = ChatPayload("c2", 1L, PayloadKind.CALL_AUDIO, callId = "call-1", audioBase64 = "QUJD", audioSeq = 7L)
        val decodedOffer = json.decodeFromString(ChatPayload.serializer(), json.encodeToString(ChatPayload.serializer(), offer))
        val decodedAudio = json.decodeFromString(ChatPayload.serializer(), json.encodeToString(ChatPayload.serializer(), audio))
        assertEquals(PayloadKind.CALL_OFFER, decodedOffer.kind)
        assertEquals("call-1", decodedOffer.callId)
        assertEquals(7L, decodedAudio.audioSeq)
        assertEquals("QUJD", decodedAudio.audioBase64)
    }

    @Test
    fun `group delete round-trips`() {
        val delete = ChatPayload("g1", 1L, PayloadKind.GROUP_DELETE, groupId = "group-9")
        val decoded = json.decodeFromString(ChatPayload.serializer(), json.encodeToString(ChatPayload.serializer(), delete))
        assertEquals(PayloadKind.GROUP_DELETE, decoded.kind)
        assertEquals("group-9", decoded.groupId)
    }

    @Test
    fun `heartbeat ping round-trips`() {
        val ping = ChatPayload("p1", 1L, PayloadKind.PING)
        val decoded = json.decodeFromString(ChatPayload.serializer(), json.encodeToString(ChatPayload.serializer(), ping))
        assertEquals(PayloadKind.PING, decoded.kind)
    }

    @Test
    fun `per-payload profile stamp round-trips and defaults to null for old peers`() {
        val stamped = ChatPayload("m7", 1L, PayloadKind.TEXT, text = "x", senderNickname = "Jerry", senderAvatarHash = "ab12")
        val decoded = json.decodeFromString(ChatPayload.serializer(), json.encodeToString(ChatPayload.serializer(), stamped))
        assertEquals("Jerry", decoded.senderNickname)
        assertEquals("ab12", decoded.senderAvatarHash)
        val oldPeer = json.decodeFromString(ChatPayload.serializer(), """{"messageId":"m8","sentAt":1,"kind":"TEXT","text":"x"}""")
        assertNull(oldPeer.senderNickname)
        assertNull(oldPeer.senderAvatarHash)
        assertNull(oldPeer.callId)
    }
}
