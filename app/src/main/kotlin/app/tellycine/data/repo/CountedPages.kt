package app.tellycine.data.repo

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pages from [page] (a query with LIMIT and OFFSET), with the total from [count]: like Room's own
 * paging, which counts by running the whole query, sort included. Sorting 50,000 titles just to
 * count them took ten times longer than counting them. Like Room's, it's invalidated when one of
 * [tables] changes.
 */
class CountedPages<T : Any>(
    private val db: RoomDatabase,
    tables: Array<String>,
    private val count: suspend () -> Int,
    private val page: suspend (limit: Int, offset: Int) -> List<T>,
) : PagingSource<Int, T>() {
    private val observer = object : InvalidationTracker.Observer(tables) {
        override fun onInvalidated(tables: Set<String>) = invalidate()
    }
    private val observing = AtomicBoolean(false)

    @Volatile
    private var total = -1

    init {
        registerInvalidatedCallback { if (observing.get()) db.invalidationTracker.removeObserver(observer) }
    }

    override val jumpingSupported: Boolean get() = true

    override fun getRefreshKey(state: PagingState<Int, T>): Int? =
        state.anchorPosition?.let { maxOf(0, it - state.config.initialLoadSize / 2) }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        if (observing.compareAndSet(false, true)) db.invalidationTracker.addObserver(observer)
        val key = params.key ?: 0
        return db.withTransaction {
            val total = if (params is LoadParams.Refresh || total < 0) count().also { total = it } else total
            val (limit, offset) = when (params) {
                is LoadParams.Refresh -> params.loadSize to if (key >= total) maxOf(0, total - params.loadSize) else key
                is LoadParams.Prepend -> if (key < params.loadSize) key to 0 else params.loadSize to key - params.loadSize
                is LoadParams.Append -> params.loadSize to key
            }
            val data = page(limit, offset)
            if (invalid) {
                LoadResult.Invalid()
            } else {
                LoadResult.Page(
                    data = data,
                    prevKey = offset.takeIf { it > 0 },
                    nextKey = (offset + data.size).takeIf { data.isNotEmpty() && it < total },
                    itemsBefore = offset,
                    itemsAfter = maxOf(0, total - offset - data.size),
                )
            }
        }
    }
}
