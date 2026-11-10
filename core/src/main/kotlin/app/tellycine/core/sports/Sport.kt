package app.tellycine.core.sports

/** The sports the Sports Hub knows by name. Anything else a guide calls sport is [OTHER]. */
enum class Sport(val label: String) {
    FOOTBALL("Football"),
    F1("Formula 1"),
    MOTOGP("MotoGP"),
    GOLF("Golf"),
    TENNIS("Tennis"),
    SNOOKER("Snooker"),
    DARTS("Darts"),
    BOXING("Boxing"),
    OTHER("Other sport"),
    ;

    companion object {
        /** The sports that can be followed, in the order they're offered. */
        val FOLLOWABLE: List<Sport> = entries - OTHER

        fun of(name: String?): Sport? = entries.firstOrNull { it.name == name }
    }
}

/**
 * What a programme is, as far as its sport goes. Magazines, news and preview shows are left out of
 * the Sports Hub altogether; replays and highlights are shown only when asked for.
 */
enum class AiringKind {
    /** Said to be live. */
    LIVE,

    /** Coverage of the event itself, not said to be live or a replay. */
    EVENT,

    HIGHLIGHTS,
    REPLAY,

    /** News, previews, talk and documentaries. */
    MAGAZINE,
    ;

    /** Shown in the hub when replays and highlights are turned off. */
    val isCoverage get() = this == LIVE || this == EVENT

    companion object {
        fun of(name: String?): AiringKind? = entries.firstOrNull { it.name == name }
    }
}

/** What [SportClassifier] made of a programme or a channel's name. */
data class SportMatch(
    val sport: Sport,
    val kind: AiringKind,
    /** What to call it: "Arsenal v Chelsea", or the event's name ("Brazilian Grand Prix"). */
    val title: String,
    /** The competition or tour, when it says ("Premier League", "ATP Finals"). */
    val competition: String? = null,
    /** The part of the event: "Qualifying", "Final round", "Semi-final". */
    val session: String? = null,
    /** The two sides of "A v B", as written. */
    val home: String? = null,
    val away: String? = null,
)
