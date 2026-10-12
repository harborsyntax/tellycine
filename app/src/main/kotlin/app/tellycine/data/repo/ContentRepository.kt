package app.tellycine.data.repo

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import app.tellycine.core.ContentKind
import app.tellycine.data.db.CategoryEntity
import app.tellycine.data.db.CategoryVisibility
import app.tellycine.data.db.HiddenCategoryEntity
import app.tellycine.data.db.ChannelRow
import app.tellycine.data.db.FavoriteEntity
import app.tellycine.data.db.HistoryEntity
import app.tellycine.data.db.NowPlaying
import app.tellycine.data.db.ProgrammeEntity
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.db.TellycineDatabase
import app.tellycine.data.settings.CategorySort
import app.tellycine.data.settings.VodOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.text.Collator

/** Pseudo category ids understood by the paging queries. */
object Categories {
    const val ALL = "__all__"
    const val FAVORITES = "__favorites__"
    private const val NAMED = "__name__:"

    /**
     * The key categories are merged by across playlists: the name without surrounding spaces, in
     * lower case. Only A–Z are lowered, exactly as the database's LOWER and TRIM do.
     */
    fun nameKey(name: String): String =
        buildString { name.trim(' ').forEach { append(if (it in 'A'..'Z') it + ('a' - 'A') else it) } }

    /** The id of the category merging every playlist's categories with [key] ([nameKey]). */
    fun named(key: String) = NAMED + key

    /** The [nameKey] of a merged category's [id], or null for any other id. */
    fun nameKeyOf(id: String): String? = id.takeIf { it.startsWith(NAMED) }?.removePrefix(NAMED)

    /** [categories] in the [sort] order: as given (the provider's), or by name. */
    fun sorted(categories: List<CategoryEntity>, sort: CategorySort): List<CategoryEntity> = when (sort) {
        CategorySort.PROVIDER -> categories
        CategorySort.NAME -> {
            val collator = Collator.getInstance()
            categories.sortedWith { a, b -> collator.compare(a.name.trim(), b.name.trim()) }
        }
    }

    /**
     * Several playlists' [categories] as one list: categories with the same name ([nameKey]) become one,
     * where the first of them was, with their counts added up. [playlistIds] gives the playlists' order.
     */
    fun merge(categories: List<CategoryEntity>, playlistIds: List<Long>): List<CategoryEntity> {
        val order = playlistIds.withIndex().associate { (i, id) -> id to i }
        return categories
            .sortedWith(compareBy<CategoryEntity>({ order[it.playlistId] ?: Int.MAX_VALUE }, { it.sortOrder }))
            .groupBy { nameKey(it.name) }
            .entries.mapIndexed { i, (key, group) ->
                val first = group.first()
                CategoryEntity(
                    playlistId = 0,
                    kind = first.kind,
                    categoryId = named(key),
                    name = first.name.trim(),
                    sortOrder = i,
                    itemCount = group.sumOf { it.itemCount },
                )
            }
    }
}

/** A guide programme found by search, with the channel it airs on. */
data class ProgrammeHit(val programme: ProgrammeEntity, val channel: StreamEntity)

/** How far back programme search looks; older entries are outside any catch-up archive the app keeps. */
private const val PAST_SEARCH_WINDOW = 14 * 24 * 60 * 60 * 1000L

class ContentRepository(private val db: TellycineDatabase) {
    private val dao = db.content()

    private val pagingConfig = PagingConfig(
        pageSize = 60,
        prefetchDistance = 90,
        initialLoadSize = 120,
        enablePlaceholders = true,
    )

    fun categories(playlistId: Long, kind: ContentKind): Flow<List<CategoryEntity>> =
        dao.observeCategories(playlistId, kind)

    suspend fun category(playlistId: Long, kind: ContentKind, categoryId: String): CategoryEntity? =
        dao.getCategory(playlistId, kind, categoryId)

    /** The categories of one or more playlists; several playlists' are merged by name ([Categories.merge]). */
    fun categories(playlistIds: List<Long>, kind: ContentKind): Flow<List<CategoryEntity>> = when (playlistIds.size) {
        0 -> flowOf(emptyList())
        1 -> dao.observeCategories(playlistIds[0], kind)
        else -> dao.observeCategoriesIn(playlistIds, kind).map { Categories.merge(it, playlistIds) }
    }

    fun categoryVisibility(playlistId: Long, kind: ContentKind): Flow<List<CategoryVisibility>> =
        dao.observeCategoryVisibility(playlistId, kind)

    suspend fun setCategoriesVisible(playlistId: Long, kind: ContentKind, categoryIds: List<String>, visible: Boolean) {
        if (categoryIds.isEmpty()) return
        if (visible) {
            // Chunked to stay below SQLite's bound-parameter limit.
            categoryIds.chunked(500).forEach { dao.showCategories(playlistId, kind, it) }
        } else {
            dao.hideCategories(categoryIds.map { HiddenCategoryEntity(playlistId, kind, it) })
        }
    }

