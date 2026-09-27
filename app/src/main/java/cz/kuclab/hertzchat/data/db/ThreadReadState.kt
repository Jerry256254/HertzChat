package cz.kuclab.hertzchat.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * The newest message timestamp this device has actually displayed per thread -
 * anything incoming and newer than it counts as unread (the dot on the chat list).
 * Written whenever a chat screen shows messages, never sent anywhere.
 */
@Entity(tableName = "thread_read_state")
data class ThreadReadStateEntity(
    @PrimaryKey val threadId: String,
    val lastSeenAt: Long,
)

@Dao
interface ThreadReadStateDao {
    @Query("SELECT * FROM thread_read_state")
    fun observeAll(): Flow<List<ThreadReadStateEntity>>

    @Query("SELECT lastSeenAt FROM thread_read_state WHERE threadId = :threadId")
    suspend fun lastSeenAt(threadId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: ThreadReadStateEntity)
}
