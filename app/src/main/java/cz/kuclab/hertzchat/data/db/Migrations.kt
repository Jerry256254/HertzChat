package cz.kuclab.hertzchat.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Real migrations (as opposed to [androidx.room.RoomDatabase.Builder.fallbackToDestructiveMigration],
 * which just deletes and recreates everything) start here, from version 4 -
 * every schema bump before this one shipped destructively, wiping contacts
 * and message history on every update. That's a real cost (re-adding every
 * contact by hand) that should only ever happen once more for anyone still
 * behind version 4 - this migration is what keeps it from happening again
 * for everyone else going forward.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // The transport moved from Tor onion addresses to I2P destinations - same
        // column, new name and meaning, so a rename preserves everything else on
        // the row (nickname, trust, avatar, blocked/pinned state, prekeys...).
        // Existing addresses are stale onion strings, not valid I2P destinations,
        // but that's unavoidable when the underlying network changes; the contact
        // itself, and the Signal session already established with them, survives.
        db.execSQL("ALTER TABLE contacts RENAME COLUMN onionAddress TO i2pDestination")
    }
}

/** Arbitrary file attachments need the original filename to display (and to open with the right app). */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN mediaFileName TEXT")
    }
}

/** Image attachments in assistant conversations. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE assistant_messages ADD COLUMN mediaPath TEXT")
    }
}

/**
 * Lets a group's owner add or remove members. Existing local groups predate the concept
 * of an owner, so they default to blank rather than guessing this device is the owner -
 * an empty ownerId never matches anyone's contactId, so pre-existing groups simply show
 * no management controls to anyone until they're recreated, rather than every member's
 * device wrongly granting itself owner rights over a group it didn't create.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE groups ADD COLUMN ownerId TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * The Mistral AI assistant is gone (replaced by the Hertz web assistant, which keeps
 * no local history), so its two conversation tables go with it. Plain DROPs - there is
 * nothing in them worth preserving or converting.
 *
 * Deliberately leaves the `contacts.allowsMistralAccess` and `messages.fromAssistant`
 * columns in place: dropping a column needs SQLite 3.35+, which older supported
 * devices don't have, and rebuilding both tables just to remove two unread booleans
 * risks real user data for zero benefit. The columns are simply never read anymore.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS assistant_messages")
        db.execSQL("DROP TABLE IF EXISTS assistant_conversations")
    }
}

/**
 * Unread dots on the chat list need a per-thread "seen up to" watermark. New table only,
 * so there is nothing to backfill: threads without a row simply treat every incoming
 * message as unread until first opened.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS thread_read_state (threadId TEXT NOT NULL PRIMARY KEY, lastSeenAt INTEGER NOT NULL)")
    }
}

/**
 * Pinned chats can be reordered by hand. Both threads tables gain a position
 * column; existing pins all default to 0 and keep their previous relative
 * order through the last-message tiebreak in the list sort.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE contacts ADD COLUMN pinOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE groups ADD COLUMN pinOrder INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * From the last I2P version (0.39): only the sender sequence is new, the
 * address column is already correct.
 */
val MIGRATION_11_13 = object : Migration(11, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN seq INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * From the short-lived relay experiment (0.40): the address column it renamed
 * to `nostrPubkey` is renamed back, which also restores the original I2P
 * addresses for every contact that was never re-added in 0.40 - those chats
 * work immediately again with no re-scan. (Contacts re-added under 0.40 hold
 * a relay key there instead; those few need one more scan.)
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE contacts RENAME COLUMN nostrPubkey TO i2pDestination")
    }
}

/** Google-free wake-up pings: each contact row gains the peer's random ntfy topic (null until exchanged over P2P). */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE contacts ADD COLUMN pushTopic TEXT")
    }
}
