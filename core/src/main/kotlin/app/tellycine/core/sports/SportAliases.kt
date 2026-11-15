package app.tellycine.core.sports

/**
 * Names someone has said are the same ("Same as…" on an event), kept so the Sports Hub puts them
 * together from then on: a side's name ("bills de buffalo" is "buffalo bills" in American
 * football), or, where sides don't settle it, a whole event's (its identity, as the hub groups
 * events by). Keys are as [SportNames.key] makes them.
 *
 * Stored as [lines], one a name: "side", the sport, the name and the one it's the same as; or
 * "event", the event's identity and the one it's the same as, separated by tabs.
 */
class SportAliases(val lines: List<String>) {
    private val sides = HashMap<String, String>()
    private val events = HashMap<String, String>()

    init {
        for (line in lines) {
            val parts = line.split('\t')
            when {
                parts.size == 4 && parts[0] == SIDE -> sides["${parts[1]}|${parts[2]}"] = parts[3]
                parts.size == 3 && parts[0] == EVENT -> events[parts[1]] = parts[2]
            }
        }
    }

    val isEmpty: Boolean get() = sides.isEmpty() && events.isEmpty()

    /** How many names have been said to be the same. */
    val size: Int get() = sides.size + events.size

    /** The name [key] (a [sport] side's) has been said to be, or [key] itself. */
    fun side(sport: Sport, key: String): String = follow(key) { sides["${sport.name}|$it"] }

    /** The event [identity] has been said to be, or [identity] itself. */
    fun event(identity: String): String = follow(identity) { events[it] }

    /** Where a chain of names leads ("a" is "b", "b" is "c"), stopping before it goes round. */
    private fun follow(start: String, next: (String) -> String?): String {
        var current = start
        val seen = HashSet<String>()
        while (seen.add(current)) current = next(current) ?: return current
        return current
    }

    /** With [more] said to be the same too; a name said again takes its new place. */
    fun plus(more: List<String>): SportAliases {
        val keys = more.map { it.substringBeforeLast('\t') }.toSet()
        return SportAliases(lines.filter { it.substringBeforeLast('\t') !in keys } + more)
    }

    override fun equals(other: Any?): Boolean = other is SportAliases && other.lines == lines
    override fun hashCode(): Int = lines.hashCode()

    companion object {
        private const val SIDE = "side"
        private const val EVENT = "event"

        val NONE = SportAliases(emptyList())

        /** [from], a [sport] side's name, is [to]. */
        fun sideLine(sport: Sport, from: String, to: String): String = listOf(SIDE, sport.name, from, to).joinToString("\t")

        /** The event [from] (an identity) is [to]. */
        fun eventLine(from: String, to: String): String = listOf(EVENT, from, to).joinToString("\t")
    }
}
