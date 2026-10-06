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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import app.mosaic.data.repo.ContentRepository
import app.mosaic.player.PlayRequest
import app.mosaic.player.play
import app.mosaic.ui.components.Artwork
import app.mosaic.ui.components.EmptyState
import app.mosaic.ui.components.SectionHeader
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

data class SearchResults(
    val query: String = "",
    val searching: Boolean = false,
    val live: List<StreamEntity> = emptyList(),
    val movies: List<StreamEntity> = emptyList(),
    val series: List<StreamEntity> = emptyList(),
) {
    val isEmpty get() = live.isEmpty() && movies.isEmpty() && series.isEmpty()
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(container: AppContainer) : ViewModel() {
    private val content = container.content
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow<ContentKind?>(null)
    val filter: StateFlow<ContentKind?> = _filter.asStateFlow()

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
                val limit = if (f == null) 40 else 300
                emit(
                    SearchResults(
                        query = q,
                        live = if (f == null || f == ContentKind.LIVE) content.search(p.id, ContentKind.LIVE, q, limit) else emptyList(),
                        movies = if (f == null || f == ContentKind.MOVIE) content.search(p.id, ContentKind.MOVIE, q, limit) else emptyList(),
                        series = if (f == null || f == ContentKind.SERIES) content.search(p.id, ContentKind.SERIES, q, limit) else emptyList(),
                    )
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setFilter(kind: ContentKind?) {
        _filter.value = kind
    }

    fun channelRequest(s: StreamEntity) = PlayRequest(
        playlistId = s.playlistId,
        kind = ContentKind.LIVE,
        streamId = s.streamId,
        historyKey = ContentRepository.liveKey(s.streamId),
        url = s.url,
        title = s.name,
        artwork = s.logo,
        userAgent = s.userAgent ?: playlist.value?.userAgent,
        referrer = s.referrer,
    )
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
                        placeholder = { Text("Search channels, movies, series") },
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
                    val options = if (vodEnabled) {
                        listOf(null to "All", ContentKind.LIVE to "Live TV", ContentKind.MOVIE to "Movies", ContentKind.SERIES to "Series")
                    } else {
                        listOf(null to "All", ContentKind.LIVE to "Live TV")
                    }
                    items(options, key = { it.second }) { (kind, label) ->
                        FilterChip(selected = filter == kind, onClick = { vm.setFilter(kind) }, label = { Text(label) })
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
