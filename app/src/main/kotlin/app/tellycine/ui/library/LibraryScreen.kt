package app.tellycine.ui.library

import app.tellycine.data.db.artwork
import app.tellycine.data.db.score
import app.tellycine.data.settings.LibrarySort
import app.tellycine.data.db.HistoryRow
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
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.HeartBroken
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Row
import app.tellycine.AppContainer
import app.tellycine.data.db.DownloadStatus
import app.tellycine.core.ContentKind
import app.tellycine.data.db.HistoryEntity
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.repo.ContentRepository
import app.tellycine.player.PlayRequest
import app.tellycine.player.play
import app.tellycine.ui.components.Artwork
import app.tellycine.ui.components.EmptyState
import app.tellycine.ui.components.ListItem
import app.tellycine.ui.components.SectionHeader
import app.tellycine.ui.components.tellycineViewModel
import app.tellycine.ui.home.PlaylistTopBar
import app.tellycine.ui.home.SessionState
import app.tellycine.ui.vod.PosterCard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
/** Counts for the Library's Downloads entry. */
data class DownloadSummary(val done: Int = 0, val active: Int = 0, val failed: Int = 0) {
    val count get() = done + active + failed
}

class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    private val content = container.content

    val downloads: StateFlow<DownloadSummary> = container.downloads.all
        .map { list ->
            DownloadSummary(
                done = list.count { it.status == DownloadStatus.COMPLETED },
                active = list.count { it.status.isActive },
                failed = list.count { it.status == DownloadStatus.FAILED },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadSummary())

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

    fun restore(item: HistoryEntity) {
        viewModelScope.launch { content.restoreHistory(item) }
    }

    fun setFavorite(stream: StreamEntity, favorite: Boolean) {
        viewModelScope.launch { content.setFavorite(stream.playlistId, stream.kind, stream.streamId, favorite) }
    }

    fun clearContinueWatching() {
        val id = state.value.playlist?.id ?: return
        viewModelScope.launch { content.clearContinueWatching(id) }
    }

    fun clearRecentChannels() {
        val id = state.value.playlist?.id ?: return
        viewModelScope.launch { content.clearRecentChannels(id) }
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
    onOpenDownloads: () -> Unit,
    onConnectDvr: (Long) -> Unit,
) {
    val vm = tellycineViewModel { c, _ -> LibraryViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf<ClearTarget?>(null) }

    /** Shows [message] with an Undo action that runs [undo]. */
    fun undoable(message: String, undo: () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) undo()
        }
    }
    fun removeHistory(item: HistoryEntity, from: String) {
        vm.remove(item)
        undoable("Removed from $from") { vm.restore(item) }
    }
    fun removeFavorite(stream: StreamEntity) {
        vm.setFavorite(stream, false)
        undoable("Removed from favourites") { vm.setFavorite(stream, true) }
    }

    Scaffold(
        topBar = {
            PlaylistTopBar(session, onSelectPlaylist, onSearch, scrollBehavior) {
                LibrarySortMenu(state.sort, state.groupByCategory, vm::setSort, vm::setGroupByCategory)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        val dvrPlaylist = state.playlist?.takeIf { it.isDispatcharr }
        if (state.isEmpty && dvrPlaylist == null && downloads.count == 0) {
            EmptyState(
                Icons.Filled.FavoriteBorder,
                "Your library is empty",
                "Favourites, recently watched channels and anything you haven't finished will show up here.",
                Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (downloads.count > 0) {
                item(key = "downloads") {
                    ListItem(
                        modifier = Modifier.clickable(onClick = onOpenDownloads),
                        leadingContent = { Icon(Icons.Outlined.DownloadForOffline, null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text("Downloads") },
                        supportingContent = {
                            Text(
                                buildList {
                                    if (downloads.done > 0) add("${downloads.done} on this device")
                                    if (downloads.active > 0) add("${downloads.active} downloading")
                                    if (downloads.failed > 0) add("${downloads.failed} failed")
                                }.joinToString(" · ")
                            )
                        },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    )
                }
            }
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
            section("cw", "Continue watching", state.continueWatching, onClearAll = { confirmClear = ClearTarget.CONTINUE_WATCHING }) { h ->
                ItemMenu(
                    title = listOfNotNull(h.history.title, h.history.subtitle).joinToString(" · "),
                    action = MenuAction("Remove from continue watching", Icons.Outlined.RemoveCircleOutline) {
                        removeHistory(h.history, "continue watching")
                    },
                ) { onLongClick ->
                    ContinueCard(
                        item = h.history,
                        onClick = { scope.launch { context.play(vm.historyRequest(h.history)) } },
                        onLongClick = onLongClick,
                        onRemove = { removeHistory(h.history, "continue watching") },
                    )
                }
            }
            section("rc", "Recently watched channels", state.recentChannels, onClearAll = { confirmClear = ClearTarget.RECENT_CHANNELS }) { h ->
                ItemMenu(
                    title = h.history.title,
                    action = MenuAction("Remove from recently watched", Icons.Outlined.RemoveCircleOutline) {
                        removeHistory(h.history, "recently watched")
                    },
                ) { onLongClick ->
                    ChannelTile(h.history.title, h.history.artwork, onLongClick) {
                        scope.launch { context.play(vm.historyRequest(h.history)) }
                    }
                }
            }
            section("fc", "Favourite channels", state.channels) { s ->
                ItemMenu(
                    title = s.name,
                    action = MenuAction("Remove from favourites", Icons.Outlined.HeartBroken) { removeFavorite(s) },
                ) { onLongClick ->
                    ChannelTile(s.name, s.logo, onLongClick) { scope.launch { context.play(vm.channelRequest(s)) } }
                }
            }
            for ((key, label, groups) in listOf(
                Triple("fm", "Favourite movies", state.movies),
                Triple("fs", "Favourite series", state.series),
            )) {
                section(key, label, groups) { s ->
                    ItemMenu(
                        title = s.name,
                        action = MenuAction("Remove from favourites", Icons.Outlined.HeartBroken) { removeFavorite(s) },
                    ) { onLongClick ->
                        PosterCard(
                            title = s.name,
                            poster = s.artwork,
                            meta = s.year,
                            rating = s.score,
                            onClick = { onOpen(s) },
                            onLongClick = onLongClick,
                            modifier = Modifier.width(120.dp),
                        )
                    }
                }
            }
        }
    }

    confirmClear?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmClear = null },
            title = { Text(target.title) },
            text = { Text(target.message) },
            confirmButton = {
                TextButton(onClick = {
                    when (target) {
                        ClearTarget.CONTINUE_WATCHING -> vm.clearContinueWatching()
                        ClearTarget.RECENT_CHANNELS -> vm.clearRecentChannels()
                    }
                    confirmClear = null
                }) { Text("Clear all") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = null }) { Text("Cancel") } },
        )
    }
}

