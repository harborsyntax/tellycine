package app.tellycine.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    version = 1,
    exportSchema = false,
)
abstract class TellycineDatabase : RoomDatabase() {
    abstract fun playlists(): PlaylistDao
    abstract fun content(): ContentDao
    abstract fun downloads(): DownloadDao

    companion object {
        fun create(context: Context): TellycineDatabase =
            Room.databaseBuilder(context, TellycineDatabase::class.java, "tellycine.db")
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                // Add migrations here when the schema changes after release.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
