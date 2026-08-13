package cz.kuclab.hertzchat.di

import android.content.Context
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import cz.kuclab.hertzchat.crypto.IdentityKeyManager
import cz.kuclab.hertzchat.crypto.RoomSignalProtocolStore
import cz.kuclab.hertzchat.data.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.security.SecureRandom
import javax.inject.Singleton
import net.sqlcipher.database.SupportFactory

private const val DB_PREFS_NAME = "hertzchat_db_prefs"
private const val KEY_DB_PASSPHRASE = "db_passphrase"

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** Random passphrase, itself stored only inside a Keystore-wrapped EncryptedSharedPreferences file - never written to disk in the clear. */
    private fun dbPassphrase(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val prefs = EncryptedSharedPreferences.create(
            context,
            DB_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        val existing = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (existing != null) return android.util.Base64.decode(existing, android.util.Base64.NO_WRAP)

        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_DB_PASSPHRASE, android.util.Base64.encodeToString(fresh, android.util.Base64.NO_WRAP)).apply()
        return fresh
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        net.sqlcipher.database.SQLiteDatabase.loadLibs(context)
        val factory = SupportFactory(dbPassphrase(context))
        return Room.databaseBuilder(context, AppDatabase::class.java, "hertzchat.db")
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideContactDao(database: AppDatabase) = database.contactDao()

    @Provides
    fun provideMessageDao(database: AppDatabase) = database.messageDao()

    @Provides
    fun provideAssistantConversationDao(database: AppDatabase) = database.assistantConversationDao()

    @Provides
    fun provideAssistantMessageDao(database: AppDatabase) = database.assistantMessageDao()

    @Provides
    fun provideGroupDao(database: AppDatabase) = database.groupDao()

    @Provides
    fun provideGroupMemberDao(database: AppDatabase) = database.groupMemberDao()

    @Provides
    @Singleton
    fun provideSignalProtocolStore(
        database: AppDatabase,
        identityKeyManager: IdentityKeyManager,
    ): RoomSignalProtocolStore = RoomSignalProtocolStore(
        localIdentity = identityKeyManager.identityKeyPair(),
        localRegistrationId = identityKeyManager.registrationId(),
        sessionDao = database.sessionDao(),
        identityDao = database.identityDao(),
        preKeyDao = database.preKeyDao(),
        signedPreKeyDao = database.signedPreKeyDao(),
        kyberPreKeyDao = database.kyberPreKeyDao(),
    )
}
