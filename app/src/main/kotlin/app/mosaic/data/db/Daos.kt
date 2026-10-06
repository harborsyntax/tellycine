package app.mosaic.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import app.mosaic.core.ContentKind
import kotlinx.coroutines.flow.Flow

/**
 * Excludes streams (aliased `s`) whose category the user hid for this playlist.
 * Streams without a category are always visible.
 */
private const val VISIBLE =
    "NOT EXISTS (SELECT 1 FROM hidden_categories h WHERE h.playlistId = s.playlistId AND h.kind = s.kind AND h.categoryId = s.categoryId)"

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

    @Query(
        """SELECT s.*,
             (SELECT p.title FROM programmes p WHERE p.playlistId = s.playlistId AND p.channelId = s.epgChannelId
                AND p.startMs <= :now AND p.stopMs > :now ORDER BY p.startMs DESC LIMIT 1) AS nowTitle,
             (SELECT p.startMs FROM programmes p WHERE p.playlistId = s.playlistId AND p.channelId = s.epgChannelId
                AND p.startMs <= :now AND p.stopMs > :now ORDER BY p.startMs DESC LIMIT 1) AS nowStart,
             (SELECT p.stopMs FROM programmes p WHERE p.playlistId = s.playlistId AND p.channelId = s.epgChannelId
                AND p.startMs <= :now AND p.stopMs > :now ORDER BY p.startMs DESC LIMIT 1) AS nowStop,
             EXISTS(SELECT 1 FROM favorites f WHERE f.playlistId = s.playlistId AND f.kind = s.kind AND f.streamId = s.streamId) AS isFavorite
           FROM streams s
           WHERE s.playlistId = :playlistId AND s.kind = 'LIVE' AND """ + VISIBLE + """
           ORDER BY s.sortOrder"""
    )
    fun pagedChannels(playlistId: Long, now: Long): PagingSource<Int, ChannelRow>

    @Query(
        """SELECT s.*,
             (SELECT p.title FROM programmes p WHERE p.playlistId = s.playlistId AND p.channelId = s.epgChannelId
                AND p.startMs <= :now AND p.stopMs > :now ORDER BY p.startMs DESC LIMIT 1) AS nowTitle,
             (SELECT p.startMs FROM programmes p WHERE p.playlistId = s.playlistId AND p.channelId = s.epgChannelId
                AND p.startMs <= :now AND p.stopMs > :now ORDER BY p.startMs DESC LIMIT 1) AS nowStart,
             (SELECT p.stopMs FROM programmes p WHERE p.playlistId = s.playlistId AND p.channelId = s.epgChannelId
                AND p.startMs <= :now AND p.stopMs > :now ORDER BY p.startMs DESC LIMIT 1) AS nowStop,
             EXISTS(SELECT 1 FROM favorites f WHERE f.playlistId = s.playlistId AND f.kind = s.kind AND f.streamId = s.streamId) AS isFavorite
           FROM streams s
           WHERE s.playlistId = :playlistId AND s.kind = 'LIVE' AND s.categoryId = :categoryId
           ORDER BY s.sortOrder"""
    )
    fun pagedChannelsInCategory(playlistId: Long, categoryId: String, now: Long): PagingSource<Int, ChannelRow>

    @Query(
        """SELECT s.*,
             (SELECT p.title FROM programmes p WHERE p.playlistId = s.playlistId AND p.channelId = s.epgChannelId
                AND p.startMs <= :now AND p.stopMs > :now ORDER BY p.startMs DESC LIMIT 1) AS nowTitle,
             (SELECT p.startMs FROM programmes p WHERE p.playlistId = s.playlistId AND p.channelId = s.epgChannelId
                AND p.startMs <= :now AND p.stopMs > :now ORDER BY p.startMs DESC LIMIT 1) AS nowStart,
             (SELECT p.stopMs FROM programmes p WHERE p.playlistId = s.playlistId AND p.channelId = s.epgChannelId
                AND p.startMs <= :now AND p.stopMs > :now ORDER BY p.startMs DESC LIMIT 1) AS nowStop,
             1 AS isFavorite
           FROM streams s INNER JOIN favorites f
             ON f.playlistId = s.playlistId AND f.kind = s.kind AND f.streamId = s.streamId
           WHERE s.playlistId = :playlistId AND s.kind = 'LIVE' AND """ + VISIBLE + """
           ORDER BY f.addedAt DESC"""
    )
    fun pagedFavoriteChannels(playlistId: Long, now: Long): PagingSource<Int, ChannelRow>

    /** Channels of one category (or all), used by the player to zap between channels. */
    @Query(
        """SELECT * FROM streams s WHERE s.playlistId = :playlistId AND s.kind = 'LIVE'
           AND (:categoryId IS NULL OR s.categoryId = :categoryId) AND """ + VISIBLE + """ ORDER BY s.sortOrder"""
    )
    suspend fun channelList(playlistId: Long, categoryId: String?): List<StreamEntity>

    // ---- Movies / series ----

    @Query("SELECT * FROM streams s WHERE s.playlistId = :playlistId AND s.kind = :kind AND " + VISIBLE + " ORDER BY s.sortOrder")
    fun pagedItems(playlistId: Long, kind: ContentKind): PagingSource<Int, StreamEntity>

    @Query("SELECT * FROM streams WHERE playlistId = :playlistId AND kind = :kind AND categoryId = :categoryId ORDER BY sortOrder")
    fun pagedItemsInCategory(playlistId: Long, kind: ContentKind, categoryId: String): PagingSource<Int, StreamEntity>

    @Query(
        """SELECT s.* FROM streams s INNER JOIN favorites f
             ON f.playlistId = s.playlistId AND f.kind = s.kind AND f.streamId = s.streamId
           WHERE s.playlistId = :playlistId AND s.kind = :kind AND """ + VISIBLE + """ ORDER BY f.addedAt DESC"""
    )
    fun pagedFavoriteItems(playlistId: Long, kind: ContentKind): PagingSource<Int, StreamEntity>

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

    @Query("SELECT * FROM episodes WHERE playlistId = :playlistId AND seriesId = :seriesId ORDER BY season, episode")
    suspend fun episodes(playlistId: Long, seriesId: String): List<EpisodeEntity>

    // ---- EPG ----

    @Query(
        """SELECT * FROM programmes WHERE playlistId = :playlistId AND channelId = :channelId AND stopMs > :fromMs
           ORDER BY startMs LIMIT :limit"""
    )
    suspend fun schedule(playlistId: Long, channelId: String, fromMs: Long, limit: Int): List<ProgrammeEntity>

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
        """SELECT * FROM history WHERE playlistId = :playlistId AND kind != 'LIVE'
           AND positionMs > 30000 AND (durationMs <= 0 OR positionMs < durationMs * 0.95)
           ORDER BY lastWatchedAt DESC LIMIT :limit"""
    )
    fun observeContinueWatching(playlistId: Long, limit: Int): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE playlistId = :playlistId AND kind = 'LIVE' ORDER BY lastWatchedAt DESC LIMIT :limit")
    fun observeRecentChannels(playlistId: Long, limit: Int): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history WHERE playlistId = :playlistId AND itemKey = :itemKey")
    suspend fun removeHistory(playlistId: Long, itemKey: String)

    @Query("DELETE FROM history")
    suspend fun clearAllHistory()
}
