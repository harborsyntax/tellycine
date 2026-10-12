package app.tellycine.data.repo

import androidx.sqlite.db.SimpleSQLiteQuery
import app.tellycine.core.ContentKind
import app.tellycine.data.db.VISIBLE
import app.tellycine.data.settings.VodSort

/** Queries for pages of movies or series: see [items]. */
object ItemQueries {
    /** A list's count and its pages, from [items]. */
    class Query(private val from: String, private val order: String, private val args: List<Any>) {
        fun count() = SimpleSQLiteQuery("SELECT COUNT(*) $from", args.toTypedArray())
        fun page(limit: Int, offset: Int) =
            SimpleSQLiteQuery("SELECT s.* $from ORDER BY $order LIMIT ? OFFSET ?", (args + limit + offset).toTypedArray())
    }

    /**
     * [kind] from [playlistIds] in [categoryId] (all of them, favourites, a playlist's own category
     * or one merged by name: see [Categories]), in [sort]'s order. Ties, and titles without the
     * date sorted by, keep the provider's order, after those with one.
     */
    fun items(playlistIds: List<Long>, kind: ContentKind, categoryId: String, sort: VodSort): Query {
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
        // Dates sort newest first; missing ones (null) come last that way. Each has an index.
        val order = when (sort) {
            VodSort.PROVIDER -> if (favorites) "f.addedAt DESC" else "s.playlistId, s.sortOrder"
            VodSort.ADDED -> "s.addedAt DESC, s.playlistId, s.sortOrder"
            VodSort.RELEASED -> "s.released DESC, s.playlistId, s.sortOrder"
            VodSort.UPDATED -> "s.updatedAt DESC, s.playlistId, s.sortOrder"
            VodSort.NAME -> "s.sortName, s.playlistId, s.sortOrder"
        }
        return Query(from, order, args)
    }

    /** The tables whose changes change these lists. */
    val TABLES = arrayOf("streams", "favorites", "hidden_categories", "categories")
}
