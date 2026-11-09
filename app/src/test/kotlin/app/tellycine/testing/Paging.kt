package app.tellycine.testing

import androidx.paging.PagingSource

/**
 * The first [size] items from a new [source], loaded again from another new one if the data changed
 * while it loaded (the load is then invalid), as Paging does: a test's own writes, or the app's as
 * it starts, can still be reaching the database's watchers.
 */
suspend fun <T : Any> firstPage(size: Int = 500, source: () -> PagingSource<Int, T>): PagingSource.LoadResult.Page<Int, T> {
    repeat(10) {
        val result = source().load(PagingSource.LoadParams.Refresh(null, size, false))
        if (result is PagingSource.LoadResult.Page) return result
    }
    error("The data kept changing")
}
