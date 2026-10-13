package app.tellycine.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.tellycine.core.ContentKind
import java.util.Locale

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
    /** XMLTV guide URLs you added, one per line (see GuideUrls). They replace [detectedEpgUrl] unless [epgCombine]. */
    val epgUrl: String = "",
    /** The playlist's own guide: named in the M3U header (`url-tvg`), or the Xtream provider's. */
    val detectedEpgUrl: String = "",
    /** Load [detectedEpgUrl] as well as the guides in [epgUrl], rather than instead of them. */
    @ColumnInfo(defaultValue = "0")
    val epgCombine: Boolean = false,
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
    /** Connection limit reported by an Xtream server at the last refresh. */
    val maxConnections: Int? = null,
    /** Connection limit entered by the user; overrides [maxConnections]. */
    val connectionLimit: Int? = null,
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
    val isDispatcharr get() = serverSoftware == app.tellycine.core.xtream.XtreamAccount.SOFTWARE_DISPATCHARR

    /** How many streams the account may play at once, if known. */
    val effectiveMaxConnections get() = connectionLimit ?: maxConnections

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
        // Movies and series sorted by date: see VodSort.
        Index(value = ["playlistId", "kind", "addedAt"]),
        Index(value = ["playlistId", "kind", "released"]),
        Index(value = ["playlistId", "kind", "changedAt"]),
        Index(value = ["playlistId", "kind", "sortName"]),
        Index(value = ["playlistId", "kind", "aired"]),
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
    /**
     * When the provider added it, or for movies and series without that date, when Tellycine first
     * saw it (kept across refreshes).
     */
    val addedAt: Long? = null,
    /** When the provider last changed it: for a series, usually when an episode was added. */
    val updatedAt: Long? = null,
    /** Its release date: "2023-05-01", or "2023" when only the year is known. */
    val released: String? = null,
    /** Days of archive available for catch-up; null or 0 when unsupported. */
    val catchupDays: Int? = null,
    /** M3U catch-up method (`default`, `append`, `shift`, `flussonic`); unused for Xtream. */
    val catchupType: String? = null,
    /** M3U `catchup-source` template. */
    val catchupSource: String? = null,
    val backdrop: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    /** For movies and series, the name as A–Z sorts it ([sortNameOf]). */
    val sortName: String? = if (kind == ContentKind.LIVE) null else sortNameOf(name),
    /**
     * For series, when it last gained episodes as far as Tellycine can tell: the provider's date
     * ([updatedAt]) when there is one; otherwise when Tellycine saw it gain episodes (M3U playlists),
     * or else first saw it. Worked out at each refresh ([app.tellycine.data.sync.FirstSeen]).
     */
    val changedAt: Long? = null,
    /** For series, when its latest episode aired ("2025-05-01"), from TVmaze when that's turned on. */
    val aired: String? = null,
) {
    companion object {
        /** [name] as A–Z sorts it: without surrounding spaces and in lower case, so case doesn't matter. */
        fun sortNameOf(name: String): String = name.trim().lowercase(Locale.ROOT)
    }
}

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

/** What's on now on a channel ([streamId]): nothing, when its guide has nothing at this time. */
data class NowPlaying(
    val streamId: String,
    val nowTitle: String?,
    val nowStart: Long?,
    val nowStop: Long?,
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

/** A history entry joined with its stream's category and position in the playlist (null if the stream is gone). */
data class HistoryRow(
    @Embedded val history: HistoryEntity,
    val categoryId: String?,
    val sortOrder: Int?,
    val number: Int?,
)

enum class DownloadStatus {
    QUEUED, RUNNING, PAUSED, COMPLETED, FAILED;

    val isActive get() = this == QUEUED || this == RUNNING || this == PAUSED
}

/** A movie or episode saved to the device through Android's download manager. */
@Entity(tableName = "downloads", primaryKeys = ["playlistId", "itemKey"])
data class DownloadEntity(
    val playlistId: Long,
    /** History key (`MOVIE:<id>` or `EPISODE:<id>`), so playback shares resume points with streaming. */
    val itemKey: String,
    val kind: ContentKind,
    /** Movie id, or the series id for episodes. */
    val streamId: String,
    val title: String,
    val subtitle: String?,
    val artwork: String?,
    val sourceUrl: String,
    val userAgent: String?,
    val referrer: String?,
    val filePath: String,
    /** Id in Android's [android.app.DownloadManager]. */
    val systemId: Long,
    val status: DownloadStatus,
    val bytesDownloaded: Long,
    /** -1 until the server reports a size. */
    val bytesTotal: Long,
    /** Why a download failed or is paused, in words. */
    val error: String?,
    val createdAt: Long,
    val completedAt: Long?,
)

/**
 * What TVmaze says about a series, by its cleaned-up name and year ([showKey], from
 * [app.tellycine.core.ShowNames]), so playlists with the same series share one lookup. Kept across
 * refreshes; see [app.tellycine.data.tvmaze.TvmazeUpdater].
 */
@Entity(tableName = "tvmaze_shows")
data class TvmazeShowEntity(
    @PrimaryKey val showKey: String,
    /** TVmaze's id for the show, or null when none matched. */
    val tvmazeId: Int?,
    /** When TVmaze last changed the show (seconds), to tell from its list of changes whether to look again. */
    val updated: Long?,
    /** When the latest episode aired, and when the next one will: "2025-05-01". */
    val lastAired: String?,
    val nextAired: String?,
    val ended: Boolean,
    /** When Tellycine last asked TVmaze about it. */
    val checkedAt: Long,
)
