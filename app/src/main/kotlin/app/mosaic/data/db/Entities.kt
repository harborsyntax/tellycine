package app.mosaic.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.mosaic.core.ContentKind

enum class PlaylistType { M3U_URL, M3U_FILE, XTREAM }

enum class SyncState { IDLE, SYNCING, ERROR }

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: PlaylistType,
    /** M3U URL, content:// URI for local files, or the Xtream server address. */
    val url: String,
    val username: String = "",
    val password: String = "",
    /** User supplied EPG URL(s), comma separated. Overrides the auto-detected one. */
    val epgUrl: String = "",
    /** EPG URL advertised by the M3U header (`url-tvg`). */
    val detectedEpgUrl: String = "",
    val userAgent: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long? = null,
    val epgSyncedAt: Long? = null,
    val syncState: SyncState = SyncState.IDLE,
    val syncMessage: String? = null,
    val liveCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    val programmeCount: Int = 0,
    val expiresAt: Long? = null,
    val maxConnections: Int? = null,
    /** IANA zone reported by an Xtream server; catch-up start times are expressed in it. */
    val serverTimezone: String? = null,
    /** Server software when it identifies itself, e.g. "Dispatcharr". */
    val serverSoftware: String? = null,
    /** When false, movies and series are neither downloaded nor shown. */
    @ColumnInfo(defaultValue = "1")
    val vodEnabled: Boolean = true,
    /** Dispatcharr REST API key, used for DVR recordings. Null when not connected. */
    val dvrApiKey: String? = null,
    /** Dispatcharr user the API key belongs to (display only). */
    val dvrUsername: String? = null,
) {
    val isDispatcharr get() = serverSoftware == app.mosaic.core.xtream.XtreamAccount.SOFTWARE_DISPATCHARR

    /** Connected to Dispatcharr's DVR, so recordings can be listed and scheduled. */
    val hasDvr get() = isDispatcharr && !dvrApiKey.isNullOrBlank()
}

@Entity(
    tableName = "categories",
    primaryKeys = ["playlistId", "kind", "categoryId"],
    indices = [Index(value = ["playlistId", "kind", "sortOrder"])],
)
data class CategoryEntity(
    val playlistId: Long,
    val kind: ContentKind,
    val categoryId: String,
    val name: String,
    val sortOrder: Int,
    val itemCount: Int,
)

/** A live channel, a movie, or a series (whose episodes are loaded separately). */
@Entity(
    tableName = "streams",
    primaryKeys = ["playlistId", "kind", "streamId"],
    indices = [
        Index(value = ["playlistId", "kind", "sortOrder"]),
        Index(value = ["playlistId", "kind", "categoryId", "sortOrder"]),
        Index(value = ["playlistId", "epgChannelId"]),
    ],
)
data class StreamEntity(
    val playlistId: Long,
    val kind: ContentKind,
    val streamId: String,
    val name: String,
    val sortOrder: Int,
    val categoryId: String?,
    val logo: String?,
    /** Direct playback URL. Empty for Xtream series (episodes are fetched on demand). */
    val url: String,
    /** Lower-cased XMLTV channel id used to join against [ProgrammeEntity.channelId]. */
    val epgChannelId: String? = null,
    val number: Int? = null,
    val containerExt: String? = null,
    val rating: Double? = null,
    val year: String? = null,
    val plot: String? = null,
    val genre: String? = null,
    val addedAt: Long? = null,
    /** Days of archive available for catch-up; null or 0 when unsupported. */
    val catchupDays: Int? = null,
    /** M3U catch-up method (`default`, `append`, `shift`, `flussonic`); unused for Xtream. */
    val catchupType: String? = null,
    /** M3U `catchup-source` template. */
    val catchupSource: String? = null,
    val backdrop: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
)

/** Episodes of M3U-sourced series (Xtream episodes come from `get_series_info`). */
@Entity(
    tableName = "episodes",
    primaryKeys = ["playlistId", "episodeId"],
    indices = [Index(value = ["playlistId", "seriesId", "season", "episode"])],
)
data class EpisodeEntity(
    val playlistId: Long,
    val episodeId: String,
    val seriesId: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val url: String,
    val logo: String?,
)

@Entity(
    tableName = "programmes",
    indices = [Index(value = ["playlistId", "channelId", "startMs"])],
)
data class ProgrammeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val channelId: String,
    val startMs: Long,
    val stopMs: Long,
    val title: String,
    val description: String?,
    val category: String?,
)

@Entity(tableName = "favorites", primaryKeys = ["playlistId", "kind", "streamId"])
data class FavoriteEntity(
    val playlistId: Long,
    val kind: ContentKind,
    val streamId: String,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "history",
    primaryKeys = ["playlistId", "itemKey"],
    indices = [Index(value = ["playlistId", "lastWatchedAt"])],
)
data class HistoryEntity(
    val playlistId: Long,
    /** `LIVE:<id>`, `MOVIE:<id>` or `EPISODE:<id>`. */
    val itemKey: String,
    val kind: ContentKind,
    /** Channel / movie / series id in [StreamEntity]. */
    val streamId: String,
    val title: String,
    val subtitle: String?,
    val artwork: String?,
    val url: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: Long,
)

/** A live channel joined with its current programme and favourite flag. */
data class ChannelRow(
    @Embedded val stream: StreamEntity,
    val nowTitle: String?,
    val nowStart: Long?,
    val nowStop: Long?,
    val isFavorite: Boolean,
)

data class CatchupWindow(val epgChannelId: String, val days: Int)

/** A category the user unticked; kept separately so it survives playlist refreshes. */
@Entity(tableName = "hidden_categories", primaryKeys = ["playlistId", "kind", "categoryId"])
data class HiddenCategoryEntity(
    val playlistId: Long,
    val kind: ContentKind,
    val categoryId: String,
)

data class CategoryVisibility(
    @Embedded val category: CategoryEntity,
    val hidden: Boolean,
)
