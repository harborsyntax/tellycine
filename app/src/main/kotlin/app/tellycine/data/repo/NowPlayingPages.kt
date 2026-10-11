package app.tellycine.data.repo

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.tellycine.data.db.ChannelRow

/**
 * Pages of live channels from [rows], with what's on now filled in for each page by [fill].
 * Invalidating either source invalidates the other.
 *
 * Room counts a list by running its whole query, so keeping the programme lookups out of [rows]
 * keeps that count cheap; [fill] only looks them up for the channels of the page being loaded.
 */
class NowPlayingPages(
    private val rows: PagingSource<Int, ChannelRow>,
    private val fill: suspend (List<ChannelRow>) -> List<ChannelRow>,
) : PagingSource<Int, ChannelRow>() {
    init {
        rows.registerInvalidatedCallback(::invalidate)
        registerInvalidatedCallback(rows::invalidate)
    }

    override val jumpingSupported: Boolean get() = rows.jumpingSupported

    override fun getRefreshKey(state: PagingState<Int, ChannelRow>): Int? = rows.getRefreshKey(state)

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ChannelRow> =
        when (val result = rows.load(params)) {
            is LoadResult.Page -> LoadResult.Page(fill(result.data), result.prevKey, result.nextKey, result.itemsBefore, result.itemsAfter)
            else -> result
        }
}
