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
}
