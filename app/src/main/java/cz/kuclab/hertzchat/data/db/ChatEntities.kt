package cz.kuclab.hertzchat.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class MessageType { TEXT, IMAGE, VIDEO, VOICE, FILE }
enum class DeliveryState { PENDING, SENDING, SENT, DELIVERED, READ, FAILED }

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val contactId: String, // stable fingerprint of the contact's identity key
    val nickname: String,
    val identityKeyBytes: ByteArray,
    val onionAddress: String,
    val avatarPath: String? = null,
    val pinned: Boolean = false,
    val blocked: Boolean = false,
    val addedAt: Long,
    val lastSeenOnlineAt: Long? = null,
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val contactId: String,
    val fromMe: Boolean,
    val type: MessageType,
    val text: String? = null,
    val mediaPath: String? = null,
    val mediaMimeType: String? = null,
    val mediaDurationMs: Long? = null,
    val timestamp: Long,
    val deliveryState: DeliveryState,
)

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts WHERE blocked = 0 ORDER BY pinned DESC, addedAt DESC")
    fun observeContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE blocked = 1 ORDER BY addedAt DESC")
    fun observeBlocked(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE contactId = :id")
    suspend fun find(id: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity)

    @Update
    suspend fun update(contact: ContactEntity)

    @Query("UPDATE contacts SET pinned = :pinned WHERE contactId = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE contacts SET blocked = :blocked WHERE contactId = :id")
    suspend fun setBlocked(id: String, blocked: Boolean)

    @Query("DELETE FROM contacts WHERE contactId = :id")
    suspend fun delete(id: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE contactId = :contactId ORDER BY timestamp ASC")
    fun observeMessages(contactId: String): Flow<List<MessageEntity>>

    @Query(
        "SELECT * FROM messages WHERE contactId = :contactId ORDER BY timestamp DESC LIMIT 1",
    )
    suspend fun lastMessage(contactId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Query("UPDATE messages SET deliveryState = :state WHERE messageId = :id")
    suspend fun updateState(id: String, state: DeliveryState)

    @Query("SELECT * FROM messages WHERE fromMe = 1 AND deliveryState = 'PENDING' ORDER BY timestamp ASC")
    suspend fun findAllPending(): List<MessageEntity>

    @Query("DELETE FROM messages WHERE contactId = :contactId")
    suspend fun deleteAllForContact(contactId: String)
}
