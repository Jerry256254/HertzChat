package cz.kuclab.hertzchat.network.relay

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * The Nostr wire codec (NIP-01), trimmed to exactly what the relay transport
 * uses: publishing one signed ephemeral event, subscribing to our own key,
 * and parsing what comes back. Pure Kotlin + kotlinx.serialization, so the
 * JVM unit tests cover the bytes the app actually sends.
 *
 * Everything Hertz Chat publishes uses [HERTZ_KIND], which sits in the
 * ephemeral range (20000-29999): relays MUST NOT store such events, they only
 * forward them to currently-subscribed clients. A relay that stores nothing
 * can't leak metadata later - there is nothing to leak.
 */
object NostrProtocol {

    /** Our event kind. Ephemeral (never stored), in no other app's use. */
    const val HERTZ_KIND = 27272

    /** Tag carrying the recipient's long-term key (hex). Every event has one. */
    const val TAG_RECIPIENT = "p"

    /** Tag carrying the pairwise routing tag for contact traffic (see [NostrCrypto.pairRoutingTag]). */
    const val TAG_ROUTE = "x"

    /** Tag marking a sealed friend request/response; value is the ephemeral X25519 pubkey (hex). */
    const val TAG_SEALED = "f"

    private val json = Json { ignoreUnknownKeys = true }

    data class NostrEvent(
        val idHex: String,
        val pubkeyHex: String,
        val createdAt: Long,
        val kind: Int,
        val tags: List<List<String>>,
        val content: String,
        val sigHex: String,
    ) {
        fun tagValue(name: String): String? = tags.firstOrNull { it.firstOrNull() == name }?.getOrNull(1)
    }

    sealed interface ServerMessage {
        data class Event(val subscriptionId: String, val event: NostrEvent) : ServerMessage
        data class EndOfStored(val subscriptionId: String) : ServerMessage
        data class Notice(val text: String) : ServerMessage
        data class Closed(val subscriptionId: String, val reason: String) : ServerMessage
        data class Ok(val eventId: String, val accepted: Boolean, val message: String) : ServerMessage
        data object Unknown : ServerMessage
    }

    /**
     * Builds and signs a publishable event. The sender key SHOULD be ephemeral
     * (fresh [NostrCrypto.generateSecretKey] per event): the signature then
     * proves only self-consistency, and the relay can neither name the sender
     * nor link two events to the same device. Real authentication happens one
     * layer up - inside the Signal-encrypted payload, or via the sealed
     * handshake for strangers.
     */
    fun buildSignedEvent(
        senderSecret: ByteArray,
        kind: Int,
        tags: List<List<String>>,
        content: String,
        createdAtSec: Long = System.currentTimeMillis() / 1000,
    ): NostrEvent {
        val pubkeyHex = NostrCrypto.bytesToHex(NostrCrypto.pubkeyFromSecret(senderSecret))
        val idHex = NostrCrypto.bytesToHex(
            NostrCrypto.sha256(canonicalEventBytes(pubkeyHex, createdAtSec, kind, tags, content)),
        )
        val sigHex = NostrCrypto.bytesToHex(
            NostrCrypto.schnorrSign(senderSecret, NostrCrypto.hexToBytes(idHex)),
        )
        return NostrEvent(idHex, pubkeyHex, createdAtSec, kind, tags, content, sigHex)
    }

    /** The exact bytes the event id commits to: `[0,pubkey,created_at,kind,tags,content]` with Nostr's minimal JSON escaping. */
    internal fun canonicalEventBytes(
        pubkeyHex: String,
        createdAtSec: Long,
        kind: Int,
        tags: List<List<String>>,
        content: String,
    ): ByteArray = buildString {
        append("[0,\"")
        append(pubkeyHex)
        append("\",")
        append(createdAtSec)
        append(",")
        append(kind)
        append(",[")
        tags.forEachIndexed { tagIndex, tag ->
            if (tagIndex > 0) append(",")
            append("[")
            tag.forEachIndexed { valueIndex, value ->
                if (valueIndex > 0) append(",")
                append("\"")
                appendJsonEscaped(value)
                append("\"")
            }
            append("]")
        }
        append("],\"")
        appendJsonEscaped(content)
        append("\"]")
    }.encodeToByteArray()

    private fun StringBuilder.appendJsonEscaped(value: String) {
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
    }

    /** `["EVENT", {...}]` ready to send over the socket. */
    fun clientPublishJson(event: NostrEvent): String = buildString {
        append("[\"EVENT\",")
        append(eventToJson(event))
        append("]")
    }

