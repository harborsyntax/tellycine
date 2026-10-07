package app.mosaic.ui.library

import app.mosaic.data.settings.LibrarySort
import app.mosaic.data.db.HistoryRow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.RadioButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.mosaic.AppContainer
import app.mosaic.core.ContentKind
import app.mosaic.data.db.HistoryEntity
import app.mosaic.data.db.PlaylistEntity
import app.mosaic.data.db.StreamEntity
import app.mosaic.data.repo.ContentRepository
import app.mosaic.player.PlayRequest
import app.mosaic.player.play
import app.mosaic.ui.components.Artwork
import app.mosaic.ui.components.EmptyState
import app.mosaic.ui.components.SectionHeader
import app.mosaic.ui.components.mosaicViewModel
import app.mosaic.ui.home.PlaylistTopBar
import app.mosaic.ui.home.SessionState
import app.mosaic.ui.vod.PosterCard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryState(
    val continueWatching: List<LibraryGroup<HistoryRow>> = emptyList(),
    val recentChannels: List<LibraryGroup<HistoryRow>> = emptyList(),
    val channels: List<LibraryGroup<StreamEntity>> = emptyList(),
    val movies: List<LibraryGroup<StreamEntity>> = emptyList(),
    val series: List<LibraryGroup<StreamEntity>> = emptyList(),
    val playlist: PlaylistEntity? = null,
    val sort: LibrarySort = LibrarySort.RECENT,
    val groupByCategory: Boolean = false,
) {
    val isEmpty get() = continueWatching.isEmpty() && recentChannels.isEmpty() && channels.isEmpty() && movies.isEmpty() && series.isEmpty()
}

