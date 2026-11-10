package app.tellycine.ui.sports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tellycine.AppContainer
import app.tellycine.core.sports.Sport
import app.tellycine.core.sports.SportCatalogue
import app.tellycine.core.sports.SportNames
import app.tellycine.data.db.SportsStatus
import app.tellycine.data.repo.CatchupResolver
import app.tellycine.data.settings.AppSettings
import app.tellycine.data.sports.SportEvent
import app.tellycine.data.sports.SportSide
import app.tellycine.data.sports.SportSource
import app.tellycine.player.PlayRequest
import app.tellycine.ui.live.liveRequest
import app.tellycine.ui.live.replayRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Which events the hub shows: those of the sports and teams followed, all of them, or one sport's. */
sealed interface SportsFilter {
    data object ForYou : SportsFilter
    data object All : SportsFilter
    data class Only(val sport: Sport) : SportsFilter

    val label: String
        get() = when (this) {
            ForYou -> "For you"
            All -> "All"
            is Only -> sport.label
        }
}

/** A team or player followed, and their next event, if one is in the guides. */
data class TeamCard(val name: String, val side: SportSide, val sport: Sport?, val next: SportEvent?)

/** A day's events, from the start of that day. */
data class SportDay(val dayStartMs: Long, val events: List<SportEvent>)

data class SportsHubState(
    val loaded: Boolean = false,
    val filters: List<SportsFilter> = listOf(SportsFilter.ForYou, SportsFilter.All),
    val filter: SportsFilter = SportsFilter.ForYou,
    val live: List<SportEvent> = emptyList(),
    val teams: List<TeamCard> = emptyList(),
    val days: List<SportDay> = emptyList(),
    /** How many events are live now, whatever the filter. */
    val liveCount: Int = 0,
    /** Whether any sport was found at all. */
    val anyEvents: Boolean = false,
    /** Whether the filter shows anything. */
    val anyShown: Boolean = false,
    val multiplePlaylists: Boolean = false,
    val hasPlaylists: Boolean = false,
    /** Whether to ask "What do you follow?". */
    val ask: Boolean = false,
    val now: Long = 0,
)

