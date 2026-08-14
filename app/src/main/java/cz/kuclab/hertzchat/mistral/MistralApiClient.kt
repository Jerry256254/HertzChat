package cz.kuclab.hertzchat.mistral

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val MISTRAL_SYSTEM_PROMPT = """Jsi AI asistent dostupný v aplikaci KucLab Hertz Chat -
peer-to-peer, end-to-end šifrované chatovací appce bez serveru. Odpovídej stručně, věcně a v jazyce,
ve kterém se s tebou uživatel baví.

DŮLEŽITÉ - o sobě mluv pravdivě, i když se to o zbytku appky neříká:
- NEBĚŽÍŠ lokálně v zařízení. Jsi model Mistral AI běžící na serverech Mistral AI (cloud).
- Zprávy v této konverzaci appka posílá přes internet do Mistral API pomocí API klíče,
  který si uživatel sám nastavil. Jsi jediná část appky, kde obsah zprávy záměrně
  opouští zařízení - všechno ostatní (chaty s lidmi, skupiny, média) je end-to-end
  šifrované a nikam se neposílá.
- Nikdy netvrď, že jsi lokální model, že běžíš v zařízení, ani že tahle konverzace
  je bez serverů nebo end-to-end šifrovaná. Není.

Nepředstírej ani další schopnosti, které nemáš (např. přístup k obsahu appky mimo tuto
konverzaci, pokud ti není výslovně poskytnut)."""

private const val CHAT_COMPLETIONS_URL = "https://api.mistral.ai/v1/chat/completions"

/** [imageDataUri] is a `data:image/...;base64,...` URI - only meaningful on a "user" message, and only sent to vision-capable models. */
data class MistralMessage(val role: String, val content: String, val imageDataUri: String? = null)

@Serializable
private data class ChatRequest(val model: String, val messages: List<ChatRequestMessage>, val stream: Boolean = false)

// Content is always the array form (even for plain text) rather than a bare string,
// which OpenAI-compatible APIs including Mistral's accept either way - using one shape
// uniformly avoids needing a polymorphic serializer to handle "sometimes string,
// sometimes array" depending on whether a given message has an image attached.
@Serializable
private data class ChatRequestMessage(val role: String, val content: List<ChatContentPart>)

@Serializable
private data class ChatContentPart(
    val type: String,
    val text: String? = null,
    @SerialName("image_url") val imageUrl: ChatImageUrl? = null,
)

@Serializable
private data class ChatImageUrl(val url: String)

private fun MistralMessage.toRequestMessage(): ChatRequestMessage = ChatRequestMessage(
    role = role,
    content = buildList {
        add(ChatContentPart(type = "text", text = content))
        imageDataUri?.let { add(ChatContentPart(type = "image_url", imageUrl = ChatImageUrl(it))) }
    },
)

@Serializable
private data class ChatResponse(val choices: List<ChatChoice> = emptyList())

@Serializable
private data class ChatChoice(val message: ChatResponseMessage)

@Serializable
private data class ChatResponseMessage(val content: String)

@Serializable
private data class ChatStreamChunk(val choices: List<ChatStreamChoice> = emptyList())

@Serializable
private data class ChatStreamChoice(val delta: ChatStreamDelta = ChatStreamDelta())

@Serializable
private data class ChatStreamDelta(val content: String? = null)

/**
 * Talks to the Mistral chat completions API using whichever of the user's
 * own API keys works. There's no server of ours in this path at all - each
 * device calls Mistral directly with the key the user pasted into Settings.
 */
