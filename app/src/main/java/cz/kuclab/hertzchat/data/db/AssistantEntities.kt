package cz.kuclab.hertzchat.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

enum class AssistantRole { USER, ASSISTANT, ERROR }

@Entity(tableName = "assistant_conversations")
data class AssistantConversationEntity(
    @PrimaryKey val conversationId: String,
    val title: String,
    val createdAt: Long,
    val lastMessageAt: Long,
)

@Entity(tableName = "assistant_messages")
data class AssistantMessageEntity(
    @PrimaryKey val messageId: String,
    val conversationId: String,
    val role: AssistantRole,
    val text: String,
    val timestamp: Long,
)

@Dao
interface AssistantConversationDao {
    @Query("SELECT * FROM assistant_conversations ORDER BY lastMessageAt DESC")
    fun observeConversations(): Flow<List<AssistantConversationEntity>>

    @Query("SELECT * FROM assistant_conversations ORDER BY lastMessageAt DESC LIMIT 1")
    suspend fun mostRecent(): AssistantConversationEntity?

    @Query("SELECT * FROM assistant_conversations WHERE conversationId = :id")
    suspend fun find(id: String): AssistantConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: AssistantConversationEntity)

    @Query("UPDATE assistant_conversations SET lastMessageAt = :timestamp WHERE conversationId = :id")
    suspend fun touch(id: String, timestamp: Long)

    @Query("DELETE FROM assistant_conversations WHERE conversationId = :id")
    suspend fun delete(id: String)
}

@Dao
interface AssistantMessageDao {
    @Query("SELECT * FROM assistant_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun observeMessages(conversationId: String): Flow<List<AssistantMessageEntity>>

    @Query("SELECT * FROM assistant_messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recentForConversation(conversationId: String, limit: Int): List<AssistantMessageEntity>

    @Query("SELECT * FROM assistant_messages ORDER BY timestamp DESC LIMIT 1")
    suspend fun mostRecentOverall(): AssistantMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: AssistantMessageEntity)

    @Query("UPDATE assistant_messages SET text = :text WHERE messageId = :id")
    suspend fun updateText(id: String, text: String)

    @Query("UPDATE assistant_messages SET role = :role, text = :text WHERE messageId = :id")
    suspend fun updateRoleAndText(id: String, role: AssistantRole, text: String)

    @Query("DELETE FROM assistant_messages WHERE conversationId = :conversationId")
    suspend fun deleteForConversation(conversationId: String)
}
