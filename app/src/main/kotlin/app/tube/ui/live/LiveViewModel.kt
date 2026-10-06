package app.tube.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import app.tube.AppContainer
import app.tube.core.ContentKind
import app.tube.data.db.CategoryEntity
import app.tube.data.db.ChannelRow
import app.tube.data.db.PlaylistEntity
import app.tube.data.db.ProgrammeEntity
import app.tube.data.repo.Categories
import app.tube.data.repo.ContentRepository
import app.tube.player.PlayRequest
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScheduleState(
    val channel: ChannelRow,
    val programmes: List<ProgrammeEntity>? = null,
)

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
        _schedule.value = ScheduleState(row)
        val channelId = row.stream.epgChannelId ?: run {
            _schedule.value = ScheduleState(row, emptyList())
            return
        }
        viewModelScope.launch {
            val programmes = content.schedule(row.stream.playlistId, channelId)
            if (_schedule.value?.channel?.stream?.streamId == row.stream.streamId) {
                _schedule.value = ScheduleState(row, programmes)
            }
        }
    }

    fun dismissSchedule() {
        _schedule.value = null
    }

    fun playRequest(row: ChannelRow): PlayRequest {
        val s = row.stream
        return PlayRequest(
            playlistId = s.playlistId,
            kind = ContentKind.LIVE,
            streamId = s.streamId,
            historyKey = ContentRepository.liveKey(s.streamId),
            url = s.url,
            title = s.name,
            subtitle = row.nowTitle,
            artwork = s.logo,
            userAgent = s.userAgent ?: playlist.value?.userAgent,
            referrer = s.referrer,
            categoryId = _selected.value,
        )
    }
}
