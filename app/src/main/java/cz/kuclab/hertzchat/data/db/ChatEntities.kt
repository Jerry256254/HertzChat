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
    /** Their broadcasted preference (as last told to us), gating whether @Mistral can read their messages - see P2pChatService group/AI logic. Defaults to true until we hear otherwise. */
    val allowsMistralAccess: Boolean = true,
)

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val groupId: String,
    val name: String,
    val pinned: Boolean = false,
    val createdAt: Long,
)

/** One row per *other* member (the local user is implicitly a member of every group it has locally). */
@Entity(tableName = "group_members", primaryKeys = ["groupId", "contactId"])
data class GroupMemberEntity(
    val groupId: String,
    val contactId: String,
    val nickname: String,
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val messageId: String,
    /** For a 1:1 chat this is the contact's id; for a group chat this is the group's id - both are just "which thread does this belong to". */
    val contactId: String,
    val fromMe: Boolean,
    val type: MessageType,
    val text: String? = null,
    val mediaPath: String? = null,
    val mediaMimeType: String? = null,
    val mediaDurationMs: Long? = null,
    val timestamp: Long,
    val deliveryState: DeliveryState,
    /** Who actually authored this in a group thread - null for 1:1 messages (the thread's contactId already says who) and for our own outgoing messages. */
    val senderContactId: String? = null,
    /** True if this message is Mistral's reply (relayed into the thread by whoever invoked @Mistral), rendered with the assistant's identity instead of any human sender's. */
    val fromAssistant: Boolean = false,
    /** Comma-separated contactIds (or the assistant's synthetic id) that got @mentioned in this message, for notification purposes. */
    val mentionedContactIds: String? = null,
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

    @Query("UPDATE contacts SET allowsMistralAccess = :allowed WHERE contactId = :id")
    suspend fun setAllowsMistralAccess(id: String, allowed: Boolean)

    @Query("DELETE FROM contacts WHERE contactId = :id")
    suspend fun delete(id: String)
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY pinned DESC, createdAt DESC")
    fun observeGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE groupId = :id")
    suspend fun find(id: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE groupId = :id")
    fun observeGroup(id: String): Flow<GroupEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: GroupEntity)

    @Query("UPDATE groups SET pinned = :pinned WHERE groupId = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("DELETE FROM groups WHERE groupId = :id")
    suspend fun delete(id: String)
}

@Dao
interface GroupMemberDao {
    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    fun observeMembers(groupId: String): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    suspend fun findMembers(groupId: String): List<GroupMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(member: GroupMemberEntity)

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun deleteAllForGroup(groupId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE contactId = :threadId ORDER BY timestamp ASC")
    fun observeMessages(threadId: String): Flow<List<MessageEntity>>

    @Query(
        "SELECT * FROM messages WHERE contactId = :threadId ORDER BY timestamp DESC LIMIT 1",
    )
    suspend fun lastMessage(threadId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE contactId = :threadId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recentForThread(threadId: String, limit: Int): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Query("UPDATE messages SET deliveryState = :state WHERE messageId = :id")
    suspend fun updateState(id: String, state: DeliveryState)

    @Query("SELECT * FROM messages WHERE fromMe = 1 AND deliveryState = 'PENDING' ORDER BY timestamp ASC")
    suspend fun findAllPending(): List<MessageEntity>

    @Query("DELETE FROM messages WHERE contactId = :threadId")
    suspend fun deleteAllForContact(threadId: String)
}
