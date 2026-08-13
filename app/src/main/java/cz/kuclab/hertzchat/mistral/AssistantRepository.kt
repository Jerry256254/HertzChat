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
 * goes over Tor or through a Signal session at all.
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
        conversationDao.upsert(AssistantConversationEntity(id, title = "Nová konverzace", createdAt = now, lastMessageAt = now))
        return id
    }

    fun send(text: String) {
        scope.launch {
            val conversationId = ensureActiveConversation()
            val now = System.currentTimeMillis()
            messageDao.upsert(AssistantMessageEntity(UUID.randomUUID().toString(), conversationId, AssistantRole.USER, text, now))
            conversationDao.touch(conversationId, now)
            maybeRetitle(conversationId, text)

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
        }
    }

    /** First user message in a conversation becomes its title in the /chats picker, instead of every entry saying "Nová konverzace". */
    private suspend fun maybeRetitle(conversationId: String, firstUserText: String) {
        val conversation = conversationDao.find(conversationId) ?: return
        if (conversation.title != "Nová konverzace") return
        val title = firstUserText.take(40).let { if (firstUserText.length > 40) "$it…" else it }
        conversationDao.upsert(conversation.copy(title = title))
    }
}
