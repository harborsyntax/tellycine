package app.mosaic.ui.library

import app.mosaic.data.settings.LibrarySort

/** A row of Library items; [label] is the category name when grouping, otherwise null. */
data class LibraryGroup<T>(val key: String, val label: String?, val items: List<T>)

/** Category name and its position in the playlist, for group headers and their order. */
data class CategoryInfo(val name: String, val order: Int)

/**
 * Sorts [items] and optionally splits them by category.
 *
 * [items] must already be in "most recent first" order, which [LibrarySort.RECENT] keeps.
 * Groups follow the playlist's category order; items whose category is unknown go last under "Other".
 */
fun <T> arrange(
    items: List<T>,
    sort: LibrarySort,
    groupByCategory: Boolean,
    categories: Map<String, CategoryInfo>,
    name: (T) -> String,
    categoryId: (T) -> String?,
    number: (T) -> Int?,
    sortOrder: (T) -> Int?,
): List<LibraryGroup<T>> {
    if (items.isEmpty()) return emptyList()
    val sorted = when (sort) {
        LibrarySort.RECENT -> items
        LibrarySort.NAME_ASC -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, name))
        LibrarySort.NAME_DESC -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, name).reversed())
        LibrarySort.PLAYLIST_ORDER -> items.sortedWith(
            compareBy<T, Int?>(nullsLast<Int>()) { number(it) }.thenBy(nullsLast<Int>()) { sortOrder(it) }
        )
    }
    if (!groupByCategory) return listOf(LibraryGroup("all", null, sorted))
    return sorted
        .groupBy { id -> categoryId(id)?.takeIf { it in categories } }
        .entries
        .sortedBy { (id, _) -> id?.let { categories[it]?.order } ?: Int.MAX_VALUE }
        .map { (id, list) -> LibraryGroup(id ?: "other", id?.let { categories[it]?.name } ?: "Other", list) }
}
