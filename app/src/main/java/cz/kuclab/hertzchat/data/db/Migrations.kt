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
