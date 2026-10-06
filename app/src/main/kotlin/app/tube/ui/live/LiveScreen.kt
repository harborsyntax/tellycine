package app.tube.ui.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import app.tube.core.ContentKind
import app.tube.data.repo.Categories
import app.tube.player.play
import app.tube.ui.components.CategoryChips
import app.tube.ui.components.CategoryPane
import app.tube.ui.components.EmptyState
import app.tube.ui.components.categoryOptions
import app.tube.ui.components.formatDay
import app.tube.ui.components.formatTime
import app.tube.ui.components.tubeViewModel
import app.tube.ui.home.PlaylistTopBar
import app.tube.ui.home.SessionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(
    session: SessionState,
    onSelectPlaylist: (Long) -> Unit,
    onSearch: () -> Unit,
) {
    val vm = tubeViewModel { c, _ -> LiveViewModel(c) }
    val context = LocalContext.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selectedCategory.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val channels = vm.channels.collectAsLazyPagingItems()
    val options = remember(categories, session.active?.liveCount) {
        categoryOptions(ContentKind.LIVE, categories, session.active?.liveCount)
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        topBar = { PlaylistTopBar("Live TV", session, onSelectPlaylist, onSearch, scrollBehavior) },
        contentWindowInsets = WindowInsets(0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val twoPane = maxWidth >= 720.dp
            val list = @Composable { modifier: Modifier ->
                val refreshing = channels.loadState.refresh is LoadState.Loading
                when {
                    channels.itemCount == 0 && refreshing -> Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    channels.itemCount == 0 && selected == Categories.FAVORITES -> EmptyState(
                        Icons.Filled.FavoriteBorder,
                        "No favourite channels",
                        "Tap the heart next to a channel to pin it here.",
                        modifier,
                    )
                    channels.itemCount == 0 -> EmptyState(
                        Icons.Filled.LiveTv,
                        if (session.activeProgress != null) "Loading channels…" else "No channels",
                        session.activeProgress?.message ?: "This playlist has no live channels in this category.",
                        modifier,
                    )
                    else -> LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(channels.itemCount, key = channels.itemKey { "${it.stream.kind}:${it.stream.streamId}" }) { index ->
                            val row = channels[index]
                            if (row == null) {
                                ChannelPlaceholder()
                            } else {
                                ChannelItem(
                                    row = row,
                                    onClick = { context.play(vm.playRequest(row)) },
                                    onLongClick = { vm.showSchedule(row) },
                                    onToggleFavorite = { vm.toggleFavorite(row) },
                                )
                            }
                        }
                    }
                }
            }

            if (session.active == null) {
                EmptyState(Icons.Filled.LiveTv, "No playlist yet", "Add an M3U playlist or Xtream Codes account from the Playlists tab.")
            } else if (twoPane) {
                Row(Modifier.fillMaxSize()) {
                    CategoryPane(options, selected, vm::selectCategory)
                    VerticalDivider()
                    list(Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    CategoryChips(options, selected, vm::selectCategory, Modifier.padding(bottom = 4.dp))
                    list(Modifier.weight(1f))
                }
            }
        }
    }

    schedule?.let { state ->
        ScheduleSheet(
            state = state,
            onPlay = {
                context.play(vm.playRequest(state.channel))
                vm.dismissSchedule()
            },
            onDismiss = vm::dismissSchedule,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSheet(state: ScheduleState, onPlay: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text(state.channel.stream.name, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onPlay) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.padding(4.dp))
                Text("Watch now")
            }
            Spacer(Modifier.height(16.dp))
        }
        HorizontalDivider()
        val programmes = state.programmes
        when {
            programmes == null -> Box(Modifier.fillMaxWidth().height(160.dp), Alignment.Center) { CircularProgressIndicator() }
            programmes.isEmpty() -> Text(
                "No guide data for this channel.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
            else -> {
                val rows = remember(programmes) {
                    programmes.mapIndexed { i, p ->
                        val day = formatDay(p.startMs)
                        Triple(p, day, i == 0 || day != formatDay(programmes[i - 1].startMs))
                    }
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(rows, key = { it.first.id }) { (p, day, showDay) ->
                        val now = System.currentTimeMillis()
                        val live = p.startMs <= now && p.stopMs > now
                        Column {
                            if (showDay) {
                                Text(
                                    day,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
                                )
                            }
                            val description = p.description
                            ListItem(
                                colors = if (live) {
                                    ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                } else {
                                    ListItemDefaults.colors()
                                },
                                overlineContent = { Text("${formatTime(p.startMs)} – ${formatTime(p.stopMs)}${if (live) "  ·  On now" else ""}") },
                                headlineContent = { Text(p.title, fontWeight = if (live) FontWeight.SemiBold else null) },
                                supportingContent = if (description != null) {
                                    { Text(description, maxLines = 3) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
