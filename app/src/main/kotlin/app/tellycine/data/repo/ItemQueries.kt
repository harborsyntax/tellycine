package app.tellycine.data.repo

import androidx.sqlite.db.SimpleSQLiteQuery
import app.tellycine.core.ContentKind
import app.tellycine.data.db.VISIBLE
import app.tellycine.data.settings.VodOrder
import app.tellycine.data.settings.VodSort

/** Queries for pages of movies or series: see [items]. */
object ItemQueries {
    /**
     * A list's count and its pages, from [items]. Sorted oldest first by a [date], titles without
     * one come last: those with one are read through the date's index in that order, then the rest,
     * rather than sorting the whole list for every page.
     */
    class Query(private val from: String, private val order: String, private val args: List<Any>, private val date: String? = null) {
        fun count() = SimpleSQLiteQuery("SELECT COUNT(*) $from", args.toTypedArray())

        /** Items [offset] to [offset] + [limit], with [items] and [count] to run its queries. */
        suspend fun <T> page(
            limit: Int,
            offset: Int,
            items: suspend (SimpleSQLiteQuery) -> List<T>,
            count: suspend (SimpleSQLiteQuery) -> Int,
        ): List<T> {
            if (date == null) return items(select(from, order, limit, offset))
            // (Every [from] ends with a WHERE clause.)
            val dated = count(SimpleSQLiteQuery("SELECT COUNT(*) $from AND $date IS NOT NULL", args.toTypedArray()))
            val first = if (offset < dated) items(select("$from AND $date IS NOT NULL", order, limit, offset)) else emptyList()
            if (first.size >= limit) return first
            return first + items(select("$from AND $date IS NULL", PROVIDER_ORDER, limit - first.size, maxOf(0, offset - dated)))
        }

        private fun select(from: String, order: String, limit: Int, offset: Int) =
            SimpleSQLiteQuery("SELECT s.* $from ORDER BY $order LIMIT ? OFFSET ?", (args + limit + offset).toTypedArray())
    }

    /**
     * [kind] from [playlistIds] in [categoryId] (all of them, favourites, a playlist's own category
     * or one merged by name: see [Categories]), in [order]. Ties, and titles without the date
     * sorted by, keep the provider's order, after those with one either way round.
     */
    fun items(playlistIds: List<Long>, kind: ContentKind, categoryId: String, order: VodOrder): Query {
        require(playlistIds.isNotEmpty())
        val ids = playlistIds.joinToString(", ") { "?" }
        val args = ArrayList<Any>(playlistIds)
        val nameKey = Categories.nameKeyOf(categoryId)
        val favorites = categoryId == Categories.FAVORITES
        val from = when {
            favorites -> {
                args += kind.name
                """FROM streams s INNER JOIN favorites f
                     ON f.playlistId = s.playlistId AND f.kind = s.kind AND f.streamId = s.streamId
                   WHERE s.playlistId IN ($ids) AND s.kind = ? AND $VISIBLE"""
            }
            nameKey != null -> {
                // Starting from the few categories with that name, rather than every title's category.
                args += nameKey
                args += kind.name
                """FROM categories c CROSS JOIN streams s
                     ON s.playlistId = c.playlistId AND s.kind = c.kind AND s.categoryId = c.categoryId
                   WHERE c.playlistId IN ($ids) AND LOWER(TRIM(c.name)) = ? AND c.kind = ? AND $VISIBLE"""
            }
            categoryId == Categories.ALL -> {
                args += kind.name
                "FROM streams s WHERE s.playlistId IN ($ids) AND s.kind = ? AND $VISIBLE"
            }
            else -> {
                args += kind.name
                args += categoryId
                "FROM streams s WHERE s.playlistId IN ($ids) AND s.kind = ? AND s.categoryId = ?"
            }
        }
        val oldestFirst = dateColumn(order.sort)?.takeIf { !order.descending }
        return Query(from, orderBy(order, favorites), args, date = oldestFirst)
    }

    private const val PROVIDER_ORDER = "s.playlistId, s.sortOrder"

    private fun dateColumn(sort: VodSort): String? = when (sort) {
        VodSort.ADDED -> "s.addedAt"
        VodSort.RELEASED -> "s.released"
        VodSort.UPDATED -> "s.changedAt"
        VodSort.AIRED -> "s.aired"
        // Not a date, but sorted the same way: titles without one last.
        VodSort.RATING -> "s.tmdbRating"
        VodSort.NAME, VodSort.PROVIDER -> null
    }

    private fun orderBy(order: VodOrder, favorites: Boolean): String {
        val down = order.descending
        val date = dateColumn(order.sort) ?: return when (order.sort) {
            VodSort.NAME -> "s.sortName ${if (down) "DESC" else "ASC"}, $PROVIDER_ORDER"
            // Favourites list as they were added, newest first, unless turned round.
            else -> when {
                favorites -> if (down) "f.addedAt" else "f.addedAt DESC"
                down -> "s.playlistId DESC, s.sortOrder DESC"
                else -> PROVIDER_ORDER
            }
        }
        // Newest first leaves missing dates (null) at the end; oldest first reads them last ([Query]).
        return "$date ${if (down) "DESC" else "ASC"}, $PROVIDER_ORDER"
    }

    /** The tables whose changes change these lists. */
    val TABLES = arrayOf("streams", "favorites", "hidden_categories", "categories")
}
