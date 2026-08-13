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

            _sending.value = true
            val history = messageDao.recentForConversation(conversationId, HISTORY_WINDOW).reversed()
            val messages = buildList {
                add(MistralMessage("system", MISTRAL_SYSTEM_PROMPT))
                history.forEach { add(MistralMessage(if (it.role == AssistantRole.USER) "user" else "assistant", it.text)) }
            }
            val result = apiClient.chat(messages)
            _sending.value = false

            val replyTimestamp = System.currentTimeMillis()
            val reply = result.fold(
                onSuccess = { AssistantMessageEntity(UUID.randomUUID().toString(), conversationId, AssistantRole.ASSISTANT, it, replyTimestamp) },
                onFailure = { AssistantMessageEntity(UUID.randomUUID().toString(), conversationId, AssistantRole.ERROR, it.message ?: "Nepodařilo se získat odpověď.", replyTimestamp) },
            )
            messageDao.upsert(reply)
            conversationDao.touch(conversationId, replyTimestamp)

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
        val generated = apiClient.chat(
            listOf(
                MistralMessage("system", TITLE_SYSTEM_PROMPT),
                MistralMessage("user", "Uživatel: $firstUserText\nAsistent: $firstReply"),
            ),
        ).getOrNull()?.trim()?.trim('"', '“', '”')?.take(60)
        val title = generated?.takeIf { it.isNotBlank() } ?: firstUserText.take(40).let { if (firstUserText.length > 40) "$it…" else it }
        conversationDao.upsert(conversation.copy(title = title))
    }

    private companion object {
        const val DEFAULT_TITLE = "Nová konverzace"
        const val TITLE_SYSTEM_PROMPT = "Vymysli krátký název (nejvýše 5 slov, bez uvozovek, bez tečky na konci) shrnující tuto konverzaci, ve stejném jazyce jako konverzace. Odpověz pouze samotným názvem, nic jiného."
    }
}
