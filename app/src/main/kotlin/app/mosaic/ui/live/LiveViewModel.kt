package app.mosaic.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import app.mosaic.AppContainer
import app.mosaic.core.ContentKind
import app.mosaic.core.dispatcharr.DispatcharrSource
import app.mosaic.core.dispatcharr.DvrRecording
import app.mosaic.data.db.CategoryEntity
import app.mosaic.data.db.ChannelRow
import app.mosaic.data.db.PlaylistEntity
import app.mosaic.data.db.ProgrammeEntity
import app.mosaic.data.repo.Categories
import app.mosaic.data.repo.CatchupResolver
import app.mosaic.data.sync.PlaylistSyncer
import app.mosaic.player.PlayRequest
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
import kotlinx.coroutines.flow.flowOf
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

    private val playlist: StateFlow<PlaylistEntity?> = container.playlists.activePlaylist
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val playlistId = playlist.map { it?.id }.distinctUntilChanged()

    private val _selected = MutableStateFlow(Categories.ALL)
    val selectedCategory: StateFlow<String> = _selected.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = playlistId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else content.categories(id, ContentKind.LIVE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @Volatile
    private var currentSource: PagingSource<*, *>? = null

    val channels: Flow<PagingData<ChannelRow>> = combine(playlistId, _selected) { id, cat -> id to cat }
        .flatMapLatest { (id, cat) ->
            if (id == null) flowOf(PagingData.empty())
            else content.channels(id, cat, now = { System.currentTimeMillis() }, onSource = { currentSource = it })
        }
        .cachedIn(viewModelScope)

    private val _schedule = MutableStateFlow<ScheduleState?>(null)
    val schedule: StateFlow<ScheduleState?> = _schedule.asStateFlow()

    init {
        // Reset the category when switching playlists.
        viewModelScope.launch {
            var last: Long? = null
            playlistId.collect { id ->
                if (last != null && id != last) _selected.value = Categories.ALL
                last = id
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
        refreshRecordings()
        val catchup = CatchupResolver.supports(playlist.value, row.stream)
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

    // ---- Dispatcharr DVR ----

    /** Recordings of the active playlist, so the guide can show what is already scheduled. */
    val recordings: StateFlow<List<DvrRecording>> = combine(playlist, container.dvr.state) { p, state ->
        p?.let { state[it.id]?.recordings }.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** One-off messages for the UI (recording scheduled, errors…). */
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun canRecord(row: ChannelRow): Boolean = playlist.value?.hasDvr == true && row.stream.streamId.toLongOrNull() != null

    fun recordingFor(row: ChannelRow, programme: ProgrammeEntity, recordings: List<DvrRecording>): DvrRecording? {
        val channelId = row.stream.streamId.toLongOrNull() ?: return null
        return recordings.firstOrNull {
            it.channelId == channelId && it.startMs < programme.stopMs && it.endMs > programme.startMs &&
                minOf(it.endMs, programme.stopMs) - maxOf(it.startMs, programme.startMs) >= (programme.stopMs - programme.startMs) / 2
        }
    }

    /** Schedules [programme], or cancels it if it is already scheduled. */
    fun toggleRecording(row: ChannelRow, programme: ProgrammeEntity) {
        val p = playlist.value ?: return
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

    private fun refreshRecordings() {
        val p = playlist.value?.takeIf { it.hasDvr } ?: return
        val loadedAt = container.dvr.state.value[p.id]?.loadedAt ?: 0
        if (System.currentTimeMillis() - loadedAt > 60_000) viewModelScope.launch { container.dvr.refresh(p) }
    }

    // ---- Share / external player ----

    /** True for Dispatcharr playlists, which offer both the Dispatcharr and the upstream source URL. */
    fun isDispatcharr() = playlist.value?.isDispatcharr == true

    private val _sources = MutableStateFlow<List<DispatcharrSource>?>(null)
    /** Upstream sources to pick from when a Dispatcharr channel has several. */
    val sources: StateFlow<List<DispatcharrSource>?> = _sources.asStateFlow()

    private val _shareRequests = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 2)
    /** (label, url) pairs the UI should hand to the share sheet. */
    val shareRequests: SharedFlow<Pair<String, String>> = _shareRequests.asSharedFlow()

    /** Looks up the provider stream(s) behind a Dispatcharr channel and shares one. */
    fun shareSourceUrl(row: ChannelRow) {
        val p = playlist.value ?: return
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
        CatchupResolver.isReplayable(playlist.value, row.stream, programme)

    /** Play request for a past (or in-progress, from the start) programme, or null if unavailable. */
    fun replayRequest(row: ChannelRow, programme: ProgrammeEntity): PlayRequest? =
        replayRequest(playlist.value, row.stream, programme)

    fun dismissSchedule() {
        _schedule.value = null
    }

    fun hasCatchup(row: ChannelRow) = CatchupResolver.supports(playlist.value, row.stream)

    fun playRequest(row: ChannelRow): PlayRequest =
        liveRequest(playlist.value, row.stream, subtitle = row.nowTitle, categoryId = _selected.value)
}
