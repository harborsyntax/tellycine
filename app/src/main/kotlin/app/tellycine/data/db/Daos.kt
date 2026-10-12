package app.tellycine.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import app.tellycine.core.ContentKind
import kotlinx.coroutines.flow.Flow

/**
 * Excludes streams (aliased `s`) whose category the user hid for this playlist.
 * Streams without a category are always visible.
 */
internal const val VISIBLE =
    "NOT EXISTS (SELECT 1 FROM hidden_categories h WHERE h.playlistId = s.playlistId AND h.kind = s.kind AND h.categoryId = s.categoryId)"

/** What's on now on a channel (aliased `s`), and whether it's a favourite, as [ChannelRow] wants them. */
private const val NOW_PLAYING =
    """(SELECT p.title FROM programmes p WHERE p.playlistId = s.playlistId AND p.channelId = s.epgChannelId
         AND p.startMs <= :now AND p.stopMs > :now ORDER BY p.startMs DESC LIMIT 1) AS nowTitle,
       (SELECT p.startMs FROM programmes p WHERE p.playlistId = s.playlistId AND p.channelId = s.epgChannelId
         AND p.startMs <= :now AND p.stopMs > :now ORDER BY p.startMs DESC LIMIT 1) AS nowStart,
       (SELECT p.stopMs FROM programmes p WHERE p.playlistId = s.playlistId AND p.channelId = s.epgChannelId
         AND p.startMs <= :now AND p.stopMs > :now ORDER BY p.startMs DESC LIMIT 1) AS nowStop"""

/** Left empty in pages of channels, for [ContentDao.nowPlaying] to fill in. */
private const val NO_NOW_PLAYING = "NULL AS nowTitle, NULL AS nowStart, NULL AS nowStop"

private const val IS_FAVORITE =
    "EXISTS(SELECT 1 FROM favorites f WHERE f.playlistId = s.playlistId AND f.kind = s.kind AND f.streamId = s.streamId) AS isFavorite"

/**
 * Streams (aliased `s`) in the categories (aliased `c`) whose name gives `:nameKey` (see
 * [app.tellycine.data.repo.Categories.nameKey]), of any of `:playlistIds`: how a category merged across
 * playlists finds its streams. CROSS JOIN makes the database start from the few matching categories
 * rather than check every stream's category.
 */
