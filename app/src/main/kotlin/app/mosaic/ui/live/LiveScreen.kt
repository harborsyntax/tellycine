package app.mosaic.ui.live

import app.mosaic.core.dispatcharr.DvrRecording
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.filled.FiberManualRecord
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import app.mosaic.data.db.ProgrammeEntity
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.filled.Replay
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
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
import app.mosaic.core.ContentKind
import app.mosaic.data.repo.Categories
import app.mosaic.player.play
import app.mosaic.ui.components.CategoryChips
import app.mosaic.ui.components.CategoryPane
import app.mosaic.ui.components.EmptyState
import app.mosaic.ui.components.categoryOptions
import app.mosaic.ui.components.formatDay
import app.mosaic.ui.components.formatTime
import app.mosaic.ui.components.mosaicViewModel
import app.mosaic.ui.home.PlaylistTopBar
import app.mosaic.ui.home.SessionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(
    session: SessionState,
    onSelectPlaylist: (Long) -> Unit,
    onSearch: () -> Unit,
) {
    val vm = mosaicViewModel { c, _ -> LiveViewModel(c) }
    val context = LocalContext.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selectedCategory.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val channels = vm.channels.collectAsLazyPagingItems()
    val options = remember(categories, session.active?.liveCount) {
        // Count only what's visible once categories are known (hidden ones are excluded).
        categoryOptions(ContentKind.LIVE, categories, if (categories.isEmpty()) session.active?.liveCount else categories.sumOf { it.itemCount })
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
                                    hasCatchup = vm.hasCatchup(row),
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

    val recordings by vm.recordings.collectAsStateWithLifecycle()
    LaunchedEffect(vm) {
        vm.events.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    schedule?.let { state ->
        val canRecord = vm.canRecord(state.channel)
        ScheduleSheet(
            state = state,
            canReplay = { vm.canReplay(state.channel, it) },
            recordingFor = { if (canRecord) vm.recordingFor(state.channel, it, recordings) else null },
            canRecord = canRecord,
            onToggleRecording = { vm.toggleRecording(state.channel, it) },
            onPlay = {
                context.play(vm.playRequest(state.channel))
                vm.dismissSchedule()
            },
            onReplay = { programme ->
                vm.replayRequest(state.channel, programme)?.let {
                    context.play(it)
                    vm.dismissSchedule()
                }
            },
            onDismiss = vm::dismissSchedule,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSheet(
    state: ScheduleState,
    canReplay: (ProgrammeEntity) -> Boolean,
    recordingFor: (ProgrammeEntity) -> DvrRecording?,
    canRecord: Boolean,
    onToggleRecording: (ProgrammeEntity) -> Unit,
    onPlay: () -> Unit,
    onReplay: (ProgrammeEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        val now = remember(state.programmes) { System.currentTimeMillis() }
        val current = state.programmes?.firstOrNull { it.startMs <= now && it.stopMs > now }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text(state.channel.stream.name, style = MaterialTheme.typography.headlineSmall)
            if (state.catchup) {
                Text(
                    "Catch-up available for ${state.channel.stream.catchupDays} day${if (state.channel.stream.catchupDays == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPlay) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Watch live")
                }
                if (current != null && canReplay(current)) {
                    FilledTonalButton(onClick = { onReplay(current) }) {
                        Icon(Icons.Filled.Replay, null)
                        Spacer(Modifier.width(8.dp))
                        Text("From the start")
                    }
                }
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
                // Open on what's on now, with a little of what came before.
                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = (programmes.indexOfFirst { it.stopMs > now } - 1).coerceAtLeast(0),
                )
                LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(rows, key = { it.first.id }) { (p, day, showDay) ->
                        val live = p.startMs <= now && p.stopMs > now
                        val past = p.stopMs <= now
                        val replayable = (past || live) && canReplay(p)
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
                                modifier = Modifier
                                    .then(if (replayable) Modifier.clickable { onReplay(p) } else Modifier)
                                    .alpha(if (past && !replayable) 0.55f else 1f),
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
                                trailingContent = if (replayable || (canRecord && !past)) {
                                    {
                                        Row {
                                            if (replayable) {
                                                IconButton(onClick = { onReplay(p) }) {
                                                    Icon(Icons.Filled.Replay, if (live) "Watch from the start" else "Watch again")
                                                }
                                            }
                                            if (canRecord && !past) {
                                                val recording = recordingFor(p)
                                                IconButton(onClick = { onToggleRecording(p) }) {
                                                    if (recording != null) {
                                                        Icon(Icons.Filled.FiberManualRecord, "Cancel recording", tint = MaterialTheme.colorScheme.error)
                                                    } else {
                                                        Icon(Icons.Outlined.FiberManualRecord, "Record")
                                                    }
                                                }
                                            }
                                        }
                                    }
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
