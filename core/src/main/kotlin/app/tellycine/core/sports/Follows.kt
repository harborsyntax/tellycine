package app.tellycine.core.sports

/**
 * The teams and players someone follows, for finding their events: by who's playing when the
 * title says ("Arsenal v Chelsea"), and otherwise by the title and description naming them.
 */
class Follows(names: List<String>) {
    private class Followed(val name: String, val figure: SportFigure?, val keys: List<String>)

    private val followed: List<Followed> = names.mapNotNull { name ->
        val figure = SportCatalogue.find(name)
        val keys = figure?.keys ?: listOf(SportNames.key(name)).filter { it.isNotEmpty() }
        if (keys.isEmpty()) null else Followed(name, figure, keys)
    }

    val isEmpty: Boolean get() = followed.isEmpty()

    /** The first of the names followed that an event of [sport] is for, or null if it's none of theirs. */
    fun match(sport: Sport, home: String?, away: String?, text: String): String? = matchAll(sport, home, away, text).firstOrNull()

    /** All the names followed that an event of [sport] is for, in the order they're followed. */
    fun matchAll(sport: Sport, home: String?, away: String?, text: String): List<String> {
        if (followed.isEmpty()) return emptyList()
        val sides = listOfNotNull(home, away)
        val padded by lazy { " " + SportNames.key(text) + " " }
        return followed.filter { f ->
            val figure = f.figure
            when {
                figure != null && figure.sport != sport -> false
                figure?.followsWholeSport == true -> true
                sides.isNotEmpty() -> sides.any { isSide(f, it) }
                else -> f.keys.any { padded.contains(" $it ") }
            }
        }.map { it.name }
    }

    /** Whether [side] is [f]: the same team, or a name with theirs in it that isn't another team's. */
    private fun isSide(f: Followed, side: String): Boolean {
        val known = SportCatalogue.find(side)
        if (known != null) return known == f.figure || SportNames.key(known.name) in f.keys
        val key = " " + SportNames.key(side) + " "
        return f.keys.any { key.contains(" $it ") }
    }
}