    /**
     * Live channels of one or more playlists, with what's on now on each as of [now] when a page
     * loads: invalidating the source (see [onSource]) brings it up to date. Several playlists'
     * channels come one playlist after another, and their categories are merged ones
     * ([Categories.named]).
     */
    fun channels(playlistIds: List<Long>, categoryId: String, now: () -> Long, onSource: (PagingSource<*, *>) -> Unit = {}): Flow<PagingData<ChannelRow>> {
        if (playlistIds.isEmpty()) return flowOf(PagingData.empty())
        return Pager(pagingConfig) { channelPages(playlistIds, categoryId, now).also(onSource) }.flow
    }

    /** The paging source behind [channels]. */
    fun channelPages(playlistIds: List<Long>, categoryId: String, now: () -> Long): PagingSource<Int, ChannelRow> {
        val nameKey = Categories.nameKeyOf(categoryId)
        // A merged category goes on working while the section switches to a single playlist.
        val rows = if (playlistIds.size == 1 && nameKey == null) {
            val playlistId = playlistIds[0]
            when (categoryId) {
                Categories.ALL -> dao.pagedChannels(playlistId)
                Categories.FAVORITES -> dao.pagedFavoriteChannels(playlistId)
                else -> dao.pagedChannelsInCategory(playlistId, categoryId)
            }
        } else {
            when {
                categoryId == Categories.FAVORITES -> dao.pagedFavoriteChannelsIn(playlistIds)
                nameKey != null -> dao.pagedChannelsInNamedCategory(playlistIds, nameKey)
                else -> dao.pagedChannelsIn(playlistIds)
            }
        }
        return NowPlayingPages(rows) { withNowPlaying(it, now()) }
    }

    /** [rows] with what's on now ([now]) on each, looked up together for each playlist's channels. */
    suspend fun withNowPlaying(rows: List<ChannelRow>, now: Long): List<ChannelRow> {
        val found = rows.filter { it.stream.epgChannelId != null }
            .groupBy { it.stream.playlistId }
            .flatMap { (playlistId, group) ->
                group.map { it.stream.streamId }.chunked(500)
                    .flatMap { dao.nowPlaying(playlistId, it, now) }
                    .map { (playlistId to it.streamId) to it }
            }
            .toMap()
        return rows.map { row ->
            found[row.stream.playlistId to row.stream.streamId]
                ?.let { row.copy(nowTitle = it.nowTitle, nowStart = it.nowStart, nowStop = it.nowStop) }
                ?: row
        }
    }

    /** What's on [row]'s channel now, for a row on screen whose programme has ended. */
    suspend fun nowPlaying(row: ChannelRow, now: Long): NowPlaying? =
        if (row.stream.epgChannelId == null) null else dao.nowPlaying(row.stream.playlistId, listOf(row.stream.streamId), now).firstOrNull()

    /** Where [channel] comes in [channels] of [playlistIds] and [categoryId]: how many channels are before it. */
    suspend fun channelPosition(playlistIds: List<Long>, categoryId: String, channel: StreamEntity): Int {
        val nameKey = Categories.nameKeyOf(categoryId)
        return when {
            categoryId == Categories.FAVORITES -> dao.favoriteChannelsBefore(playlistIds, channel.playlistId, channel.streamId)
            nameKey != null -> dao.channelsBeforeInNamedCategory(playlistIds, nameKey, channel.playlistId, channel.sortOrder)
            categoryId == Categories.ALL -> dao.channelsBefore(playlistIds, channel.playlistId, channel.sortOrder)
            else -> dao.channelsBeforeInCategory(channel.playlistId, categoryId, channel.sortOrder)
        }
    }

    /** Movies or series of one or more playlists, in [order] ([ItemQueries.items]). */
    fun items(playlistIds: List<Long>, kind: ContentKind, categoryId: String, order: VodOrder): Flow<PagingData<StreamEntity>> {
        if (playlistIds.isEmpty()) return flowOf(PagingData.empty())
        return Pager(pagingConfig) { itemPages(playlistIds, kind, categoryId, order) }.flow
    }

    /** The paging source behind [items]. */
    fun itemPages(playlistIds: List<Long>, kind: ContentKind, categoryId: String, order: VodOrder): PagingSource<Int, StreamEntity> {
        val query = ItemQueries.items(playlistIds, kind, categoryId, order)
        return CountedPages(db, ItemQueries.TABLES, count = { dao.countItems(query.count()) }) { limit, offset ->
            query.page(limit, offset, dao::items, dao::countItems)
        }
    }

