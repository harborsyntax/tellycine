package app.tube.data.db

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
    ],
    version = 2,
    exportSchema = false,
)
abstract class TubeDatabase : RoomDatabase() {
    abstract fun playlists(): PlaylistDao
    abstract fun content(): ContentDao

    companion object {
        /** Catch-up support and server detection. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN serverTimezone TEXT")
                db.execSQL("ALTER TABLE playlists ADD COLUMN serverSoftware TEXT")
                db.execSQL("ALTER TABLE streams ADD COLUMN catchupType TEXT")
                db.execSQL("ALTER TABLE streams ADD COLUMN catchupSource TEXT")
            }
        }

        fun create(context: Context): TubeDatabase =
            Room.databaseBuilder(context, TubeDatabase::class.java, "tube.db")
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
