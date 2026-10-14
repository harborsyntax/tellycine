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
        TmdbTitleEntity::class,
        TmdbPageEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class TellycineDatabase : RoomDatabase() {
    abstract fun playlists(): PlaylistDao
    abstract fun content(): ContentDao
    abstract fun downloads(): DownloadDao
    abstract fun tmdb(): TmdbDao

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

        /** 1.13: guides you add to a playlist can be loaded as well as its own, not only instead. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playlists` ADD COLUMN `epgCombine` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * 1.14: details from TMDB (opt-in) in place of air dates from TVmaze. Titles gain their TMDB
         * id, score and poster, and TMDB's answers get tables of their own. Air dates came from
         * TVmaze, so they're cleared until TMDB fills them in.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `tvmaze_shows`")
                db.execSQL("ALTER TABLE `streams` ADD COLUMN `tmdbId` INTEGER")
                db.execSQL("ALTER TABLE `streams` ADD COLUMN `tmdbRating` REAL")
                db.execSQL("ALTER TABLE `streams` ADD COLUMN `tmdbPoster` TEXT")
                db.execSQL("UPDATE `streams` SET `aired` = NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_streams_playlistId_kind_tmdbRating` ON `streams` (`playlistId`, `kind`, `tmdbRating`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_streams_kind_tmdbId` ON `streams` (`kind`, `tmdbId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tmdb_titles` (`titleKey` TEXT NOT NULL, `kind` TEXT NOT NULL, `tmdbId` INTEGER, " +
                        "`rating` REAL, `poster` TEXT, `lastAired` TEXT, `nextAired` TEXT, `ended` INTEGER NOT NULL, " +
                        "`checkedAt` INTEGER NOT NULL, PRIMARY KEY(`titleKey`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tmdb_pages` (`kind` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, `json` TEXT NOT NULL, " +
                        "`fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`kind`, `tmdbId`))"
                )
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
    }
}
