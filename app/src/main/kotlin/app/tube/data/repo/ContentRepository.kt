package app.tube.data.repo

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import app.tube.core.ContentKind
import app.tube.data.db.CategoryEntity
import app.tube.data.db.ChannelRow
import app.tube.data.db.FavoriteEntity
import app.tube.data.db.HistoryEntity
import app.tube.data.db.ProgrammeEntity
import app.tube.data.db.StreamEntity
import app.tube.data.db.TubeDatabase
import kotlinx.coroutines.flow.Flow

/** Pseudo category ids understood by the paging queries. */
object Categories {
    const val ALL = "__all__"
    const val FAVORITES = "__favorites__"
}

class ContentRepository(db: TubeDatabase) {
    private val dao = db.content()

    private val pagingConfig = PagingConfig(
        pageSize = 60,
        prefetchDistance = 90,
        initialLoadSize = 120,
        enablePlaceholders = true,
    )

    fun categories(playlistId: Long, kind: ContentKind): Flow<List<CategoryEntity>> =
        dao.observeCategories(playlistId, kind)

    /**
     * Pages live channels joined with their current programme. [now] is read whenever a
     * new paging source is created, so invalidating the source refreshes "now playing".
     */
    fun channels(playlistId: Long, categoryId: String, now: () -> Long, onSource: (PagingSource<*, *>) -> Unit = {}): Flow<PagingData<ChannelRow>> =
        Pager(pagingConfig) {
            when (categoryId) {
                Categories.ALL -> dao.pagedChannels(playlistId, now())
                Categories.FAVORITES -> dao.pagedFavoriteChannels(playlistId, now())
                else -> dao.pagedChannelsInCategory(playlistId, categoryId, now())
            }.also(onSource)
        }.flow

    fun items(playlistId: Long, kind: ContentKind, categoryId: String): Flow<PagingData<StreamEntity>> =
        Pager(pagingConfig) {
            when (categoryId) {
                Categories.ALL -> dao.pagedItems(playlistId, kind)
                Categories.FAVORITES -> dao.pagedFavoriteItems(playlistId, kind)
                else -> dao.pagedItemsInCategory(playlistId, kind, categoryId)
            }
        }.flow

    suspend fun channelList(playlistId: Long, categoryId: String?): List<StreamEntity> =
        dao.channelList(playlistId, categoryId?.takeUnless { it == Categories.ALL || it == Categories.FAVORITES })

    fun stream(playlistId: Long, kind: ContentKind, streamId: String): Flow<StreamEntity?> =
        dao.observeStream(playlistId, kind, streamId)

    suspend fun getStream(playlistId: Long, kind: ContentKind, streamId: String) =
        dao.getStream(playlistId, kind, streamId)

    suspend fun search(playlistId: Long, kind: ContentKind, query: String, limit: Int) =
        dao.search(playlistId, kind, query, limit)

    suspend fun episodes(playlistId: Long, seriesId: String) = dao.episodes(playlistId, seriesId)

    suspend fun schedule(
        playlistId: Long,
        channelId: String,
        fromMs: Long = System.currentTimeMillis(),
        limit: Int = 300,
    ): List<ProgrammeEntity> = dao.schedule(playlistId, channelId, fromMs, limit)

    fun isFavorite(playlistId: Long, kind: ContentKind, streamId: String) = dao.observeIsFavorite(playlistId, kind, streamId)

    suspend fun toggleFavorite(playlistId: Long, kind: ContentKind, streamId: String) {
        if (dao.isFavorite(playlistId, kind, streamId)) dao.removeFavorite(playlistId, kind, streamId)
        else dao.addFavorite(FavoriteEntity(playlistId, kind, streamId))
    }

    fun favoriteItems(playlistId: Long, kind: ContentKind, limit: Int = 50) = dao.observeFavoriteItems(playlistId, kind, limit)

    fun continueWatching(playlistId: Long) = dao.observeContinueWatching(playlistId, 30)
    fun recentChannels(playlistId: Long) = dao.observeRecentChannels(playlistId, 30)
    fun history(playlistId: Long, keys: List<String>) = dao.observeHistory(playlistId, keys)
    suspend fun getHistory(playlistId: Long, key: String) = dao.getHistory(playlistId, key)
    suspend fun saveHistory(item: HistoryEntity) = dao.upsertHistory(item)
    suspend fun removeHistory(playlistId: Long, key: String) = dao.removeHistory(playlistId, key)
    suspend fun clearHistory() = dao.clearAllHistory()

    companion object {
        fun liveKey(streamId: String) = "LIVE:$streamId"
        fun movieKey(streamId: String) = "MOVIE:$streamId"
        fun episodeKey(episodeId: String) = "EPISODE:$episodeId"
    }
}
