package app.tellycine.data.sports

import app.tellycine.core.sports.AiringKind
import app.tellycine.core.sports.Follows
import app.tellycine.core.sports.Sport
import app.tellycine.core.sports.SportCatalogue
import app.tellycine.core.sports.SportNames
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.SportAiringEntity
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
    fun events(fromMs: Long, toMs: Long, replays: Boolean, eventChannels: Boolean, teams: List<String>): Flow<List<SportEvent>> {
        val kinds = if (replays) listOf(AiringKind.LIVE, AiringKind.EVENT, AiringKind.HIGHLIGHTS, AiringKind.REPLAY) else listOf(AiringKind.LIVE, AiringKind.EVENT)
        val follows = Follows(teams)
        return combine(dao.observeBetween(fromMs, toMs, Sport.entries, kinds), db.playlists().observeAll()) { airings, playlists -> airings to playlists }
            .mapLatest { (airings, playlists) -> build(airings.filter { eventChannels || !it.isEventChannel }, playlists, follows) }
            .flowOn(Dispatchers.IO)
    }

    /** Teams and players in the events found, for suggestions while choosing what to follow. */
    suspend fun sideNames(): List<String> = dao.sideNames()

    private suspend fun build(airings: List<SportAiringEntity>, playlists: List<PlaylistEntity>, follows: Follows): List<SportEvent> {
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
                for (s in streams) sources += SportSource(playlist, s, a)
            }
        }
        return group(sources, follows)
    }

    companion object {
        private const val MAX_VARIABLES = 900

        /** How far apart the same event can start on different channels: build-up starts earlier on some. */
        private const val SAME_EVENT_MS = 4 * 60 * 60 * 1000L
        private const val SAME_SHOW_MS = 60 * 60 * 1000L

        /** [sources] gathered into events, soonest first. */
        fun group(sources: List<SportSource>, follows: Follows): List<SportEvent> {
            val timed = sources.filter { it.airing.startMs != 0L }
            val timeless = sources.filter { it.airing.startMs == 0L }

            // The same event: the same sport, sides (or title and session) and kind, starting close together.
            val clusters = ArrayList<MutableList<SportSource>>()
            for ((_, group) in timed.groupBy { identity(it.airing) }) {
                val sorted = group.sortedBy { it.airing.startMs }
                val window = if (sorted.first().airing.home != null) SAME_EVENT_MS else SAME_SHOW_MS
                var current: MutableList<SportSource>? = null
                for (s in sorted) {
                    val first = current?.first()?.airing?.startMs
                    if (current == null || first == null || s.airing.startMs - first > window) {
                        current = arrayListOf(s)
                        clusters += current
                    } else {
                        current += s
                    }
                }
            }
            // Event channels that don't say when join the event with the same sides that's soonest.
            for (s in timeless) {
                val id = identity(s.airing)
                clusters.filter { identity(it.first().airing) == id }
                    .minByOrNull { abs(it.first().airing.startMs - System.currentTimeMillis()) }
                    ?.add(s)
            }
            return clusters.map { event(it, follows) }.sortedWith(compareBy({ it.startMs }, { it.title }))
        }

        /** What makes airings the same event, apart from when. */
        private fun identity(a: SportAiringEntity): String {
            val kind = if (a.kind.isCoverage) "coverage" else a.kind.name
            val home = a.home
            val away = a.away
            return if (home != null && away != null) {
                "${a.sport}|$kind|" + listOf(sideKey(home, a.sport), sideKey(away, a.sport)).sorted().joinToString("|")
            } else {
                "${a.sport}|$kind|${SportNames.key(a.title)}|${a.session.orEmpty()}"
            }
        }

        private fun sideKey(name: String, sport: Sport): String =
            (SportCatalogue.find(name, sport)?.name ?: name).let(SportNames::key)

        private fun event(sources: List<SportSource>, follows: Follows): SportEvent {
            val timed = sources.filter { it.airing.startMs != 0L }
            // The guide says more than a channel's name does.
            val lead = timed.firstOrNull { !it.airing.isEventChannel } ?: timed.first()
            val a = lead.airing
            val kinds = sources.map { it.airing.kind }
            val kind = AiringKind.entries.first { it in kinds }
            val followed = sources.flatMap { s ->
                follows.matchAll(s.airing.sport, s.airing.home, s.airing.away, s.airing.title + " " + s.airing.detail.orEmpty())
            }.distinct()
            val start = timed.minOf { it.airing.startMs }
            val stop = timed.maxOf { it.airing.stopMs }
            return SportEvent(
                key = "${identity(a)}|$start",
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
            )
        }

        private fun side(name: String, sport: Sport): SportSide {
            val figure = SportCatalogue.find(name, sport)
            return SportSide(name, figure?.code ?: SportNames.monogram(name), figure?.colour)
        }
    }
}