@Singleton
class MistralApiClient @Inject constructor(private val keyStore: MistralKeyStore) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Tries every configured key in order and returns the first success.
     * Stateless by design - each call starts again from key #1, matching
     * "if the first doesn't work, try the next" rather than remembering a
     * rotating cursor across calls.
     */
    suspend fun chat(messages: List<MistralMessage>): Result<String> = withContext(Dispatchers.IO) {
        val keys = keyStore.currentKeys
        if (keys.isEmpty()) return@withContext Result.failure(IllegalStateException("Nemáš přidaný žádný Mistral API klíč - přidej ho v Nastavení."))

        val model = keyStore.currentModel
        val body = json.encodeToString(ChatRequest(model, messages.map { it.toRequestMessage() }))

        var lastError: String? = null
        for (key in keys) {
            val result = runCatching { callOnce(key, body) }
            if (result.isSuccess) return@withContext Result.success(result.getOrThrow())
            lastError = result.exceptionOrNull()?.message
        }
        Result.failure(IllegalStateException("Všech ${keys.size} nastavených klíčů selhalo. Poslední chyba: ${lastError ?: "neznámá"}"))
    }

    /**
     * Same failover-across-keys behavior as [chat], but streams tokens to [onToken] as
     * they arrive instead of waiting for the full reply. Only moves on to the next key
     * if a given key fails before producing any tokens at all - once a key has started
     * streaming visible output, retrying a different key would mean showing a second,
     * unrelated reply glued onto the first, which reads as more broken than just letting
     * that attempt's output stand.
     */
    suspend fun chatStream(messages: List<MistralMessage>, onToken: (String) -> Unit): Result<String> = withContext(Dispatchers.IO) {
        val keys = keyStore.currentKeys
        if (keys.isEmpty()) return@withContext Result.failure(IllegalStateException("Nemáš přidaný žádný Mistral API klíč - přidej ho v Nastavení."))

        val model = keyStore.currentModel
        val body = json.encodeToString(ChatRequest(model, messages.map { it.toRequestMessage() }, stream = true))

        var lastError: String? = null
        for (key in keys) {
            val builder = StringBuilder()
            val result = runCatching { callOnceStream(key, body) { token -> builder.append(token); onToken(token) } }
            if (result.isSuccess) return@withContext Result.success(builder.toString().trim())
            if (builder.isNotEmpty()) return@withContext Result.success(builder.toString().trim())
            lastError = result.exceptionOrNull()?.message
        }
        Result.failure(IllegalStateException("Všech ${keys.size} nastavených klíčů selhalo. Poslední chyba: ${lastError ?: "neznámá"}"))
    }

    private fun callOnceStream(apiKey: String, body: String, onToken: (String) -> Unit) {
        val connection = URL(CHAT_COMPLETIONS_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "text/event-stream")
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
            if (connection.responseCode !in 200..299) {
                val errorBody = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                error("HTTP ${connection.responseCode}${errorBody?.let { ": $it" } ?: ""}")
            }
            connection.inputStream.bufferedReader().use { reader ->
                var line: String?
                var gotAnyChunk = false
                while (reader.readLine().also { line = it } != null) {
                    val data = line?.removePrefix("data:")?.trim() ?: continue
                    if (data.isEmpty()) continue
                    if (data == "[DONE]") break
                    val chunk = runCatching { json.decodeFromString(ChatStreamChunk.serializer(), data) }.getOrNull() ?: continue
                    val delta = chunk.choices.firstOrNull()?.delta?.content
                    if (!delta.isNullOrEmpty()) {
                        gotAnyChunk = true
                        onToken(delta)
                    }
                }
                if (!gotAnyChunk) error("Odpověď neobsahovala žádný text")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun callOnce(apiKey: String, body: String): String {
        val connection = URL(CHAT_COMPLETIONS_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
            if (connection.responseCode !in 200..299) {
                val errorBody = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                error("HTTP ${connection.responseCode}${errorBody?.let { ": $it" } ?: ""}")
            }
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val parsed = json.decodeFromString(ChatResponse.serializer(), responseBody)
            return parsed.choices.firstOrNull()?.message?.content?.trim()
                ?: error("Odpověď neobsahovala žádný text")
        } finally {
            connection.disconnect()
        }
    }
}
