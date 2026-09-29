package cz.kuclab.hertzchat.p2p

import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Wake-up pings over plain ntfy (https://ntfy.sh/docs), the Google-free
 * half of reliable delivery: when direct P2P to a contact keeps failing
 * (their app was killed hours ago and Android won't let ours reach them),
 * we POST an *empty* ping to their random topic. Their periodic worker
 * polls the same topic, sees a new id, and starts the service so the
 * queued messages can finally flow over I2P.
 *
 * Privacy: the server sees a topic id, timestamps and both sides' IPs -
 * never message content (the body is empty), never identities (topics are
 * 128-bit random and only ever travel over established P2P channels).
 * Zero dependencies: plain [HttpURLConnection], works on degoogled phones.
 *
 * All network calls block - invoke off the main thread.
 */
object NtfyPing {
    const val DEFAULT_SERVER = "https://ntfy.sh"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val MAX_POLL_BYTES = 256 * 1024

    private val json = Json { ignoreUnknownKeys = true }
    private val topicPattern = Regex("^[A-Za-z0-9_-]{1,64}$")

    /** A fresh unguessable inbound topic for this device. */
    fun newTopic(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
    }

    fun isValidTopic(topic: String): Boolean = topicPattern.matches(topic)

    /**
     * POSTs one empty ping. True on any 2xx - the server accepted it for the
     * topic's cache, which is all a wake-up needs. Never throws.
     */
    fun publish(serverUrl: String, topic: String): Boolean {
        if (!isValidTopic(topic)) return false
        return runCatching {
            val url = URL(normalizeServer(serverUrl) + "/" + topic)
            (url.openConnection() as HttpURLConnection).run {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "POST"
                doOutput = true
                setFixedLengthStreamingMode(0)
                connect()
                // Drain the small JSON receipt so the connection can be reused.
                runCatching { inputStream.use { it.readBytes() } }
                val ok = responseCode in 200..299
                disconnect()
                ok
            }
        }.getOrDefault(false)
    }

    /**
     * Returns the newest cached message id on our topic, or null when the
     * cache is empty/unreachable. The caller compares against its stored
     * cursor - a different id means "someone pinged us". Never throws.
     */
    fun pollLatestId(serverUrl: String, topic: String, sinceId: String?): String? {
        if (!isValidTopic(topic)) return null
        return runCatching {
            val query = if (sinceId != null) "?since=$sinceId&poll=1" else "?poll=1"
            val url = URL(normalizeServer(serverUrl) + "/" + topic + "/json" + query)
            (url.openConnection() as HttpURLConnection).run {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                connect()
                if (responseCode !in 200..299) {
                    disconnect()
                    return@runCatching null
                }
                val body = inputStream.use { stream ->
                    val buf = ByteArray(MAX_POLL_BYTES + 1)
                    var read = 0
                    while (read < buf.size) {
                        val n = stream.read(buf, read, buf.size - read)
                        if (n < 0) break
                        read += n
                    }
                    buf.copyOf(minOf(read, MAX_POLL_BYTES)).toString(Charsets.UTF_8)
                }
                disconnect()
                latestIdFromPollBody(body)
            }
        }.getOrNull()
    }

    /**
     * Pure extraction: the last `event: message` id from a newline-delimited
     * JSON poll body. Malformed lines and non-message events (open,
     * keepalive) are skipped - a poll body is server output, never trusted.
     */
    fun latestIdFromPollBody(body: String): String? {
        var latest: String? = null
        for (line in body.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val obj = runCatching { json.parseToJsonElement(trimmed).jsonObject }.getOrNull() ?: continue
            val event = obj["event"]?.jsonPrimitive?.content ?: continue
            if (event != "message") continue
            obj["id"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }?.let { latest = it }
        }
        return latest
    }

    private fun normalizeServer(serverUrl: String): String = serverUrl.trim().trimEnd('/')
}