private data class LibraryData(
    val continueWatching: List<HistoryRow>,
    val recentChannels: List<HistoryRow>,
    val channels: List<StreamEntity>,
    val movies: List<StreamEntity>,
    val series: List<StreamEntity>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    private val content = container.content

    val state: StateFlow<LibraryState> = container.playlists.activePlaylist
        .flatMapLatest { p ->
            if (p == null) return@flatMapLatest flowOf(LibraryState())
            val data = combine(
                content.continueWatching(p.id),
                content.recentChannels(p.id),
                content.favoriteItems(p.id, ContentKind.LIVE, 200),
                content.favoriteItems(p.id, ContentKind.MOVIE, 200),
                content.favoriteItems(p.id, ContentKind.SERIES, 200),
            ) { cw, recent, ch, mv, sr ->
                if (p.vodEnabled) LibraryData(cw, recent, ch, mv, sr)
                else LibraryData(emptyList(), recent, ch, emptyList(), emptyList())
            }
            // Category names and order per kind, for group headers.
            val categories = combine(
                content.categoryVisibility(p.id, ContentKind.LIVE),
                content.categoryVisibility(p.id, ContentKind.MOVIE),
                content.categoryVisibility(p.id, ContentKind.SERIES),
            ) { live, movies, series ->
                mapOf(
                    ContentKind.LIVE to live.associate { it.category.categoryId to CategoryInfo(it.category.name, it.category.sortOrder) },
                    ContentKind.MOVIE to movies.associate { it.category.categoryId to CategoryInfo(it.category.name, it.category.sortOrder) },
                    ContentKind.SERIES to series.associate { it.category.categoryId to CategoryInfo(it.category.name, it.category.sortOrder) },
                )
            }
            combine(data, categories, container.settings.settings) { d, cats, prefs ->
                val sort = prefs.librarySort
                val group = prefs.libraryGroupByCategory
                fun streams(list: List<StreamEntity>, kind: ContentKind) = arrange(
                    list, sort, group, cats[kind].orEmpty(),
                    name = { it.name }, categoryId = { it.categoryId }, number = { it.number }, sortOrder = { it.sortOrder },
                )
                fun history(list: List<HistoryRow>) = arrange(
                    list, sort, group,
                    // Continue watching mixes movies and series, whose category ids live in separate namespaces.
                    categories = list.map { it.history.kind }.distinct()
                        .flatMap { k -> cats[k].orEmpty().map { (id, info) -> "${k.name}:$id" to info } }.toMap(),
                    name = { it.history.title },
                    categoryId = { r -> r.categoryId?.let { "${r.history.kind.name}:$it" } },
                    number = { it.number },
                    sortOrder = { it.sortOrder },
                )
                LibraryState(
                    continueWatching = history(d.continueWatching),
                    recentChannels = history(d.recentChannels),
                    channels = streams(d.channels, ContentKind.LIVE),
                    movies = streams(d.movies, ContentKind.MOVIE),
                    series = streams(d.series, ContentKind.SERIES),
                    playlist = p,
                    sort = sort,
                    groupByCategory = group,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    fun setSort(sort: LibrarySort) {
        viewModelScope.launch { container.settings.setLibrarySort(sort) }
    }

    fun setGroupByCategory(enabled: Boolean) {
        viewModelScope.launch { container.settings.setLibraryGroupByCategory(enabled) }
    }

    fun remove(item: HistoryEntity) {
        viewModelScope.launch { content.removeHistory(item.playlistId, item.itemKey) }
    }

    suspend fun channelRequest(stream: StreamEntity): PlayRequest {
        val playlist = container.playlists.get(stream.playlistId)
        return PlayRequest(
            playlistId = stream.playlistId,
            kind = ContentKind.LIVE,
            streamId = stream.streamId,
            historyKey = ContentRepository.liveKey(stream.streamId),
            url = stream.url,
            title = stream.name,
            artwork = stream.logo,
            userAgent = stream.userAgent ?: playlist?.userAgent,
            referrer = stream.referrer,
        )
    }

    suspend fun historyRequest(item: HistoryEntity): PlayRequest {
        val playlist = container.playlists.get(item.playlistId)
        val stream = content.getStream(item.playlistId, item.kind, item.streamId)
        return PlayRequest(
            playlistId = item.playlistId,
            kind = item.kind,
            streamId = item.streamId,
            historyKey = item.itemKey,
            // Live URLs are refreshed from the playlist in case credentials changed.
            url = if (item.kind == ContentKind.LIVE) stream?.url ?: item.url else item.url,
            title = item.title,
            subtitle = item.subtitle,
            artwork = item.artwork,
            userAgent = stream?.userAgent ?: playlist?.userAgent,
            referrer = stream?.referrer,
            startPositionMs = if (item.kind == ContentKind.LIVE) 0 else item.positionMs,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    session: SessionState,
    onSelectPlaylist: (Long) -> Unit,
    onSearch: () -> Unit,
    onOpen: (StreamEntity) -> Unit,
    onOpenRecordings: (Long) -> Unit,
    onConnectDvr: (Long) -> Unit,
) {
    val vm = mosaicViewModel { c, _ -> LibraryViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        topBar = {
            PlaylistTopBar("Library", session, onSelectPlaylist, onSearch, scrollBehavior) {
                LibrarySortMenu(state.sort, state.groupByCategory, vm::setSort, vm::setGroupByCategory)
            }
        },
        contentWindowInsets = WindowInsets(0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        val dvrPlaylist = state.playlist?.takeIf { it.isDispatcharr }
        if (state.isEmpty && dvrPlaylist == null) {
            EmptyState(
                Icons.Filled.FavoriteBorder,
                "Your library is empty",
                "Favourites, recently watched channels and anything you haven't finished will show up here.",
                Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (dvrPlaylist != null) {
                item(key = "dvr") {
                    ListItem(
                        modifier = Modifier.clickable {
                            if (dvrPlaylist.hasDvr) onOpenRecordings(dvrPlaylist.id) else onConnectDvr(dvrPlaylist.id)
                        },
                        leadingContent = { Icon(Icons.Filled.FiberManualRecord, null, tint = MaterialTheme.colorScheme.error) },
                        headlineContent = { Text("Recordings") },
                        supportingContent = {
                            Text(
                                if (dvrPlaylist.hasDvr) "Watch and manage your Dispatcharr recordings"
                                else "Connect your Dispatcharr login to watch and schedule recordings"
                            )
                        },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    )
                }
            }
            section("cw", "Continue watching", state.continueWatching) { h ->
                ContinueCard(
                    item = h.history,
                    onClick = { scope.launch { context.play(vm.historyRequest(h.history)) } },
                    onRemove = { vm.remove(h.history) },
                )
            }
            section("rc", "Recently watched channels", state.recentChannels) { h ->
                ChannelTile(h.history.title, h.history.artwork) { scope.launch { context.play(vm.historyRequest(h.history)) } }
            }
            section("fc", "Favourite channels", state.channels) { s ->
                ChannelTile(s.name, s.logo) { scope.launch { context.play(vm.channelRequest(s)) } }
            }
            for ((key, label, groups) in listOf(
                Triple("fm", "Favourite movies", state.movies),
                Triple("fs", "Favourite series", state.series),
            )) {
                section(key, label, groups) { s ->
                    PosterCard(
                        title = s.name,
                        poster = s.logo,
                        meta = s.year,
                        rating = s.rating,
                        onClick = { onOpen(s) },
                        modifier = Modifier.width(120.dp),
                    )
                }
            }
        }
    }
}

/** A Library section: a header, then one horizontal row per group (a single row when not grouping). */
private fun <T> LazyListScope.section(
    key: String,
    title: String,
    groups: List<LibraryGroup<T>>,
    itemContent: @Composable (T) -> Unit,
) {
    if (groups.isEmpty()) return
    item(key = "$key-h") { SectionHeader(title) }
    for (group in groups) {
        if (group.label != null) {
            item(key = "$key-g-${group.key}") {
                Text(
                    group.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
                )
            }
        }
        item(key = "$key-r-${group.key}") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = if (group.label != null) 12.dp else 0.dp),
            ) {
                items(group.items) { itemContent(it) }
            }
        }
    }
}

@Composable
private fun LibrarySortMenu(
    sort: LibrarySort,
    groupByCategory: Boolean,
    onSort: (LibrarySort) -> Unit,
    onGroup: (Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort and group") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                "Sort by",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            listOf(
                LibrarySort.RECENT to "Most recent",
                LibrarySort.NAME_ASC to "Name (A–Z)",
                LibrarySort.NAME_DESC to "Name (Z–A)",
                LibrarySort.PLAYLIST_ORDER to "Playlist order",
            ).forEach { (option, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { RadioButton(selected = option == sort, onClick = null) },
                    onClick = {
                        onSort(option)
                        open = false
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Group by category") },
                leadingIcon = { Checkbox(checked = groupByCategory, onCheckedChange = null) },
                onClick = {
                    onGroup(!groupByCategory)
                    open = false
                },
            )
        }
    }
}

@Composable
private fun ContinueCard(item: HistoryEntity, onClick: () -> Unit, onRemove: () -> Unit) {
    Column(Modifier.width(220.dp).clickable(onClick = onClick)) {
        Box {
            Artwork(
                url = item.artwork,
                fallbackText = item.title,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
            IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Filled.Close, "Remove", tint = MaterialTheme.colorScheme.onSurface)
            }
            if (item.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp).height(4.dp),
                    drawStopIndicator = {},
                )
            }
        }
        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        item.subtitle?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ChannelTile(name: String, logo: String?, onClick: () -> Unit) {
    Column(
        Modifier.width(112.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(
            url = logo,
            fallbackText = name,
            contentScale = ContentScale.Fit,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.size(width = 112.dp, height = 76.dp),
        )
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
