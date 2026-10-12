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
    ],
    version = 2,
    exportSchema = true,
)
abstract class TellycineDatabase : RoomDatabase() {
    abstract fun playlists(): PlaylistDao
    abstract fun content(): ContentDao
    abstract fun downloads(): DownloadDao

    companion object {
        fun create(context: Context): TellycineDatabase =
            Room.databaseBuilder(context, TellycineDatabase::class.java, "tellycine.db")
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                // Add migrations here when the schema changes; the schemas are in app/schemas.
                .addMigrations(MIGRATION_1_2)
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
    }
}
