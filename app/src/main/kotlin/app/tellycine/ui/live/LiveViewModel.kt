package app.tellycine.ui.live

import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import app.tellycine.AppContainer
import app.tellycine.core.ContentKind
import app.tellycine.core.dispatcharr.DispatcharrSource
import app.tellycine.core.dispatcharr.DvrRecording
import app.tellycine.data.db.CategoryEntity
import app.tellycine.data.db.ChannelRow
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.ProgrammeEntity
import app.tellycine.data.repo.Categories
import app.tellycine.data.repo.CatchupResolver
import app.tellycine.data.repo.PlaylistFilter
import app.tellycine.data.repo.PlaylistScope
import app.tellycine.data.settings.CategorySort
import app.tellycine.data.sync.PlaylistSyncer
import app.tellycine.player.PlayRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScheduleState(
    val channel: ChannelRow,
    val programmes: List<ProgrammeEntity>? = null,
    /** The channel has an archive, so past programmes can be replayed. */
    val catchup: Boolean = false,
)

private const val DAY = 24 * 60 * 60 * 1000L

@OptIn(ExperimentalCoroutinesApi::class)
class LiveViewModel(private val container: AppContainer) : ViewModel() {
    private val content = container.content

    /** Every playlist, for the details of whichever one a channel is from. */
    private val allPlaylists: StateFlow<List<PlaylistEntity>> = container.playlists.playlists
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The playlists Live TV shows: the one chosen, or all of them together. */
    val scope: StateFlow<PlaylistScope> = container.playlists.scope(ContentKind.LIVE)
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaylistScope.EMPTY)
    private val scopeIds = scope.map { it.ids }.distinctUntilChanged()

    /** "All playlists" and each playlist, to choose from; null with only one playlist. */
    val playlistFilter: StateFlow<PlaylistFilter?> = combine(allPlaylists, scope) { all, s -> PlaylistFilter.of(all, s) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Whether there's more than one playlist, so details say which one a channel is from. */
    val showSource: StateFlow<Boolean> = allPlaylists.map { it.size > 1 }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun selectPlaylists(id: Long) {
        viewModelScope.launch { container.playlists.setSectionPlaylists(ContentKind.LIVE, id) }
    }

    /** The playlist [row] is from. */
    fun playlistOf(row: ChannelRow): PlaylistEntity? = allPlaylists.value.firstOrNull { it.id == row.stream.playlistId }

    private val _selected = MutableStateFlow(Categories.ALL)
    val selectedCategory: StateFlow<String> = _selected.asStateFlow()

    /** The order categories are listed in: the provider's, or A–Z. */
    val categorySort: StateFlow<CategorySort> = container.settings.settings.map { it.liveCategorySort }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, CategorySort.PROVIDER)

    fun setCategorySort(sort: CategorySort) {
        viewModelScope.launch { container.settings.setLiveCategorySort(sort) }
    }

    /** The categories of the playlists [ids] (merged when there are several), in [categorySort]'s order. */
    fun categoriesOf(ids: List<Long>): Flow<List<CategoryEntity>> =
        combine(content.categories(ids, ContentKind.LIVE), categorySort) { list, sort -> Categories.sorted(list, sort) }

    val categories: StateFlow<List<CategoryEntity>> = scopeIds
        .flatMapLatest { ids -> categoriesOf(ids) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @Volatile
    private var currentSource: PagingSource<*, *>? = null

    val channels: Flow<PagingData<ChannelRow>> = combine(scopeIds, _selected) { ids, cat -> ids to cat }
        .flatMapLatest { (ids, cat) ->
            content.channels(ids, cat, now = { System.currentTimeMillis() }, onSource = { currentSource = it })
        }
        .cachedIn(viewModelScope)

    private val _schedule = MutableStateFlow<ScheduleState?>(null)
    val schedule: StateFlow<ScheduleState?> = _schedule.asStateFlow()

    init {
        // Reset the category when switching playlists.
        viewModelScope.launch {
            var last: List<Long>? = null
            scopeIds.collect { ids ->
                if (last != null && ids != last) _selected.value = Categories.ALL
                last = ids
            }
        }
        // Refresh "now playing" as programmes change.
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                delay(60_000 - now % 60_000 + 500)
                currentSource?.invalidate()
            }
        }
    }

    fun selectCategory(id: String) {
        _selected.value = id
    }

    fun toggleFavorite(row: ChannelRow) {
        viewModelScope.launch {
            content.toggleFavorite(row.stream.playlistId, ContentKind.LIVE, row.stream.streamId)
        }
    }

    fun showSchedule(row: ChannelRow) {
        refreshRecordings(row)
        val catchup = CatchupResolver.supports(playlistOf(row), row.stream)
        _schedule.value = ScheduleState(row, catchup = catchup)
        val channelId = row.stream.epgChannelId ?: run {
            _schedule.value = ScheduleState(row, emptyList(), catchup)
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val days = if (catchup) (row.stream.catchupDays ?: 0).coerceAtMost(PlaylistSyncer.MAX_CATCHUP_GUIDE_DAYS) else 0
            val from = now - days * DAY
            val programmes = content.schedule(row.stream.playlistId, channelId, from, limit = if (catchup) 2000 else 300)
            if (_schedule.value?.channel?.stream?.streamId == row.stream.streamId) {
                _schedule.value = ScheduleState(row, programmes, catchup)
            }
        }
    }

    // ---- Grid guide ----

    /** Whether the user chose the grid guide for landscape. */
    val guideGrid: StateFlow<Boolean> = container.settings.settings.map { it.liveGuideGrid }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Days of upcoming guide kept, which is how far the grid scrolls. */
    val guideDays: StateFlow<Int> = container.settings.settings.map { it.epgDays }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 3)

    /** Changes whenever a guide is re-imported, so the grid reloads its rows. */
    val guideVersion: StateFlow<List<Long?>> = allPlaylists.map { list -> list.map { it.epgSyncedAt } }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setGuideGrid(enabled: Boolean) {
        viewModelScope.launch { container.settings.setLiveGuideGrid(enabled) }
    }

    /** TV: whether the guide uses its compact rows. */
    val tvGuideCompact: StateFlow<Boolean> = container.settings.settings.map { it.tvGuideCompact }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setTvGuideCompact(enabled: Boolean) {
        viewModelScope.launch { container.settings.setTvGuideCompact(enabled) }
    }

    /** Recently shown grid rows, so scrolling back up doesn't query the database again. */
    private val guideCache = LruCache<String, List<ProgrammeEntity>>(300)

    /** A channel's programmes between [fromMs] and [toMs], for one row of the grid. */
    suspend fun guideProgrammes(row: ChannelRow, fromMs: Long, toMs: Long): List<ProgrammeEntity> {
        val p = playlistOf(row) ?: return emptyList()
        val channelId = row.stream.epgChannelId ?: return emptyList()
        val key = "${p.id}:${p.epgSyncedAt}:$channelId:$fromMs:$toMs"
        guideCache.get(key)?.let { return it }
        return content.programmesBetween(p.id, channelId, fromMs, toMs).also { guideCache.put(key, it) }
    }

    // ---- Dispatcharr DVR ----

    /** Each playlist's recordings, by its id, so the guide can show what is already scheduled. */
    val recordings: StateFlow<Map<Long, List<DvrRecording>>> = container.dvr.state
        .map { state -> state.mapValues { (_, dvr) -> dvr.recordings } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** One-off messages for the UI (recording scheduled, errors…). */
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun canRecord(row: ChannelRow): Boolean = playlistOf(row)?.hasDvr == true && row.stream.streamId.toLongOrNull() != null

    fun recordingFor(row: ChannelRow, programme: ProgrammeEntity, recordings: Map<Long, List<DvrRecording>>): DvrRecording? {
        val channelId = row.stream.streamId.toLongOrNull() ?: return null
        return recordings[row.stream.playlistId].orEmpty().firstOrNull {
            it.channelId == channelId && it.startMs < programme.stopMs && it.endMs > programme.startMs &&
                minOf(it.endMs, programme.stopMs) - maxOf(it.startMs, programme.startMs) >= (programme.stopMs - programme.startMs) / 2
        }
    }

    /** Schedules [programme], or cancels it if it is already scheduled. */
    fun toggleRecording(row: ChannelRow, programme: ProgrammeEntity) {
        val p = playlistOf(row) ?: return
        val channelId = row.stream.streamId.toLongOrNull() ?: return
        val existing = recordingFor(row, programme, recordings.value)
        viewModelScope.launch {
            try {
                if (existing != null && existing.state(System.currentTimeMillis()) == DvrRecording.State.SCHEDULED) {
                    container.dvr.delete(p, existing.id)
                    _events.emit("Recording cancelled")
                } else if (existing == null) {
                    container.dvr.schedule(p, channelId, programme)
                    _events.emit("Recording “${programme.title}”")
                } else {
                    _events.emit("Manage this recording from Library › Recordings")
                }
            } catch (e: Exception) {
                _events.emit(e.message ?: "Couldn't schedule the recording")
            }
        }
    }

    private fun refreshRecordings(row: ChannelRow) {
        val p = playlistOf(row)?.takeIf { it.hasDvr } ?: return
        val loadedAt = container.dvr.state.value[p.id]?.loadedAt ?: 0
        if (System.currentTimeMillis() - loadedAt > 60_000) viewModelScope.launch { container.dvr.refresh(p) }
    }

    // ---- Share / external player ----

    /** True for channels of Dispatcharr playlists, which offer both the Dispatcharr and the upstream source URL. */
    fun isDispatcharr(row: ChannelRow) = playlistOf(row)?.isDispatcharr == true

    private val _sources = MutableStateFlow<List<DispatcharrSource>?>(null)
    /** Upstream sources to pick from when a Dispatcharr channel has several. */
    val sources: StateFlow<List<DispatcharrSource>?> = _sources.asStateFlow()

    private val _shareRequests = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 2)
    /** (label, url) pairs the UI should hand to the share sheet. */
    val shareRequests: SharedFlow<Pair<String, String>> = _shareRequests.asSharedFlow()

    /** Looks up the provider stream(s) behind a Dispatcharr channel and shares one. */
    fun shareSourceUrl(row: ChannelRow) {
        val p = playlistOf(row) ?: return
        val channelId = row.stream.streamId.toLongOrNull() ?: return
        if (!p.hasDvr) {
            _events.tryEmit("Connect your Dispatcharr login in the playlist settings to share source URLs.")
            return
        }
        viewModelScope.launch {
            try {
                val list = container.dvr.channelSources(p, channelId)
                when (list.size) {
                    0 -> _events.emit("This channel has no source streams with a URL.")
                    1 -> _shareRequests.emit("${row.stream.name} (source)" to list[0].url)
                    else -> _sources.value = list
                }
            } catch (e: Exception) {
                _events.emit(e.message ?: "Couldn't load the channel's sources")
            }
        }
    }

    fun pickSource(source: DispatcharrSource?) {
        _sources.value = null
        if (source != null) _shareRequests.tryEmit("${source.name} (source)" to source.url)
    }

    fun canReplay(row: ChannelRow, programme: ProgrammeEntity): Boolean =
        CatchupResolver.isReplayable(playlistOf(row), row.stream, programme)

    /** Play request for a past (or in-progress, from the start) programme, or null if unavailable. */
    fun replayRequest(row: ChannelRow, programme: ProgrammeEntity): PlayRequest? =
        replayRequest(playlistOf(row), row.stream, programme)

    fun dismissSchedule() {
        _schedule.value = null
    }

    fun hasCatchup(row: ChannelRow) = CatchupResolver.supports(playlistOf(row), row.stream)

    /**
     * Plays [row], noting the category it was picked from ([categoryId], the one selected unless
     * given) for changing channel in the player. The player stays in the channel's own playlist, so
     * a merged category becomes the channel's own.
     */
    fun playRequest(row: ChannelRow, categoryId: String = _selected.value): PlayRequest {
        val category = categoryId.takeIf { Categories.nameKeyOf(it) == null } ?: row.stream.categoryId
        return liveRequest(playlistOf(row), row.stream, subtitle = row.nowTitle, categoryId = category)
    }

    /**
     * Where the guide over the player opens on [request]'s channel, among the playlists [ids]: its
     * category as listed for them (merged by name for several playlists), and its row there. When
     * the channel isn't in those playlists, all their channels from the top.
     */
    suspend fun guideStart(request: PlayRequest, ids: List<Long>): GuideStart {
        if (request.playlistId !in ids) return GuideStart(Categories.ALL, null)
        val own = request.categoryId ?: Categories.ALL
        val category = if (ids.size > 1 && own != Categories.ALL && own != Categories.FAVORITES) {
            content.category(request.playlistId, ContentKind.LIVE, own)?.let { Categories.named(Categories.nameKey(it.name)) } ?: Categories.ALL
        } else {
            own
        }
        val stream = content.getStream(request.playlistId, ContentKind.LIVE, request.streamId)
        val row = stream?.let { content.channelPosition(ids, category, it) }
        return GuideStart(category, row)
    }
}

/** Where a guide opens: a [category], and the [row] of the channel to start on (null for the top). */
data class GuideStart(val category: String, val row: Int?)
