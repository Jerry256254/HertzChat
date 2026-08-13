package cz.kuclab.hertzchat.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val address: String,
    val record: ByteArray,
)

@Entity(tableName = "identities")
data class IdentityEntity(
    @PrimaryKey val address: String,
    val identityKey: ByteArray,
    val firstSeenAt: Long,
)

@Entity(tableName = "prekeys")
data class PreKeyEntity(
    @PrimaryKey val id: Int,
    val record: ByteArray,
)

@Entity(tableName = "signed_prekeys")
data class SignedPreKeyEntity(
    @PrimaryKey val id: Int,
    val record: ByteArray,
)

@Entity(tableName = "kyber_prekeys")
data class KyberPreKeyEntity(
    @PrimaryKey val id: Int,
    val record: ByteArray,
    val used: Boolean = false,
)

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE address = :address")
    fun find(address: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE address LIKE :namePrefix || ':%'")
    fun findAllForName(namePrefix: String): List<SessionEntity>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    fun upsert(entity: SessionEntity)

    @Query("DELETE FROM sessions WHERE address = :address")
    fun delete(address: String)

    @Query("DELETE FROM sessions WHERE address LIKE :namePrefix || ':%'")
    fun deleteAllForName(namePrefix: String)
}

@Dao
interface IdentityDao {
    @Query("SELECT * FROM identities WHERE address = :address")
    fun find(address: String): IdentityEntity?

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    fun upsert(entity: IdentityEntity)
}

@Dao
interface PreKeyDao {
    @Query("SELECT * FROM prekeys WHERE id = :id")
    fun find(id: Int): PreKeyEntity?

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    fun upsert(entity: PreKeyEntity)

    @Query("DELETE FROM prekeys WHERE id = :id")
    fun delete(id: Int)

    @Query("SELECT COUNT(*) FROM prekeys")
    fun count(): Int

    @Query("SELECT MAX(id) FROM prekeys")
    fun maxId(): Int?
}

@Dao
interface SignedPreKeyDao {
    @Query("SELECT * FROM signed_prekeys WHERE id = :id")
    fun find(id: Int): SignedPreKeyEntity?

    @Query("SELECT * FROM signed_prekeys")
    fun findAll(): List<SignedPreKeyEntity>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    fun upsert(entity: SignedPreKeyEntity)

    @Query("DELETE FROM signed_prekeys WHERE id = :id")
    fun delete(id: Int)
}

@Dao
interface KyberPreKeyDao {
    @Query("SELECT * FROM kyber_prekeys WHERE id = :id")
    fun find(id: Int): KyberPreKeyEntity?

    @Query("SELECT * FROM kyber_prekeys")
    fun findAll(): List<KyberPreKeyEntity>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    fun upsert(entity: KyberPreKeyEntity)

    @Query("UPDATE kyber_prekeys SET used = 1 WHERE id = :id")
    fun markUsed(id: Int)

    @Query("SELECT MAX(id) FROM kyber_prekeys")
    fun maxId(): Int?
}
