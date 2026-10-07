package app.mosaic.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        CategoryEntity::class,
        StreamEntity::class,
        EpisodeEntity::class,
        ProgrammeEntity::class,
        FavoriteEntity::class,
        HistoryEntity::class,
        HiddenCategoryEntity::class,
        DownloadEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class MosaicDatabase : RoomDatabase() {
    abstract fun playlists(): PlaylistDao
    abstract fun content(): ContentDao
    abstract fun downloads(): DownloadDao

    companion object {
        /** Offline downloads. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `downloads` (`playlistId` INTEGER NOT NULL, `itemKey` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, `streamId` TEXT NOT NULL, `title` TEXT NOT NULL, `subtitle` TEXT, " +
                        "`artwork` TEXT, `sourceUrl` TEXT NOT NULL, `userAgent` TEXT, `referrer` TEXT, " +
                        "`filePath` TEXT NOT NULL, `systemId` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
                        "`bytesDownloaded` INTEGER NOT NULL, `bytesTotal` INTEGER NOT NULL, `error` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, `completedAt` INTEGER, PRIMARY KEY(`playlistId`, `itemKey`))"
                )
            }
        }

        /** Dispatcharr DVR credentials. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN dvrApiKey TEXT")
                db.execSQL("ALTER TABLE playlists ADD COLUMN dvrUsername TEXT")
            }
        }

        /** Per-playlist VOD switch and hidden categories. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN vodEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `hidden_categories` (`playlistId` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                        "`categoryId` TEXT NOT NULL, PRIMARY KEY(`playlistId`, `kind`, `categoryId`))"
                )
            }
        }

        /** Catch-up support and server detection. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN serverTimezone TEXT")
                db.execSQL("ALTER TABLE playlists ADD COLUMN serverSoftware TEXT")
                db.execSQL("ALTER TABLE streams ADD COLUMN catchupType TEXT")
                db.execSQL("ALTER TABLE streams ADD COLUMN catchupSource TEXT")
            }
        }

        fun create(context: Context): MosaicDatabase =
            Room.databaseBuilder(context, MosaicDatabase::class.java, "mosaic.db")
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
