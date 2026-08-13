package cz.kuclab.hertzchat.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun fromMessageType(value: MessageType): String = value.name

    @TypeConverter
    fun toMessageType(value: String): MessageType = MessageType.valueOf(value)

    @TypeConverter
    fun fromDeliveryState(value: DeliveryState): String = value.name

    @TypeConverter
    fun toDeliveryState(value: String): DeliveryState = DeliveryState.valueOf(value)

    @TypeConverter
    fun fromAssistantRole(value: AssistantRole): String = value.name

    @TypeConverter
    fun toAssistantRole(value: String): AssistantRole = AssistantRole.valueOf(value)
}

@Database(
    entities = [
        SessionEntity::class,
        IdentityEntity::class,
        PreKeyEntity::class,
        SignedPreKeyEntity::class,
        KyberPreKeyEntity::class,
        ContactEntity::class,
        MessageEntity::class,
        AssistantConversationEntity::class,
        AssistantMessageEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun identityDao(): IdentityDao
    abstract fun preKeyDao(): PreKeyDao
    abstract fun signedPreKeyDao(): SignedPreKeyDao
    abstract fun kyberPreKeyDao(): KyberPreKeyDao
    abstract fun contactDao(): ContactDao
    abstract fun messageDao(): MessageDao
    abstract fun assistantConversationDao(): AssistantConversationDao
    abstract fun assistantMessageDao(): AssistantMessageDao
    abstract fun groupDao(): GroupDao
    abstract fun groupMemberDao(): GroupMemberDao
}
