package app.mosaic.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.mosaic.AppContainer
import app.mosaic.core.ContentKind
import app.mosaic.data.db.PlaylistEntity
import app.mosaic.data.db.StreamEntity
import app.mosaic.data.repo.CatchupResolver
import app.mosaic.data.repo.ProgrammeHit
import app.mosaic.player.PlayRequest
import app.mosaic.player.play
import app.mosaic.ui.components.Artwork
import app.mosaic.ui.components.EmptyState
import app.mosaic.ui.components.SectionHeader
import app.mosaic.ui.components.formatDay
import app.mosaic.ui.components.formatTime
import app.mosaic.ui.live.liveRequest
import app.mosaic.ui.live.replayRequest
import app.mosaic.ui.components.mosaicViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** What the search is narrowed to; [ALL] shows a few of each. */
enum class SearchFilter(val label: String, val needsVod: Boolean = false) {
    ALL("All"),
    LIVE("Live TV"),
    GUIDE("TV guide"),
    MOVIES("Movies", needsVod = true),
    SERIES("Series", needsVod = true),
}

data class SearchResults(
    val query: String = "",
    val searching: Boolean = false,
    val live: List<StreamEntity> = emptyList(),
    val programmes: List<ProgrammeHit> = emptyList(),
    val movies: List<StreamEntity> = emptyList(),
    val series: List<StreamEntity> = emptyList(),
) {
    val isEmpty get() = live.isEmpty() && programmes.isEmpty() && movies.isEmpty() && series.isEmpty()
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(container: AppContainer) : ViewModel() {
    private val content = container.content
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter.ALL)
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    val playlist: StateFlow<PlaylistEntity?> = container.playlists.activePlaylist
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val results: StateFlow<SearchResults> = combine(_query.debounce(250), playlist, _filter) { q, p, f -> Triple(q.trim(), p, f) }
        .flatMapLatest { (q, p, f) ->
            flow {
                if (p == null || q.length < 2) {
                    emit(SearchResults(q))
                    return@flow
                }
                emit(SearchResults(query = q, searching = true))
                val limit = if (f == SearchFilter.ALL) 40 else 300
                fun wants(kind: SearchFilter) = f == SearchFilter.ALL || f == kind
                val now = System.currentTimeMillis()
                emit(
                    SearchResults(
                        query = q,
                        live = if (wants(SearchFilter.LIVE)) content.search(p.id, ContentKind.LIVE, q, limit) else emptyList(),
                        // Past programmes are only useful when they can still be replayed.
                        programmes = if (wants(SearchFilter.GUIDE)) {
                            content.searchProgrammes(p.id, q, limit, now)
                                .filter { it.programme.stopMs > now || CatchupResolver.isReplayable(p, it.channel, it.programme, now) }
                        } else {
                            emptyList()
                        },
                        movies = if (wants(SearchFilter.MOVIES)) content.search(p.id, ContentKind.MOVIE, q, limit) else emptyList(),
                        series = if (wants(SearchFilter.SERIES)) content.search(p.id, ContentKind.SERIES, q, limit) else emptyList(),
                    )
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setFilter(filter: SearchFilter) {
        _filter.value = filter
    }

    fun channelRequest(s: StreamEntity) = liveRequest(playlist.value, s)

    fun canReplay(hit: ProgrammeHit) = CatchupResolver.isReplayable(playlist.value, hit.channel, hit.programme)

    /**
     * Replays a programme that has aired (or is airing, from the start) when the archive allows;
     * otherwise tunes to the channel live.
     */
    fun programmeRequest(hit: ProgrammeHit): PlayRequest {
        val p = hit.programme
        val now = System.currentTimeMillis()
        val onNow = p.startMs <= now && p.stopMs > now
        return (if (!onNow) replayRequest(playlist.value, hit.channel, p) else null)
            ?: liveRequest(playlist.value, hit.channel, subtitle = if (onNow) p.title else null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit, onOpen: (StreamEntity) -> Unit) {
    val vm = mosaicViewModel { c, _ -> SearchViewModel(c) }
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val activePlaylist by vm.playlist.collectAsStateWithLifecycle()
    val vodEnabled = activePlaylist?.vodEnabled != false
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    TextField(
                        value = query,
                        onValueChange = vm::setQuery,
                        placeholder = { Text(if (vodEnabled) "Search channels, TV guide, movies, series" else "Search channels and TV guide") },
                        singleLine = true,
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, "Clear") }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "filters") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val options = SearchFilter.entries.filter { vodEnabled || !it.needsVod }
                    items(options, key = { it.name }) { option ->
                        FilterChip(selected = filter == option, onClick = { vm.setFilter(option) }, label = { Text(option.label) })
                    }
                }
            }
            when {
                results.searching && results.isEmpty -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator() }
                }
                query.trim().length < 2 -> item(key = "hint") {
                    EmptyState(Icons.Filled.Search, "Search your playlist", "Type at least two characters.")
                }
                !results.searching && results.isEmpty -> item(key = "none") {
                    EmptyState(Icons.Filled.Search, "No results", "Nothing matches \"${results.query}\".")
                }
            }
            section("Live TV", results.live) { s -> context.play(vm.channelRequest(s)) }
            programmeSection(results.programmes, vm::canReplay) { hit -> context.play(vm.programmeRequest(hit)) }
            section("Movies", results.movies, onOpen)
            section("Series", results.series, onOpen)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    list: List<StreamEntity>,
    onClick: (StreamEntity) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "h-$title") { SectionHeader(title) }
    items(list, key = { "$title-${it.streamId}" }) { s ->
        ListItem(
            modifier = Modifier.clickable { onClick(s) },
            leadingContent = {
                Artwork(
                    url = s.logo,
                    fallbackText = s.name,
                    contentScale = if (s.kind == ContentKind.LIVE) ContentScale.Fit else ContentScale.Crop,
                    shape = RoundedCornerShape(10.dp),
                    modifier = if (s.kind == ContentKind.LIVE) Modifier.size(64.dp, 44.dp) else Modifier.size(44.dp, 64.dp),
                )
            },
            headlineContent = { Text(s.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = listOfNotNull(s.year, s.genre).joinToString(" · ").takeIf { it.isNotBlank() }?.let { meta ->
                @Composable { Text(meta, style = MaterialTheme.typography.bodySmall) }
            },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.programmeSection(
    hits: List<ProgrammeHit>,
    canReplay: (ProgrammeHit) -> Boolean,
    onClick: (ProgrammeHit) -> Unit,
) {
    if (hits.isEmpty()) return
    item(key = "h-guide") { SectionHeader("TV guide") }
    items(hits, key = { "guide-${it.programme.id}" }) { hit ->
        val p = hit.programme
        val now = remember(hit) { System.currentTimeMillis() }
        val onNow = p.startMs <= now && p.stopMs > now
        val replay = !onNow && p.stopMs <= now && canReplay(hit)
        ListItem(
            modifier = Modifier.clickable { onClick(hit) },
            colors = if (onNow) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ListItemDefaults.colors(),
            leadingContent = {
                Artwork(
                    url = hit.channel.logo,
                    fallbackText = hit.channel.name,
                    contentScale = ContentScale.Fit,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(64.dp, 44.dp),
                )
            },
            overlineContent = {
                val time = "${formatTime(p.startMs)} – ${formatTime(p.stopMs)}"
                Text(if (onNow) "$time  ·  On now" else "${formatDay(p.startMs)}  $time", maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            headlineContent = {
                Text(p.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = if (onNow) FontWeight.SemiBold else null)
            },
            supportingContent = { Text(hit.channel.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingContent = if (replay || onNow) {
                { Icon(if (replay) Icons.Filled.Replay else Icons.Filled.PlayArrow, if (replay) "Watch again" else "Watch live") }
            } else {
                null
            },
        )
    }
}
