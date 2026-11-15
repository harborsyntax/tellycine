package app.tellycine.data.sports

import app.tellycine.core.sports.AiringKind
import app.tellycine.core.sports.Competition
import app.tellycine.core.sports.Competitions
import app.tellycine.core.sports.Follows
import app.tellycine.core.sports.SameEvent
import app.tellycine.core.sports.Sport
import app.tellycine.core.sports.SportCatalogue
import app.tellycine.core.sports.SportClassifier
import app.tellycine.core.sports.SportAliases
import app.tellycine.core.sports.SportNames
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.SportAiringEntity
import app.tellycine.data.db.SportCategory
import app.tellycine.data.db.SportsStatus
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.db.TellycineDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.math.abs

/** One side of "A v B": a team or a player, with a code and colour to show it by. */
data class SportSide(val name: String, val code: String, val colour: Int?)

/** Somewhere to watch an event: a playlist's channel, and the programme on it. */
data class SportSource(
    val playlist: PlaylistEntity,
    val stream: StreamEntity,
    val airing: SportAiringEntity,
) {
    fun isOn(now: Long): Boolean = airing.startMs == 0L || (airing.startMs <= now && airing.stopMs > now)
}

/** An event, wherever it's on: the same match on several channels and playlists is one event. */
data class SportEvent(
    val key: String,
    val sport: Sport,
    val kind: AiringKind,
    val title: String,
    val competition: String?,
    val session: String?,
    val home: SportSide?,
    val away: SportSide?,
    val startMs: Long,
    val stopMs: Long,
    /** Where it's on, those on now first. */
    val sources: List<SportSource>,
    /** The competition it's in, as the hub files it, for filtering by country and league. */
    val league: Competition? = null,
    /** The teams and players followed that it's for. */
    val followed: List<String>,
) {
    val isFollowed: Boolean get() = followed.isNotEmpty()

    fun isLive(now: Long): Boolean = startMs <= now && stopMs > now && sources.any { it.isOn(now) }

    /** What it's part of, as said above its title: "Premier League", "Formula 1 · Qualifying". */
    val caption: String get() = listOfNotNull(competition ?: sport.label, session).joinToString(" · ")

    /** The sources to watch it on at [now]: those it's on now, or all of them. */
    fun sourcesAt(now: Long): List<SportSource> = sources.filter { it.isOn(now) }.ifEmpty { sources }
}

/** The events the Sports Hub shows: sport in the guides and channel names, gathered into events. */
@OptIn(ExperimentalCoroutinesApi::class)
class SportsRepository(private val db: TellycineDatabase) {
    private val dao = db.sports()

    /**
     * The events on between [fromMs] and [toMs], of every sport, kept up to date: coverage, and
     * replays and highlights too if [replays]; events channels' names announce if [eventChannels].
     * Those of teams and players in [teams] say so.
     */
    fun events(
        fromMs: Long,
        toMs: Long,
        replays: Boolean,
        eventChannels: Boolean,
        teams: List<String>,
        aliases: SportAliases = SportAliases.NONE,
        /** Categories left out of the hub, as [categoryKey] makes them. */
        excludedCategories: Set<String> = emptySet(),
    ): Flow<List<SportEvent>> {
        val kinds = if (replays) listOf(AiringKind.LIVE, AiringKind.EVENT, AiringKind.HIGHLIGHTS, AiringKind.REPLAY) else listOf(AiringKind.LIVE, AiringKind.EVENT)
        val follows = Follows(teams)
        return combine(dao.observeBetween(fromMs, toMs, Sport.entries, kinds), db.playlists().observeAll()) { airings, playlists -> airings to playlists }
            .mapLatest { (airings, playlists) ->
                build(airings.filter { eventChannels || !it.isEventChannel }, playlists, follows, replays, aliases, excludedCategories)
            }
            .flowOn(Dispatchers.IO)
    }

    /** What was found in each playlist, and when. */
    fun status(): Flow<List<SportsStatus>> = dao.observeStatus()

    /** The categories sport was found in, the most first, for leaving some out of the hub. */
    fun categories(): Flow<List<SportCategory>> = dao.observeCategories()

    /** Teams and players in the events found, for suggestions while choosing what to follow. */
    suspend fun sideNames(): List<String> = dao.sideNames()

