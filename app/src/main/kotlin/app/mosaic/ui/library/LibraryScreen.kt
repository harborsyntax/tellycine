package app.mosaic.ui.library

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
    val continueWatching: List<HistoryEntity> = emptyList(),
    val recentChannels: List<HistoryEntity> = emptyList(),
    val channels: List<StreamEntity> = emptyList(),
    val movies: List<StreamEntity> = emptyList(),
    val series: List<StreamEntity> = emptyList(),
    val playlist: PlaylistEntity? = null,
) {
    val isEmpty get() = continueWatching.isEmpty() && recentChannels.isEmpty() && channels.isEmpty() && movies.isEmpty() && series.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    private val content = container.content

    val state: StateFlow<LibraryState> = container.playlists.activePlaylist
        .flatMapLatest { p ->
            if (p == null) flowOf(LibraryState())
            else combine(
                content.continueWatching(p.id),
                content.recentChannels(p.id),
                content.favoriteItems(p.id, ContentKind.LIVE, 100),
                content.favoriteItems(p.id, ContentKind.MOVIE),
                content.favoriteItems(p.id, ContentKind.SERIES),
            ) { cw, recent, ch, mv, sr ->
                if (p.vodEnabled) LibraryState(cw, recent, ch, mv, sr, p) else LibraryState(recentChannels = recent, channels = ch, playlist = p)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

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
        topBar = { PlaylistTopBar("Library", session, onSelectPlaylist, onSearch, scrollBehavior) },
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
            if (state.continueWatching.isNotEmpty()) {
                item(key = "cw-h") { SectionHeader("Continue watching") }
                item(key = "cw") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.continueWatching, key = { it.itemKey }) { h ->
                            ContinueCard(
                                item = h,
                                onClick = { scope.launch { context.play(vm.historyRequest(h)) } },
                                onRemove = { vm.remove(h) },
                            )
                        }
                    }
                }
            }
            if (state.recentChannels.isNotEmpty()) {
                item(key = "rc-h") { SectionHeader("Recently watched channels") }
                item(key = "rc") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.recentChannels, key = { it.itemKey }) { h ->
                            ChannelTile(h.title, h.artwork) { scope.launch { context.play(vm.historyRequest(h)) } }
                        }
                    }
                }
            }
            if (state.channels.isNotEmpty()) {
                item(key = "fc-h") { SectionHeader("Favourite channels") }
                item(key = "fc") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.channels, key = { it.streamId }) { s ->
                            ChannelTile(s.name, s.logo) { scope.launch { context.play(vm.channelRequest(s)) } }
                        }
                    }
                }
            }
            for ((label, list) in listOf("Favourite movies" to state.movies, "Favourite series" to state.series)) {
                if (list.isEmpty()) continue
                item(key = "$label-h") { SectionHeader(label) }
                item(key = label) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(list, key = { it.streamId }) { s ->
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