private const val FROM_NAMED_CATEGORY =
    """FROM categories c CROSS JOIN streams s
         ON s.playlistId = c.playlistId AND s.kind = c.kind AND s.categoryId = c.categoryId
       WHERE c.playlistId IN (:playlistIds) AND LOWER(TRIM(c.name)) = :nameKey"""

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY createdAt")
    suspend fun getAll(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun getBlocking(id: Long): PlaylistEntity?

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Upsert
    suspend fun upsert(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET syncState = :state, syncMessage = :message WHERE id = :id")
    suspend fun setSyncState(id: Long, state: SyncState, message: String?)

    @Query(
        """UPDATE playlists SET liveCount = :live, movieCount = :movies, seriesCount = :series,
           lastSyncedAt = :at, detectedEpgUrl = :detectedEpg, expiresAt = :expiresAt, maxConnections = :maxConnections,
           serverTimezone = :serverTimezone, serverSoftware = :serverSoftware
           WHERE id = :id"""
    )
    suspend fun setContentSynced(
        id: Long, live: Int, movies: Int, series: Int, at: Long,
        detectedEpg: String, expiresAt: Long?, maxConnections: Int?,
        serverTimezone: String?, serverSoftware: String?,
    )

    @Query("UPDATE playlists SET dvrApiKey = :apiKey, dvrUsername = :username WHERE id = :id")
    suspend fun setDvrCredentials(id: Long, apiKey: String?, username: String?)

    @Query("UPDATE playlists SET epgSyncedAt = :at, programmeCount = :count WHERE id = :id")
    suspend fun setEpgSynced(id: Long, at: Long, count: Int)

    @Query("UPDATE playlists SET syncState = 'IDLE', syncMessage = NULL WHERE syncState = 'SYNCING'")
    suspend fun resetInterruptedSyncs()

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ContentDao {
    // ---- Sync (blocking, called inside runInTransaction) ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertStreams(items: List<StreamEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCategories(items: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEpisodes(items: List<EpisodeEntity>)

    @Insert
    fun insertProgrammes(items: List<ProgrammeEntity>)

    @Query("DELETE FROM streams WHERE playlistId = :playlistId")
    fun deleteStreams(playlistId: Long)

    @Query("DELETE FROM categories WHERE playlistId = :playlistId")
    fun deleteCategories(playlistId: Long)

    @Query("DELETE FROM episodes WHERE playlistId = :playlistId")
    fun deleteEpisodes(playlistId: Long)

    @Query("DELETE FROM programmes WHERE playlistId = :playlistId")
    fun deleteProgrammes(playlistId: Long)

    @Query("DELETE FROM favorites WHERE playlistId = :playlistId")
    fun deleteFavorites(playlistId: Long)

    @Query("DELETE FROM history WHERE playlistId = :playlistId")
    fun deleteHistory(playlistId: Long)

    @Query("SELECT DISTINCT epgChannelId FROM streams WHERE playlistId = :playlistId AND kind = 'LIVE' AND epgChannelId IS NOT NULL")
    fun epgChannelIds(playlistId: Long): List<String>

    /** Longest catch-up archive per guide channel, used to keep past programmes for replay. */
    @Query(
        """SELECT epgChannelId, MAX(catchupDays) AS days FROM streams
           WHERE playlistId = :playlistId AND kind = 'LIVE' AND epgChannelId IS NOT NULL AND catchupDays > 0
           GROUP BY epgChannelId"""
    )
    fun catchupWindows(playlistId: Long): List<CatchupWindow>

    // ---- Categories ----

    /** Categories shown in the browse screens (hidden ones excluded). */
    @Query(
        """SELECT * FROM categories c WHERE c.playlistId = :playlistId AND c.kind = :kind
           AND NOT EXISTS (SELECT 1 FROM hidden_categories h
                           WHERE h.playlistId = c.playlistId AND h.kind = c.kind AND h.categoryId = c.categoryId)
           ORDER BY c.sortOrder"""
    )
    fun observeCategories(playlistId: Long, kind: ContentKind): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND kind = :kind AND categoryId = :categoryId")
    suspend fun getCategory(playlistId: Long, kind: ContentKind, categoryId: String): CategoryEntity?

    /** The categories of several playlists, for browsing them together; merged by name afterwards. */
    @Query(
        """SELECT * FROM categories c WHERE c.playlistId IN (:playlistIds) AND c.kind = :kind
           AND NOT EXISTS (SELECT 1 FROM hidden_categories h
                           WHERE h.playlistId = c.playlistId AND h.kind = c.kind AND h.categoryId = c.categoryId)
           ORDER BY c.playlistId, c.sortOrder"""
    )
    fun observeCategoriesIn(playlistIds: List<Long>, kind: ContentKind): Flow<List<CategoryEntity>>

    /** Every category with its visibility, for the playlist's category filter. */
    @Query(
        """SELECT c.*, EXISTS (SELECT 1 FROM hidden_categories h
                               WHERE h.playlistId = c.playlistId AND h.kind = c.kind AND h.categoryId = c.categoryId) AS hidden
           FROM categories c WHERE c.playlistId = :playlistId AND c.kind = :kind ORDER BY c.sortOrder"""
    )
    fun observeCategoryVisibility(playlistId: Long, kind: ContentKind): Flow<List<CategoryVisibility>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hideCategories(items: List<HiddenCategoryEntity>)

    @Query("DELETE FROM hidden_categories WHERE playlistId = :playlistId AND kind = :kind AND categoryId IN (:categoryIds)")
    suspend fun showCategories(playlistId: Long, kind: ContentKind, categoryIds: List<String>)

    @Query("DELETE FROM hidden_categories WHERE playlistId = :playlistId")
    fun deleteHiddenCategories(playlistId: Long)

    // ---- Live channels ----

    // The pages of channels leave what's on now empty: Room counts a list by running its whole query,
    // which with the programme lookups cost far more than the page itself. [nowPlaying] fills them in
    // for each page.

    @Query(
        "SELECT s.*, " + NO_NOW_PLAYING + ", " + IS_FAVORITE + """
           FROM streams s
           WHERE s.playlistId = :playlistId AND s.kind = 'LIVE' AND """ + VISIBLE + """
           ORDER BY s.sortOrder"""
    )
    fun pagedChannels(playlistId: Long): PagingSource<Int, ChannelRow>

    @Query(
        "SELECT s.*, " + NO_NOW_PLAYING + ", " + IS_FAVORITE + """
           FROM streams s
           WHERE s.playlistId = :playlistId AND s.kind = 'LIVE' AND s.categoryId = :categoryId
           ORDER BY s.sortOrder"""
    )
    fun pagedChannelsInCategory(playlistId: Long, categoryId: String): PagingSource<Int, ChannelRow>

    @Query(
        "SELECT s.*, " + NO_NOW_PLAYING + """, 1 AS isFavorite
           FROM streams s INNER JOIN favorites f
             ON f.playlistId = s.playlistId AND f.kind = s.kind AND f.streamId = s.streamId
           WHERE s.playlistId = :playlistId AND s.kind = 'LIVE' AND """ + VISIBLE + """
           ORDER BY f.addedAt DESC"""
    )
    fun pagedFavoriteChannels(playlistId: Long): PagingSource<Int, ChannelRow>

    // Several playlists together: each playlist's channels in turn, in the order they were added.

    @Query(
        "SELECT s.*, " + NO_NOW_PLAYING + ", " + IS_FAVORITE + """
           FROM streams s
           WHERE s.playlistId IN (:playlistIds) AND s.kind = 'LIVE' AND """ + VISIBLE + """
           ORDER BY s.playlistId, s.sortOrder"""
    )
    fun pagedChannelsIn(playlistIds: List<Long>): PagingSource<Int, ChannelRow>

    @Query(
        "SELECT s.*, " + NO_NOW_PLAYING + ", " + IS_FAVORITE + """
           """ + FROM_NAMED_CATEGORY + """ AND c.kind = 'LIVE' AND """ + VISIBLE + """
           ORDER BY s.playlistId, s.sortOrder"""
    )
    fun pagedChannelsInNamedCategory(playlistIds: List<Long>, nameKey: String): PagingSource<Int, ChannelRow>

    @Query(
        "SELECT s.*, " + NO_NOW_PLAYING + """, 1 AS isFavorite
           FROM streams s INNER JOIN favorites f
             ON f.playlistId = s.playlistId AND f.kind = s.kind AND f.streamId = s.streamId
           WHERE s.playlistId IN (:playlistIds) AND s.kind = 'LIVE' AND """ + VISIBLE + """
           ORDER BY f.addedAt DESC"""
    )
    fun pagedFavoriteChannelsIn(playlistIds: List<Long>): PagingSource<Int, ChannelRow>

    /** What's on now ([now]) on some of a playlist's channels: the rows of a page, or one row on screen. */
    @Query("SELECT s.streamId AS streamId, " + NOW_PLAYING + " FROM streams s WHERE s.playlistId = :playlistId AND s.kind = 'LIVE' AND s.streamId IN (:streamIds)")
    suspend fun nowPlaying(playlistId: Long, streamIds: List<String>, now: Long): List<NowPlaying>

    // How many channels come before one (of [playlistId], at [sortOrder]) in the lists above: where it is in them.

    @Query(
        """SELECT COUNT(*) FROM streams s WHERE s.playlistId IN (:playlistIds) AND s.kind = 'LIVE' AND """ + VISIBLE + """
           AND (s.playlistId < :playlistId OR (s.playlistId = :playlistId AND s.sortOrder < :sortOrder))"""
    )
    suspend fun channelsBefore(playlistIds: List<Long>, playlistId: Long, sortOrder: Int): Int

    @Query(
        """SELECT COUNT(*) FROM streams s WHERE s.playlistId = :playlistId AND s.kind = 'LIVE' AND s.categoryId = :categoryId
           AND s.sortOrder < :sortOrder"""
    )
    suspend fun channelsBeforeInCategory(playlistId: Long, categoryId: String, sortOrder: Int): Int

    @Query(
        "SELECT COUNT(*) " + FROM_NAMED_CATEGORY + """ AND c.kind = 'LIVE' AND """ + VISIBLE + """
           AND (s.playlistId < :playlistId OR (s.playlistId = :playlistId AND s.sortOrder < :sortOrder))"""
    )
    suspend fun channelsBeforeInNamedCategory(playlistIds: List<Long>, nameKey: String, playlistId: Long, sortOrder: Int): Int

    @Query(
        """SELECT COUNT(*) FROM streams s INNER JOIN favorites f
             ON f.playlistId = s.playlistId AND f.kind = s.kind AND f.streamId = s.streamId
           WHERE s.playlistId IN (:playlistIds) AND s.kind = 'LIVE' AND """ + VISIBLE + """
           AND f.addedAt > (SELECT addedAt FROM favorites WHERE playlistId = :playlistId AND kind = 'LIVE' AND streamId = :streamId)"""
    )
    suspend fun favoriteChannelsBefore(playlistIds: List<Long>, playlistId: Long, streamId: String): Int

    /** Channels of one category (or all), used by the player to zap between channels. */
    @Query(
        """SELECT * FROM streams s WHERE s.playlistId = :playlistId AND s.kind = 'LIVE'
           AND (:categoryId IS NULL OR s.categoryId = :categoryId) AND """ + VISIBLE + """ ORDER BY s.sortOrder"""
    )
    suspend fun channelList(playlistId: Long, categoryId: String?): List<StreamEntity>

    // ---- Movies / series ----

    /** A page of movies or series, from a query built by [app.tellycine.data.repo.ItemQueries]. */
    @RawQuery
    suspend fun items(query: SupportSQLiteQuery): List<StreamEntity>

    /** How many there are in a list of movies or series ([app.tellycine.data.repo.ItemQueries]). */
    @RawQuery
    suspend fun countItems(query: SupportSQLiteQuery): Int

    @Query(
        """SELECT s.* FROM streams s INNER JOIN favorites f
             ON f.playlistId = s.playlistId AND f.kind = s.kind AND f.streamId = s.streamId
           WHERE s.playlistId = :playlistId AND s.kind = :kind AND """ + VISIBLE + """ ORDER BY f.addedAt DESC LIMIT :limit"""
    )
    fun observeFavoriteItems(playlistId: Long, kind: ContentKind, limit: Int): Flow<List<StreamEntity>>

    @Query("SELECT * FROM streams WHERE playlistId = :playlistId AND kind = :kind AND streamId = :streamId")
    fun observeStream(playlistId: Long, kind: ContentKind, streamId: String): Flow<StreamEntity?>

    @Query("SELECT * FROM streams WHERE playlistId = :playlistId AND kind = :kind AND streamId = :streamId")
    suspend fun getStream(playlistId: Long, kind: ContentKind, streamId: String): StreamEntity?

    @Query(
        """SELECT * FROM streams s WHERE s.playlistId = :playlistId AND s.kind = :kind AND s.name LIKE '%' || :query || '%'
           AND """ + VISIBLE + """
           ORDER BY CASE WHEN s.name LIKE :query || '%' THEN 0 ELSE 1 END, s.sortOrder LIMIT :limit"""
    )
    suspend fun search(playlistId: Long, kind: ContentKind, query: String, limit: Int): List<StreamEntity>

    /**
     * Guide programmes whose title matches [query] on visible channels: on now and upcoming first
     * (soonest first), then past ones from [fromMs] (most recent first) for catch-up.
     */
    @Query(
        """SELECT * FROM programmes p WHERE p.playlistId = :playlistId AND p.stopMs > :fromMs
           AND p.title LIKE '%' || :query || '%'
           AND EXISTS (SELECT 1 FROM streams s WHERE s.playlistId = p.playlistId AND s.kind = 'LIVE'
                       AND s.epgChannelId = p.channelId AND """ + VISIBLE + """)
           ORDER BY p.stopMs <= :now, CASE WHEN p.stopMs > :now THEN p.startMs ELSE -p.startMs END
           LIMIT :limit"""
    )
    suspend fun searchProgrammes(playlistId: Long, query: String, now: Long, fromMs: Long, limit: Int): List<ProgrammeEntity>

    @Query(
        """SELECT * FROM streams s WHERE s.playlistId = :playlistId AND s.kind = 'LIVE'
           AND s.epgChannelId IN (:epgChannelIds) AND """ + VISIBLE + """ ORDER BY s.sortOrder"""
    )
    suspend fun channelsForGuideIds(playlistId: Long, epgChannelIds: List<String>): List<StreamEntity>

    @Query("SELECT * FROM episodes WHERE playlistId = :playlistId AND seriesId = :seriesId ORDER BY season, episode")
    suspend fun episodes(playlistId: Long, seriesId: String): List<EpisodeEntity>

    // ---- EPG ----

    @Query(
        """SELECT * FROM programmes WHERE playlistId = :playlistId AND channelId = :channelId AND stopMs > :fromMs
           ORDER BY startMs LIMIT :limit"""
    )
    suspend fun schedule(playlistId: Long, channelId: String, fromMs: Long, limit: Int): List<ProgrammeEntity>

    /** Programmes overlapping [fromMs]–[toMs], for the grid guide. */
    @Query(
        """SELECT * FROM programmes WHERE playlistId = :playlistId AND channelId = :channelId
           AND stopMs > :fromMs AND startMs < :toMs ORDER BY startMs"""
    )
    suspend fun programmesBetween(playlistId: Long, channelId: String, fromMs: Long, toMs: Long): List<ProgrammeEntity>

    // ---- Favourites ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE playlistId = :playlistId AND kind = :kind AND streamId = :streamId")
    suspend fun removeFavorite(playlistId: Long, kind: ContentKind, streamId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE playlistId = :playlistId AND kind = :kind AND streamId = :streamId)")
    fun observeIsFavorite(playlistId: Long, kind: ContentKind, streamId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE playlistId = :playlistId AND kind = :kind AND streamId = :streamId)")
    suspend fun isFavorite(playlistId: Long, kind: ContentKind, streamId: String): Boolean

    // ---- History ----

    @Upsert
    suspend fun upsertHistory(item: HistoryEntity)

    @Query("SELECT * FROM history WHERE playlistId = :playlistId AND itemKey = :itemKey")
    suspend fun getHistory(playlistId: Long, itemKey: String): HistoryEntity?

    @Query("SELECT * FROM history WHERE playlistId = :playlistId AND itemKey IN (:itemKeys)")
    fun observeHistory(playlistId: Long, itemKeys: List<String>): Flow<List<HistoryEntity>>

    @Query(
        """SELECT h.*, s.categoryId AS categoryId, s.sortOrder AS sortOrder, s.number AS number
           FROM history h LEFT JOIN streams s
             ON s.playlistId = h.playlistId AND s.kind = h.kind AND s.streamId = h.streamId
           WHERE h.playlistId = :playlistId AND h.kind != 'LIVE'
             AND h.positionMs > 30000 AND (h.durationMs <= 0 OR h.positionMs < h.durationMs * 0.95)
           ORDER BY h.lastWatchedAt DESC LIMIT :limit"""
    )
    fun observeContinueWatching(playlistId: Long, limit: Int): Flow<List<HistoryRow>>

    @Query(
        """SELECT h.*, s.categoryId AS categoryId, s.sortOrder AS sortOrder, s.number AS number
           FROM history h LEFT JOIN streams s
             ON s.playlistId = h.playlistId AND s.kind = h.kind AND s.streamId = h.streamId
           WHERE h.playlistId = :playlistId AND h.kind = 'LIVE'
           ORDER BY h.lastWatchedAt DESC LIMIT :limit"""
    )
    fun observeRecentChannels(playlistId: Long, limit: Int): Flow<List<HistoryRow>>

    @Query("DELETE FROM history WHERE playlistId = :playlistId AND itemKey = :itemKey")
    suspend fun removeHistory(playlistId: Long, itemKey: String)

    @Query("DELETE FROM history")
    suspend fun clearAllHistory()

    /** Clears what Continue watching shows; finished titles keep their watched state. */
    @Query(
        """DELETE FROM history WHERE playlistId = :playlistId AND kind != 'LIVE'
           AND positionMs > 30000 AND (durationMs <= 0 OR positionMs < durationMs * 0.95)"""
    )
    suspend fun clearContinueWatching(playlistId: Long)

    @Query("DELETE FROM history WHERE playlistId = :playlistId AND kind = 'LIVE'")
    suspend fun clearRecentChannels(playlistId: Long)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE playlistId = :playlistId AND itemKey IN (:keys)")
    fun observe(playlistId: Long, keys: List<String>): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE playlistId = :playlistId AND itemKey = :itemKey")
    suspend fun get(playlistId: Long, itemKey: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED', 'RUNNING', 'PAUSED')")
    suspend fun active(): List<DownloadEntity>

    @Query("SELECT * FROM downloads")
    suspend fun all(): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE playlistId = :playlistId AND itemKey = :itemKey")
    suspend fun delete(playlistId: Long, itemKey: String)
}

