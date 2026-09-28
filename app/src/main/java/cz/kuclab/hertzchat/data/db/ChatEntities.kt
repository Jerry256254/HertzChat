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
    /** The contact's long-term relay key (64 hex chars) - where sealed requests and routed traffic are published. */
    val nostrPubkey: String,
    val avatarPath: String? = null,
    val pinned: Boolean = false,
    /** Position inside the pinned section (lower floats higher); only meaningful while [pinned] is true. */
    val pinOrder: Int = 0,
    val blocked: Boolean = false,
    val addedAt: Long,
    val lastSeenOnlineAt: Long? = null,
    /** Legacy "@Mistral may read my messages" preference from the removed Mistral assistant - column kept for schema stability (see MIGRATION_8_9), never read or written. */
    val allowsMistralAccess: Boolean = true,
)

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val groupId: String,
    val name: String,
    val pinned: Boolean = false,
    /** Position inside the pinned section (lower floats higher); only meaningful while [pinned] is true. */
    val pinOrder: Int = 0,
    val createdAt: Long,
    /** Whoever created the group - the only member allowed to add or remove others (enforced by every recipient checking this before applying a roster update, not by any server). */
    val ownerId: String = "",
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
    /** Original filename, kept for arbitrary file attachments so they can be shown and opened by name. */
    val mediaFileName: String? = null,
    val mediaDurationMs: Long? = null,
    val timestamp: Long,
    /** Sender-side sequence within the thread - the tiebreak after [timestamp] that keeps send order exact. */
    val seq: Long = 0,
    val deliveryState: DeliveryState,
    /** Who actually authored this in a group thread - null for 1:1 messages (the thread's contactId already says who) and for our own outgoing messages. */
    val senderContactId: String? = null,
    /** Legacy marker from the removed @Mistral invocation - old rows may still have it set, but every message now renders as a normal human message. Column kept for schema stability (see MIGRATION_8_9). */
    val fromAssistant: Boolean = false,
    /** Comma-separated contactIds that got @mentioned in this message, for notification purposes. */
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

    /** Live row for an open chat - nickname/avatar/QR refresh when the peer's PROFILE_UPDATE or AVATAR lands. */
    @Query("SELECT * FROM contacts WHERE contactId = :id")
    fun observeContact(id: String): Flow<ContactEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity)

    @Update
    suspend fun update(contact: ContactEntity)

    @Query("UPDATE contacts SET pinned = :pinned WHERE contactId = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE contacts SET pinOrder = :order WHERE contactId = :id")
    suspend fun setPinOrder(id: String, order: Int)

    @Query("UPDATE contacts SET blocked = :blocked WHERE contactId = :id")
    suspend fun setBlocked(id: String, blocked: Boolean)

    @Query("DELETE FROM contacts WHERE contactId = :id")
    suspend fun delete(id: String)
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY pinned DESC, createdAt DESC")
    fun observeGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE groupId = :id")
    suspend fun find(id: String): GroupEntity?

    @Query("SELECT * FROM groups")
    suspend fun allGroups(): List<GroupEntity>

    @Query("SELECT * FROM groups WHERE groupId = :id")
    fun observeGroup(id: String): Flow<GroupEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: GroupEntity)

    @Query("UPDATE groups SET pinned = :pinned WHERE groupId = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE groups SET pinOrder = :order WHERE groupId = :id")
    suspend fun setPinOrder(id: String, order: Int)

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

    @Query("DELETE FROM group_members WHERE groupId = :groupId AND contactId = :contactId")
    suspend fun deleteMember(groupId: String, contactId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE contactId = :threadId ORDER BY timestamp ASC, seq ASC")
    fun observeMessages(threadId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE messageId = :id")
    suspend fun find(id: String): MessageEntity?

    @Query(
        "SELECT * FROM messages WHERE contactId = :threadId ORDER BY timestamp DESC, seq DESC LIMIT 1",
    )
    suspend fun lastMessage(threadId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE contactId = :threadId ORDER BY timestamp DESC, seq DESC LIMIT :limit")
    suspend fun recentForThread(threadId: String, limit: Int): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Query("UPDATE messages SET deliveryState = :state WHERE messageId = :id")
    suspend fun updateState(id: String, state: DeliveryState)

    /** Everything still awaiting a delivery receipt - never-sent PENDING plus SENT-but-unacked. */
    @Query("SELECT * FROM messages WHERE fromMe = 1 AND deliveryState IN ('PENDING', 'SENT') ORDER BY timestamp ASC, seq ASC")
    suspend fun findUnsent(): List<MessageEntity>

    /** Incoming messages in one thread that arrived but were never marked read - what READ_ACKs go out for. */
    @Query("SELECT * FROM messages WHERE contactId = :threadId AND fromMe = 0 AND deliveryState = 'DELIVERED' ORDER BY timestamp ASC, seq ASC")
    suspend fun findDeliveredIncoming(threadId: String): List<MessageEntity>

    /**
     * Recent messages across all threads, incoming and outgoing alike. The chat list
     * derives both the per-row preview and the unread dots from this one flow - it must
     * re-emit on our own sends too, or the preview freezes the moment we write first.
     * Capped, so a huge history can't stall the list.
     */
    @Query("SELECT * FROM messages ORDER BY timestamp DESC, seq DESC LIMIT 500")
    fun observeRecent(): Flow<List<MessageEntity>>

    /** Case-insensitive substring search within one thread, for in-chat find. */
    @Query("SELECT * FROM messages WHERE contactId = :threadId AND text LIKE '%' || :query || '%' ESCAPE '\\' ORDER BY timestamp ASC, seq ASC")
    suspend fun searchInThread(threadId: String, query: String): List<MessageEntity>

    @Query("DELETE FROM messages WHERE contactId = :threadId")
    suspend fun deleteAllForContact(threadId: String)
}