/** The Sports Hub, on phones and on TV. */
@OptIn(ExperimentalCoroutinesApi::class)
class SportsViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** The time, a minute at a time, for what's on now. */
    private val clock: Flow<Long> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(now)
            delay(MINUTE - now % MINUTE)
        }
    }

    /** Where the events are read from: moves on every quarter of an hour. */
    private val windowStart: Flow<Long> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(now / WINDOW_STEP * WINDOW_STEP)
            delay(WINDOW_STEP - now % WINDOW_STEP)
        }
    }

    private data class Query(val replays: Boolean, val eventChannels: Boolean, val teams: List<String>, val days: Int)

    private val events: StateFlow<List<SportEvent>?> = combine(
        windowStart,
        settings.map { Query(it.sportsReplays, it.sportsEventChannels, it.sportsTeams, it.epgDays) }.distinctUntilChanged(),
    ) { start, q -> start to q }
        .flatMapLatest { (start, q) -> container.sports.events(start - LIVE_FOR, start + (q.days + 1) * DAY, q.replays, q.eventChannels, q.teams) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _filter = MutableStateFlow<SportsFilter>(SportsFilter.ForYou)

    /** Whether sport is being looked for in the guides right now. */
    val indexing: StateFlow<Boolean> = container.sportsIndexer.working

    /** What was found in each playlist, and when. */
    val status: StateFlow<List<SportsStatus>> = container.sports.status()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<SportsHubState> = combine(events, settings, _filter, clock, container.playlists.playlists) { events, prefs, filter, now, playlists ->
        hubState(events, prefs, filter, now, playlists.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SportsHubState())

    fun select(filter: SportsFilter) {
        _filter.value = filter
    }

    private fun hubState(events: List<SportEvent>?, prefs: AppSettings, chosen: SportsFilter, now: Long, playlists: Int): SportsHubState {
        val filters = listOf(SportsFilter.ForYou, SportsFilter.All) + Sport.FOLLOWABLE.filter { it in prefs.sports }.map { SportsFilter.Only(it) }
        val filter = chosen.takeIf { it in filters } ?: SportsFilter.ForYou
        val all = events.orEmpty().filter { it.stopMs > now }
        val shown = all.filter { e ->
            when (filter) {
                SportsFilter.ForYou -> e.sport in prefs.sports || e.isFollowed
                SportsFilter.All -> true
                is SportsFilter.Only -> e.sport == filter.sport
            }
        }
        val live = shown.filter { it.isLive(now) }.sortedWith(compareBy({ !it.isFollowed }, { Sport.entries.indexOf(it.sport) }, { it.startMs }))
        val upcoming = shown.filter { !it.isLive(now) && it.startMs > now }
        val zone = ZoneId.systemDefault()
        val days = upcoming.groupBy { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() }
            .map { (day, list) -> SportDay(day.atStartOfDay(zone).toInstant().toEpochMilli(), list) }
            .sortedBy { it.dayStartMs }
        val teams = if (filter == SportsFilter.All) emptyList() else prefs.sportsTeams.mapNotNull { name ->
            val figure = SportCatalogue.find(name)
            if (filter is SportsFilter.Only && figure != null && figure.sport != filter.sport) return@mapNotNull null
            val next = all.firstOrNull { name in it.followed && (it.isLive(now) || it.startMs > now) }
            if (filter is SportsFilter.Only && figure == null && next?.sport != filter.sport) return@mapNotNull null
            TeamCard(
                name = figure?.name ?: name,
                side = SportSide(figure?.name ?: name, figure?.code ?: SportNames.monogram(name), figure?.colour),
                sport = figure?.sport ?: next?.sport,
                next = next,
            )
        }
        return SportsHubState(
            loaded = events != null,
            filters = filters,
            filter = filter,
            live = live,
            teams = teams,
            days = days,
            liveCount = all.count { it.isLive(now) },
            anyEvents = all.isNotEmpty(),
            anyShown = live.isNotEmpty() || days.isNotEmpty(),
            multiplePlaylists = playlists > 1,
            hasPlaylists = playlists > 0,
            ask = !prefs.sportsAsked,
            now = now,
        )
    }

    // ---- An event ----

    private val _open = MutableStateFlow<String?>(null)

    /** The event shown in full (the sheet, or the TV's panel), kept up to date as the guide changes. */
    val opened: StateFlow<SportEvent?> = combine(_open, events) { key, list -> key?.let { k -> list?.firstOrNull { it.key == k } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun open(event: SportEvent) {
        _open.value = event.key
    }

    fun close() {
        _open.value = null
    }

    /** Plays [source]'s channel live. */
    fun playRequest(source: SportSource, now: Long = System.currentTimeMillis()): PlayRequest =
        liveRequest(source.playlist, source.stream, subtitle = source.airing.programmeTitle.takeIf { source.isOn(now) && !source.airing.isEventChannel }, categoryId = source.stream.categoryId)

    /** Whether [source]'s programme can be watched from its start, through catch-up. */
    fun canWatchFromStart(source: SportSource, now: Long = System.currentTimeMillis()): Boolean =
        !source.airing.isEventChannel && source.airing.startMs <= now &&
            CatchupResolver.isReplayable(source.playlist, source.stream, source.airing.programme(), now)

    fun fromStartRequest(source: SportSource): PlayRequest? = replayRequest(source.playlist, source.stream, source.airing.programme())

    /** What to play for [event] at once: its first channel on now. */
    fun quickPlay(event: SportEvent, now: Long = System.currentTimeMillis()): PlayRequest? =
        event.sources.firstOrNull { it.isOn(now) }?.let { playRequest(it, now) }

    // ---- What's followed ----

    fun follow(name: String) {
        viewModelScope.launch { container.settings.followTeam(name) }
    }

    fun unfollow(name: String) {
        viewModelScope.launch { container.settings.unfollowTeam(name) }
    }

    fun setSport(sport: Sport, followed: Boolean) {
        viewModelScope.launch { container.settings.setSportFollowed(sport, followed) }
    }

    /** "What do you follow?" has been answered or put off. */
    fun asked() {
        viewModelScope.launch { container.settings.setSportsAsked() }
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun search(text: String) {
        _query.value = text
    }

    /** Teams and players to follow for what's typed: those Tellycine knows, and those in the guides. */
    val suggestions: StateFlow<List<String>> = combine(_query, settings.map { it.sportsTeams }.distinctUntilChanged()) { q, teams -> q to teams }
        .map { (q, teams) ->
            val key = SportNames.key(q)
            if (key.isEmpty()) return@map emptyList()
            val known = SportCatalogue.suggest(q, limit = 8).map { it.name }
            val seen = container.sports.sideNames().filter { name -> SportNames.key(name).let { it.startsWith(key) || it.contains(" $key") } }
                .filter { SportCatalogue.find(it) == null }.sortedBy { it.length }
            val followed = teams.map(SportNames::key).toSet()
            (known + seen).distinctBy(SportNames::key).filter { SportNames.key(it) !in followed }.take(8)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Settings ----

    fun setHub(enabled: Boolean) {
        viewModelScope.launch { container.settings.setSportsHub(enabled) }
    }

    fun setReplays(enabled: Boolean) {
        viewModelScope.launch { container.settings.setSportsReplays(enabled) }
    }

    /** Looks through every playlist's guide again. */
    fun lookAgain() = container.sportsIndexer.refreshAll()

    fun setEventChannels(enabled: Boolean) {
        viewModelScope.launch { container.settings.setSportsEventChannels(enabled) }
    }

    companion object {
        private const val MINUTE = 60_000L
        private const val DAY = 24 * 60 * MINUTE
        private const val WINDOW_STEP = 15 * MINUTE

        /** How long ago an event can have started and still be on. */
        private const val LIVE_FOR = 8 * 60 * MINUTE
    }
}