    /** `["REQ", subId, {"kinds":[...],"#p":[...]}]`. */
    fun clientSubscribeJson(subscriptionId: String, kinds: List<Int>, recipientPubkeys: List<String>): String {
        val filter = JsonObject(
            mapOf(
                "kinds" to JsonArray(kinds.map { JsonPrimitive(it) }),
                "#p" to JsonArray(recipientPubkeys.map { JsonPrimitive(it) }),
            ),
        )
        return "[\"REQ\"," + json.encodeToString(JsonPrimitive(subscriptionId)) + "," + json.encodeToString(JsonObject.serializer(), filter) + "]"
    }

    fun clientCloseJson(subscriptionId: String): String =
        "[\"CLOSE\"," + json.encodeToString(JsonPrimitive(subscriptionId)) + "]"

    /** Recomputes the id and checks the Schnorr signature - a relay must never be able to forge traffic for us. */
    fun verifyEvent(event: NostrEvent): Boolean = runCatching {
        val recomputed = NostrCrypto.bytesToHex(
            NostrCrypto.sha256(
                canonicalEventBytes(event.pubkeyHex, event.createdAt, event.kind, event.tags, event.content),
            ),
        )
        if (!recomputed.equals(event.idHex, ignoreCase = true)) return false
        NostrCrypto.verify(
            NostrCrypto.hexToBytes(event.pubkeyHex),
            NostrCrypto.hexToBytes(event.idHex),
            NostrCrypto.hexToBytes(event.sigHex),
        )
    }.getOrDefault(false)

    fun parseServerMessage(text: String): ServerMessage = runCatching {
        val array = json.parseToJsonElement(text).jsonArray
        val verb = array.getOrNull(0)?.jsonPrimitive?.content ?: return ServerMessage.Unknown
        when (verb) {
            "EVENT" -> {
                val event = parseEventObject(array.getOrNull(2)?.jsonObject ?: return ServerMessage.Unknown)
                    ?: return ServerMessage.Unknown
                ServerMessage.Event(array.getOrNull(1)?.jsonPrimitive?.content.orEmpty(), event)
            }
            "EOSE" -> ServerMessage.EndOfStored(array.getOrNull(1)?.jsonPrimitive?.content.orEmpty())
            "NOTICE" -> ServerMessage.Notice(array.getOrNull(1)?.jsonPrimitive?.content.orEmpty())
            "CLOSED" -> ServerMessage.Closed(
                array.getOrNull(1)?.jsonPrimitive?.content.orEmpty(),
                array.getOrNull(2)?.jsonPrimitive?.content.orEmpty(),
            )
            "OK" -> ServerMessage.Ok(
                array.getOrNull(1)?.jsonPrimitive?.content.orEmpty(),
                array.getOrNull(2)?.jsonPrimitive?.content == "true",
                array.getOrNull(3)?.jsonPrimitive?.content.orEmpty(),
            )
            else -> ServerMessage.Unknown
        }
    }.getOrDefault(ServerMessage.Unknown)

    private fun parseEventObject(obj: JsonObject): NostrEvent? = runCatching {
        NostrEvent(
            idHex = obj.getValue("id").jsonPrimitive.content,
            pubkeyHex = obj.getValue("pubkey").jsonPrimitive.content,
            createdAt = obj.getValue("created_at").jsonPrimitive.longOrNull ?: 0L,
            kind = obj.getValue("kind").jsonPrimitive.content.toInt(),
            tags = obj.getValue("tags").jsonArray.map { tag -> tag.jsonArray.map { it.jsonPrimitive.content } },
            content = obj.getValue("content").jsonPrimitive.content,
            sigHex = obj.getValue("sig").jsonPrimitive.content,
        )
    }.getOrNull()

    internal fun eventToJson(event: NostrEvent): String {
        val obj = JsonObject(
            mapOf(
                "id" to JsonPrimitive(event.idHex),
                "pubkey" to JsonPrimitive(event.pubkeyHex),
                "created_at" to JsonPrimitive(event.createdAt),
                "kind" to JsonPrimitive(event.kind),
                "tags" to JsonArray(event.tags.map { tag -> JsonArray(tag.map { JsonPrimitive(it) }) }),
                "content" to JsonPrimitive(event.content),
                "sig" to JsonPrimitive(event.sigHex),
            ),
        )
        return json.encodeToString(JsonObject.serializer(), obj)
    }
}
