package cz.kuclab.hertzchat.mistral

import cz.kuclab.hertzchat.data.db.AssistantConversationDao
import cz.kuclab.hertzchat.data.db.AssistantConversationEntity
import cz.kuclab.hertzchat.data.db.AssistantMessageDao
import cz.kuclab.hertzchat.data.db.AssistantMessageEntity
import cz.kuclab.hertzchat.data.db.AssistantRole
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val HISTORY_WINDOW = 30

/**
 * Owns the assistant's own local-only conversation history and talks to
 * [MistralApiClient] - entirely separate from the P2P/Signal pipeline in
 * [cz.kuclab.hertzchat.data.repository.P2pChatService], since this never
 * goes over I2P or through a Signal session at all.
 */
@Singleton
class AssistantRepository @Inject constructor(
    private val conversationDao: AssistantConversationDao,
    private val messageDao: AssistantMessageDao,
    private val apiClient: MistralApiClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending

    suspend fun ensureActiveConversation(): String {
        _activeConversationId.value?.let { return it }
        val existing = conversationDao.mostRecent()
        val id = existing?.conversationId ?: createConversation()
        _activeConversationId.value = id
        return id
    }

    fun newConversation() {
        scope.launch { _activeConversationId.value = createConversation() }
    }

    fun switchConversation(conversationId: String) {
        _activeConversationId.value = conversationId
    }

    fun deleteConversation(conversationId: String) {
        scope.launch {
            messageDao.deleteForConversation(conversationId)
            conversationDao.delete(conversationId)
            if (_activeConversationId.value == conversationId) {
                _activeConversationId.value = null
                ensureActiveConversation()
            }
        }
    }

    fun renameConversation(conversationId: String, title: String) {
        scope.launch {
            val conversation = conversationDao.find(conversationId) ?: return@launch
            conversationDao.upsert(conversation.copy(title = title.take(60).ifBlank { DEFAULT_TITLE }))
        }
    }

    private suspend fun createConversation(): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        conversationDao.upsert(AssistantConversationEntity(id, title = DEFAULT_TITLE, createdAt = now, lastMessageAt = now))
        return id
    }

    fun send(text: String) {
        scope.launch {
            val conversationId = ensureActiveConversation()
            val now = System.currentTimeMillis()
            messageDao.upsert(AssistantMessageEntity(UUID.randomUUID().toString(), conversationId, AssistantRole.USER, text, now))
            conversationDao.touch(conversationId, now)

            val history = messageDao.recentForConversation(conversationId, HISTORY_WINDOW).reversed()
            val messages = buildList {
                add(MistralMessage("system", MISTRAL_SYSTEM_PROMPT))
                history.forEach { add(MistralMessage(if (it.role == AssistantRole.USER) "user" else "assistant", it.text)) }
            }

            // The reply bubble is inserted empty and up front, then filled in as tokens
            // stream in - _sending only covers the gap before the first token, so the UI's
            // "Mistral přemýšlí..." indicator disappears the moment real text starts appearing.
            val replyId = UUID.randomUUID().toString()
            val replyTimestamp = System.currentTimeMillis()
            _sending.value = true
            messageDao.upsert(AssistantMessageEntity(replyId, conversationId, AssistantRole.ASSISTANT, "", replyTimestamp))
            conversationDao.touch(conversationId, replyTimestamp)

            // callOnceStream() runs its read loop on a single IO thread and calls onToken
            // synchronously from it, so accumulating into this builder and writing it back
            // with runBlocking here is safe and strictly ordered - no concurrent DAO calls
            // racing each other over the same row.
            val accumulated = StringBuilder()
            val result = apiClient.chatStream(messages) { token ->
                if (accumulated.isEmpty()) _sending.value = false
                accumulated.append(token)
                kotlinx.coroutines.runBlocking { messageDao.updateText(replyId, accumulated.toString()) }
            }
            _sending.value = false

            result.fold(
                onSuccess = { full -> messageDao.updateText(replyId, full) },
                onFailure = { e -> messageDao.updateRoleAndText(replyId, AssistantRole.ERROR, e.message ?: "Nepodařilo se získat odpověď.") },
            )
            conversationDao.touch(conversationId, System.currentTimeMillis())

            result.getOrNull()?.let { assistantReply -> maybeGenerateTitle(conversationId, text, assistantReply) }
        }
    }

    /**
     * Rather than just truncating the raw first message, the assistant itself
     * comes up with the conversation's title once it actually knows what the
     * exchange was about - only tried once, right after the first reply.
     */
    private suspend fun maybeGenerateTitle(conversationId: String, firstUserText: String, firstReply: String) {
        val conversation = conversationDao.find(conversationId) ?: return
        if (conversation.title != DEFAULT_TITLE) return
        val fallback = firstUserText.take(40).let { if (firstUserText.length > 40) "$it…" else it }
        val generated = apiClient.chat(
            listOf(
                MistralMessage("system", TITLE_SYSTEM_PROMPT),
                MistralMessage("user", "Uživatel: $firstUserText\nAsistent: $firstReply"),
            ),
        ).getOrNull()?.trim()?.trim('"', '“', '”')?.take(60)
        val title = generated?.takeIf { isUsableTitle(it) } ?: fallback
        conversationDao.upsert(conversation.copy(title = title))
    }

    /**
     * Small/fast models asked to "invent a title" sometimes answer with a description of
     * the task instead of doing it ("Název konverzace", "Chat title", "Souhrn konverzace")
     * - reject anything that looks like that meta-echo rather than an actual title, and
     * fall back to the first message instead of showing that placeholder-like text verbatim.
     */
    private fun isUsableTitle(candidate: String): Boolean {
        if (candidate.isBlank() || candidate.length > 60) return false
        val normalized = candidate.lowercase()
        val metaPhrases = listOf(
            "název konverzace", "název chatu", "conversation title", "chat title",
            "souhrn konverzace", "shrnutí konverzace", "krátký název", "here is a title", "titulek",
        )
        return metaPhrases.none { normalized == it || normalized.contains(it) }
    }

    private companion object {
        const val DEFAULT_TITLE = "Nová konverzace"
        const val TITLE_SYSTEM_PROMPT = "Vymysli krátký, konkrétní název (2 až 5 slov, bez uvozovek, bez tečky na konci) shrnující TÉMA této konverzace, ve stejném jazyce jako konverzace. Odpověz pouze samotným názvem tématu - nikdy neodpovídej obecným popisem jako \"Název konverzace\" nebo \"Souhrn konverzace\"."
    }
}