    suspend fun channelList(playlistId: Long, categoryId: String?): List<StreamEntity> =
        dao.channelList(playlistId, categoryId?.takeUnless { it == Categories.ALL || it == Categories.FAVORITES })

    fun stream(playlistId: Long, kind: ContentKind, streamId: String): Flow<StreamEntity?> =
        dao.observeStream(playlistId, kind, streamId)

    suspend fun getStream(playlistId: Long, kind: ContentKind, streamId: String) =
        dao.getStream(playlistId, kind, streamId)

    suspend fun search(playlistId: Long, kind: ContentKind, query: String, limit: Int) =
        dao.search(playlistId, kind, query, limit)

    /** Guide programmes matching [query], each with the first visible channel that carries it. */
    suspend fun searchProgrammes(
        playlistId: Long,
        query: String,
        limit: Int,
        now: Long = System.currentTimeMillis(),
    ): List<ProgrammeHit> {
        val programmes = dao.searchProgrammes(playlistId, query, now, now - PAST_SEARCH_WINDOW, limit)
        if (programmes.isEmpty()) return emptyList()
        val channels = programmes.map { it.channelId }.distinct().chunked(500)
            .flatMap { dao.channelsForGuideIds(playlistId, it) }
            .groupBy { it.epgChannelId }
            .mapValues { (_, list) -> list.minBy { it.sortOrder } }
        return programmes
            .distinctBy { it.channelId to it.startMs }
            .mapNotNull { p -> channels[p.channelId]?.let { ProgrammeHit(p, it) } }
    }

    suspend fun episodes(playlistId: Long, seriesId: String) = dao.episodes(playlistId, seriesId)

    suspend fun schedule(
        playlistId: Long,
        channelId: String,
        fromMs: Long = System.currentTimeMillis(),
        limit: Int = 300,
    ): List<ProgrammeEntity> =
        // Defensive: a guide can list the same programme twice.
        dao.schedule(playlistId, channelId, fromMs, limit).distinctBy { it.startMs }

    suspend fun programmesBetween(playlistId: Long, channelId: String, fromMs: Long, toMs: Long): List<ProgrammeEntity> =
        dao.programmesBetween(playlistId, channelId, fromMs, toMs).distinctBy { it.startMs }

    fun isFavorite(playlistId: Long, kind: ContentKind, streamId: String) = dao.observeIsFavorite(playlistId, kind, streamId)

    suspend fun toggleFavorite(playlistId: Long, kind: ContentKind, streamId: String) {
        if (dao.isFavorite(playlistId, kind, streamId)) dao.removeFavorite(playlistId, kind, streamId)
        else dao.addFavorite(FavoriteEntity(playlistId, kind, streamId))
    }

    fun favoriteItems(playlistId: Long, kind: ContentKind, limit: Int = 50) = dao.observeFavoriteItems(playlistId, kind, limit)

    fun continueWatching(playlistId: Long) = dao.observeContinueWatching(playlistId, 50)
    fun recentChannels(playlistId: Long) = dao.observeRecentChannels(playlistId, 60)
    fun history(playlistId: Long, keys: List<String>) = dao.observeHistory(playlistId, keys)
    suspend fun getHistory(playlistId: Long, key: String) = dao.getHistory(playlistId, key)
    suspend fun saveHistory(item: HistoryEntity) = dao.upsertHistory(item)
    /** Records that playback of [item] started elsewhere (external player), keeping any resume point. */
    suspend fun touchHistory(item: HistoryEntity) {
        val existing = dao.getHistory(item.playlistId, item.itemKey)
        dao.upsertHistory(existing?.copy(lastWatchedAt = item.lastWatchedAt) ?: item)
    }

    suspend fun removeHistory(playlistId: Long, key: String) = dao.removeHistory(playlistId, key)

    /** Puts back a history entry exactly as it was, e.g. to undo a removal. */
    suspend fun restoreHistory(item: HistoryEntity) = dao.upsertHistory(item)
    suspend fun clearContinueWatching(playlistId: Long) = dao.clearContinueWatching(playlistId)
    suspend fun clearRecentChannels(playlistId: Long) = dao.clearRecentChannels(playlistId)
    suspend fun setFavorite(playlistId: Long, kind: ContentKind, streamId: String, favorite: Boolean) {
        if (favorite) dao.addFavorite(FavoriteEntity(playlistId, kind, streamId)) else dao.removeFavorite(playlistId, kind, streamId)
    }
    suspend fun clearHistory() = dao.clearAllHistory()

    companion object {
        fun liveKey(streamId: String) = "LIVE:$streamId"
        fun movieKey(streamId: String) = "MOVIE:$streamId"
        fun episodeKey(episodeId: String) = "EPISODE:$episodeId"
    }
}
