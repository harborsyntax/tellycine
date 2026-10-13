package app.tellycine.data.db

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
        TvmazeShowEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class TellycineDatabase : RoomDatabase() {
    abstract fun playlists(): PlaylistDao
    abstract fun content(): ContentDao
    abstract fun downloads(): DownloadDao
    abstract fun tvmaze(): TvmazeDao

    companion object {
        fun create(context: Context): TellycineDatabase =
            Room.databaseBuilder(context, TellycineDatabase::class.java, "tellycine.db")
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                // Add each schema change's migration to MIGRATIONS; the schemas are in app/schemas.
                .addMigrations(*MIGRATIONS)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()

        /**
         * 1.10: release dates, series' last updates and names to sort by, for sorting movies and series.
         * A series' added date used to be its last update, so it moves there; the next refresh fills
         * in the rest (including names in lower case beyond A–Z, which SQLite's lower() leaves).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `streams` ADD COLUMN `updatedAt` INTEGER")
                db.execSQL("ALTER TABLE `streams` ADD COLUMN `released` TEXT")
                db.execSQL("ALTER TABLE `streams` ADD COLUMN `sortName` TEXT")
                db.execSQL("UPDATE `streams` SET `sortName` = lower(trim(`name`)) WHERE `kind` != 'LIVE'")
                db.execSQL("UPDATE `streams` SET `updatedAt` = `addedAt`, `addedAt` = NULL WHERE `kind` = 'SERIES'")
                db.execSQL("UPDATE `streams` SET `released` = substr(`year`, 1, 4) WHERE `year` GLOB '[12][0-9][0-9][0-9]*'")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_streams_playlistId_kind_addedAt` ON `streams` (`playlistId`, `kind`, `addedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_streams_playlistId_kind_released` ON `streams` (`playlistId`, `kind`, `released`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_streams_playlistId_kind_updatedAt` ON `streams` (`playlistId`, `kind`, `updatedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_streams_playlistId_kind_sortName` ON `streams` (`playlistId`, `kind`, `sortName`)")
            }
        }

        /**
         * 1.11: series' own change dates (to sort by their latest episode, M3U playlists too) and air
         * dates from TVmaze, with TVmaze's answers kept in a table of their own. Until the next
         * refresh, a series' change date is its provider's date, or else when it was first seen.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `streams` ADD COLUMN `changedAt` INTEGER")
                db.execSQL("ALTER TABLE `streams` ADD COLUMN `aired` TEXT")
                db.execSQL("UPDATE `streams` SET `changedAt` = COALESCE(`updatedAt`, `addedAt`) WHERE `kind` = 'SERIES'")
                db.execSQL("DROP INDEX IF EXISTS `index_streams_playlistId_kind_updatedAt`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_streams_playlistId_kind_changedAt` ON `streams` (`playlistId`, `kind`, `changedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_streams_playlistId_kind_aired` ON `streams` (`playlistId`, `kind`, `aired`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tvmaze_shows` (`showKey` TEXT NOT NULL, `tvmazeId` INTEGER, `updated` INTEGER, " +
                        "`lastAired` TEXT, `nextAired` TEXT, `ended` INTEGER NOT NULL, `checkedAt` INTEGER NOT NULL, PRIMARY KEY(`showKey`))"
                )
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