    private suspend fun build(
        airings: List<SportAiringEntity>,
        playlists: List<PlaylistEntity>,
        follows: Follows,
        replays: Boolean,
        aliases: SportAliases,
        excluded: Set<String>,
    ): List<SportEvent> {
        val byId = playlists.associateBy { it.id }
        // Each airing's channels: those with its guide id, or the event channel, if they're not hidden.
        val sources = ArrayList<SportSource>()
        for ((pid, list) in airings.groupBy { it.playlistId }) {
            val playlist = byId[pid] ?: continue
            val guideIds = list.filter { !it.isEventChannel }.map { it.channelId }.distinct()
            val byGuideId = HashMap<String, MutableList<StreamEntity>>()
            for (ids in guideIds.chunked(MAX_VARIABLES)) {
                for (s in dao.channelsFor(pid, ids)) byGuideId.getOrPut(s.epgChannelId ?: continue) { ArrayList() } += s
            }
            val streamIds = list.mapNotNull { it.streamId }.distinct()
            val byStreamId = HashMap<String, StreamEntity>()
            for (ids in streamIds.chunked(MAX_VARIABLES)) for (s in dao.channelsById(pid, ids)) byStreamId[s.streamId] = s
            for (a in list) {
                val streams = if (a.streamId != null) listOfNotNull(byStreamId[a.streamId]) else byGuideId[a.channelId].orEmpty()
                for (s in streams) if (excluded.isEmpty() || categoryKey(pid, s.categoryId) !in excluded) sources += SportSource(playlist, s, a)
            }
        }
        return group(unmarkedAsReplays(sources, replays), follows, aliases)
    }