/** History lists with a "Clear all" action, and what their confirmation says. */
private enum class ClearTarget(val title: String, val message: String) {
    CONTINUE_WATCHING(
        "Clear continue watching?",
        "Everything you haven't finished will be removed from this list, and its resume point forgotten. " +
            "Your favourites and downloads aren't affected.",
    ),
    RECENT_CHANNELS(
        "Clear recently watched channels?",
        "Your recently watched channels will be cleared. Favourite channels aren't affected.",
    ),
}

private class MenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * Wraps a Library item so a long press opens a small menu, headed by the item's name, with
 * [action]. [content] receives the long-press handler to attach to the item.
 */
@Composable
private fun ItemMenu(title: String, action: MenuAction, content: @Composable (onLongClick: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        content {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            open = true
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 260.dp),
            )
            DropdownMenuItem(
                text = { Text(action.label) },
                leadingIcon = { Icon(action.icon, null) },
                onClick = {
                    open = false
                    action.onClick()
                },
            )
        }
    }
}

/** A Library section: a header, then one horizontal row per group (a single row when not grouping). */
private fun <T> LazyListScope.section(
    key: String,
    title: String,
    groups: List<LibraryGroup<T>>,
    onClearAll: (() -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    if (groups.isEmpty()) return
    item(key = "$key-h") {
        if (onClearAll == null) {
            SectionHeader(title)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(title, Modifier.weight(1f))
                TextButton(onClick = onClearAll, modifier = Modifier.padding(end = 8.dp)) { Text("Clear all") }
            }
        }
    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueCard(item: HistoryEntity, onClick: () -> Unit, onLongClick: () -> Unit, onRemove: () -> Unit) {
    Column(Modifier.width(220.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelTile(name: String, logo: String?, onLongClick: () -> Unit, onClick: () -> Unit) {
    Column(
        Modifier.width(112.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