    companion object {
        private const val MAX_VARIABLES = 900

        /** How far apart the same event can start on different channels: build-up starts earlier on some. */
        private const val SAME_EVENT_MS = 4 * 60 * 60 * 1000L
        private const val SAME_SHOW_MS = 60 * 60 * 1000L

        /** How far apart events nearly the same by name can start and be one: the same match, not a replay. */
        private const val NEAR_START_MS = 45 * 60 * 1000L

        /** A broadcaster's sports channels that say "Live" this often in a playlist's guide say it of all their live coverage. */
        private const val MARKS_LIVE = 3

        /**
         * UK sports broadcasters (Sky Sports, TNT Sports, Premier Sports) say "Live" on their live
         * coverage, and show matches again under plain names ("The Emirates FA Cup"). So where a
         * broadcaster's sports channels in a playlist say "Live", their coverage that doesn't is
         * shown again: left out, or with [replays], shown as replays. Channels that aren't sports
         * channels (ITV4's snooker) and broadcasters that never say "Live" are as they were.
         */
        fun unmarkedAsReplays(sources: List<SportSource>, replays: Boolean): List<SportSource> {
            val classifier = SportClassifier()
            val sportsChannel = HashMap<String, Boolean>()
            fun onSportsChannel(s: SportSource) = !s.airing.isEventChannel && sportsChannel.getOrPut(s.stream.name) { classifier.channel(s.stream.name).isSport }
            fun broadcaster(s: SportSource) = s.playlist.id to broadcasterOf(s.stream.name)
            val marked = sources.filter { it.airing.kind == AiringKind.LIVE && onSportsChannel(it) }
                .distinctBy { Triple(it.playlist.id, it.airing.channelId, it.airing.startMs) }
                .groupingBy(::broadcaster).eachCount()
                .filterValues { it >= MARKS_LIVE }.keys
            if (marked.isEmpty()) return sources
            return sources.mapNotNull { s ->
                val shownAgain = s.airing.kind == AiringKind.EVENT && onSportsChannel(s) && broadcaster(s) in marked
                when {
                    !shownAgain -> s
                    replays -> s.copy(airing = s.airing.copy(kind = AiringKind.REPLAY))
                    else -> null
                }
            }
        }

        private val COUNTRY_PREFIX = Regex("^\\s*(?:[\\[(|]?\\s*[A-Za-z]{2,3}\\s*[\\])|:]\\s*)+")

        /** Who a channel belongs to, by the first word of its name: "TNT Sports 4 HD" and "UK: TNT Sports 1" are both "tnt". */
        fun broadcasterOf(name: String): String = SportNames.key(name.replace(COUNTRY_PREFIX, "")).substringBefore(' ')

        /**
         * [sources] gathered into events, soonest first: those that are the same event by name, or
         * by names someone has said are the same ([aliases]), starting close together; then those
         * whose names are nearly the same (another spelling, language or form of a name) on at the
         * same time ([nearlyTheSame]).
         */
        fun group(sources: List<SportSource>, follows: Follows, aliases: SportAliases = SportAliases.NONE): List<SportEvent> {
            val timed = sources.filter { it.airing.startMs != 0L }
            val timeless = sources.filter { it.airing.startMs == 0L }

            // The same event: the same sport, sides (or title and session) and kind, starting close together.
            val exact = ArrayList<MutableList<SportSource>>()
            for ((_, group) in timed.groupBy { identity(it.airing, aliases) }) {
                val sorted = group.sortedBy { it.airing.startMs }
                val window = if (sorted.first().airing.home != null) SAME_EVENT_MS else SAME_SHOW_MS
                var current: MutableList<SportSource>? = null
                for (s in sorted) {
                    val first = current?.first()?.airing?.startMs
                    if (current == null || first == null || s.airing.startMs - first > window) {
                        current = arrayListOf(s)
                        exact += current
                    } else {
                        current += s
                    }
                }
            }
            val clusters = joinNearlyTheSame(exact)
            // Event channels that don't say when join the event with the same sides that's soonest.
            for (s in timeless) {
                val id = identity(s.airing, aliases)
                clusters.filter { c -> c.any { identity(it.airing, aliases) == id } || nearlyTheSame(lead(c).airing, s.airing, timed = false) }
                    .minByOrNull { abs(it.first().airing.startMs - System.currentTimeMillis()) }
                    ?.add(s)
            }
            return clusters.map { event(it, follows, aliases) }.sortedWith(compareBy({ it.startMs }, { it.title }))
        }

        /**
         * [clusters] (each one event by name), with those that are nearly the same event put
         * together: each with all of the others, so a race and its qualifying aren't one through a
         * channel that doesn't say which it is.
         */
        private fun joinNearlyTheSame(clusters: List<MutableList<SportSource>>): MutableList<MutableList<SportSource>> {
            val order = clusters.indices.sortedBy { clusters[it].first().airing.startMs }
            val leads = clusters.map { lead(it).airing }
            val groupOf = IntArray(clusters.size) { it }
            val members = HashMap<Int, MutableList<Int>>().apply { for (i in clusters.indices) put(i, mutableListOf(i)) }
            for (x in order.indices) {
                for (y in x + 1 until order.size) {
                    val i = order[x]
                    val j = order[y]
                    if (clusters[j].first().airing.startMs - clusters[i].first().airing.startMs > NEAR_START_MS) break
                    val gi = groupOf[i]
                    val gj = groupOf[j]
                    if (gi == gj) continue
                    val a = members.getValue(gi)
                    val b = members.getValue(gj)
                    if (a.all { m -> b.all { n -> nearlyTheSame(leads[m], leads[n], timed = true) } }) {
                        a += b
                        for (n in b) groupOf[n] = gi
                        members.remove(gj)
                    }
                }
            }
            return order.map { groupOf[it] }.distinct().map { g ->
                members.getValue(g).flatMap { clusters[it] }.sortedBy { it.airing.startMs }.toMutableList()
            }.toMutableList()
        }

        /**
         * Whether [a] and [b], named differently, are nearly the same event: the same kind (live or
         * not), on at the same time ([timed]: overlapping), and either a match whose sides are
         * both the same ("Bills de Buffalo c. Vikings du Minnesota" and "Buffalo Bills at
         * Minnesota Vikings"), or, without sides, the same Formula 1 or MotoGP session (only one
         * is on at a time, whatever it's called) or an event of the same name.
         */
        private fun nearlyTheSame(a: SportAiringEntity, b: SportAiringEntity, timed: Boolean): Boolean {
            if (kindOf(a) != kindOf(b)) return false
            if (timed && (a.startMs >= b.stopMs || b.startMs >= a.stopMs)) return false
            val aHome = a.home
            val aAway = a.away
            val bHome = b.home
            val bAway = b.away
            if (aHome != null && aAway != null && bHome != null && bAway != null) {
                // A guide that doesn't know the sport ("Other sport") can still have the match.
                if (a.sport != b.sport && a.sport != Sport.OTHER && b.sport != Sport.OTHER) return false
                val sport = if (a.sport == Sport.OTHER) b.sport else a.sport
                return SameEvent.sameSides(aHome to aAway, bHome to bAway, sport)
            }
            if (aHome != null || bHome != null || a.sport != b.sport) return false
            val sessions = a.session == null || b.session == null || a.session == b.session
            return when (a.sport) {
                Sport.F1, Sport.MOTOGP -> timed && sessions
                Sport.OTHER -> false
                else -> sessions && SameEvent.sameTitle(a.title, b.title)
            }
        }

        private fun kindOf(a: SportAiringEntity): String = if (a.kind.isCoverage) "coverage" else a.kind.name

        /**
         * What [sources]' event is called by: a guide rather than a channel's name, which says more;
         * then the name most of them give it, with teams Tellycine knows first.
         */
        private fun lead(sources: List<SportSource>): SportSource {
            val timed = sources.filter { it.airing.startMs != 0L }.ifEmpty { sources }
            val guide = timed.filter { !it.airing.isEventChannel }.ifEmpty { timed }
            if (guide.size == 1) return guide[0]
            val counts = guide.groupingBy { it.airing.title }.eachCount()
            return guide.maxWith(
                compareBy<SportSource>(
                    { s -> listOfNotNull(s.airing.home, s.airing.away).count { SportCatalogue.find(it, s.airing.sport) != null } },
                    { s -> counts.getValue(s.airing.title) },
                    { s -> -s.airing.startMs },
                ),
            )
        }

        /**
         * What makes airings the same event, apart from when, after the names someone has said are
         * the same ([aliases]).
         */
        fun identity(a: SportAiringEntity, aliases: SportAliases = SportAliases.NONE): String {
            val kind = kindOf(a)
            val home = a.home
            val away = a.away
            val id = if (home != null && away != null) {
                "${a.sport}|$kind|" + listOf(home, away).map { aliases.side(a.sport, sideKey(it, a.sport)) }.sorted().joinToString("|")
            } else {
                "${a.sport}|$kind|${SportNames.key(a.title)}|${a.session.orEmpty()}"
            }
            return aliases.event(id)
        }

        /** A playlist's category, as the categories left out of the hub are kept. */
        fun categoryKey(playlistId: Long, categoryId: String?): String = "$playlistId:${categoryId.orEmpty()}"

        /** A side's name as events are told apart by: the one Tellycine knows it by, or the guide's. */
        fun sideKey(name: String, sport: Sport): String =
            (SportCatalogue.find(name, sport)?.name ?: name).let(SportNames::key)

        private fun event(sources: List<SportSource>, follows: Follows, aliases: SportAliases): SportEvent {
            val timed = sources.filter { it.airing.startMs != 0L }
            val a = lead(sources).airing
            val kinds = sources.map { it.airing.kind }
            val kind = AiringKind.entries.first { it in kinds }
            val followed = sources.flatMap { s ->
                follows.matchAll(s.airing.sport, s.airing.home, s.airing.away, s.airing.title + " " + s.airing.detail.orEmpty())
            }.distinct()
            val start = timed.minOf { it.airing.startMs }
            val stop = timed.maxOf { it.airing.stopMs }
            return SportEvent(
                key = "${identity(a, aliases)}|$start",
                sport = a.sport,
                kind = kind,
                title = a.title,
                competition = sources.firstNotNullOfOrNull { it.airing.competition },
                session = a.session ?: sources.firstNotNullOfOrNull { it.airing.session },
                home = a.home?.let { side(it, a.sport) },
                away = a.away?.let { side(it, a.sport) },
                startMs = start,
                stopMs = stop,
                // Those on first, then by start, then the playlists in the order they were added.
                sources = sources.distinctBy { it.playlist.id to it.stream.streamId }.sortedWith(
                    compareBy<SportSource>({ it.airing.startMs.takeIf { s -> s != 0L } ?: start }, { it.playlist.createdAt }, { it.stream.sortOrder })
                ),
                followed = followed,
                league = leagueOf(sources, a.sport),
            )
        }

        /**
         * The competition [sources]' event is in: what the guide says it is, or what a programme's
         * title or an event channel's name does ("EPL 03: Arsenal v Chelsea").
         */
        private fun leagueOf(sources: List<SportSource>, sport: Sport): Competition? =
            sources.firstNotNullOfOrNull { s -> s.airing.competition?.let { Competitions.of(it, sport) } }
                ?: sources.firstNotNullOfOrNull { s -> Competitions.find(s.airing.programmeTitle, sport) }

        private fun side(name: String, sport: Sport): SportSide {
            val figure = SportCatalogue.find(name, sport)
            return SportSide(name, figure?.code ?: SportNames.monogram(name), figure?.colour)
        }
    }
}
