package app.tellycine.tv.live

import android.view.KeyEvent.KEYCODE_DPAD_LEFT
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.tellycine.core.ContentKind
import app.tellycine.data.db.ChannelRow
import app.tellycine.data.db.ProgrammeEntity
import app.tellycine.data.repo.Categories
import app.tellycine.player.play
import app.tellycine.tv.TvPalette
import app.tellycine.tv.components.RequestFocusOnStart
import app.tellycine.tv.components.TvActionButton
import app.tellycine.tv.components.TvCategoryPanel
import app.tellycine.tv.components.TvClock
import app.tellycine.tv.components.TvEmptyState
import app.tellycine.tv.components.TvHeader
import app.tellycine.tv.components.TvQrDialog
import app.tellycine.tv.components.TvSpacing
import app.tellycine.tv.components.okClicks
import app.tellycine.ui.components.Artwork
import app.tellycine.ui.components.categoryOptions
import app.tellycine.ui.components.formatDay
import app.tellycine.ui.components.formatTime
import app.tellycine.ui.components.tellycineViewModel
import app.tellycine.ui.home.SessionState
import app.tellycine.ui.live.GuideWindow
import app.tellycine.ui.live.LiveViewModel
import kotlinx.coroutines.delay

@Composable
fun TvLiveScreen(session: SessionState, onAddPlaylist: () -> Unit) {
    val vm = tellycineViewModel { c, _ -> LiveViewModel(c) }
    val context = LocalContext.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selectedCategory.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    val channels = vm.channels.collectAsLazyPagingItems()
    val options = remember(categories, session.active?.liveCount) {
        categoryOptions(ContentKind.LIVE, categories, if (categories.isEmpty()) session.active?.liveCount else categories.sumOf { it.itemCount })
    }
    val categoryName = options.firstOrNull { it.id == selected }?.name ?: "All channels"
    // The guide is the TV's main view; the plain list is one press away.
    var listView by rememberSaveable { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    var share by remember { mutableStateOf<Pair<String, String>?>(null) }
    val contentFocus = remember { FocusRequester() }

    val days by vm.guideDays.collectAsStateWithLifecycle()
    val version by vm.guideVersion.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            value = System.currentTimeMillis()
        }
    }
    // Fixed while the screen is open, so the grid doesn't jump as the clock moves.
    val window = remember(days, version) { GuideWindow.around(System.currentTimeMillis(), days) }
    val guide = rememberTvGuideState(window)
    LaunchedEffect(selected) {
        if (guide.row != 0) guide.reset(System.currentTimeMillis())
        guide.listState.scrollToItem(0)
    }

    if (session.active == null) {
        Column(Modifier.fillMaxSize()) {
            TvHeader("Live TV")
            TvEmptyState(
                Icons.Filled.LiveTv,
                "No playlist yet",
                "Add an M3U playlist, Xtream Codes or Dispatcharr login to start watching.",
                actionLabel = "Add a playlist",
                onAction = onAddPlaylist,
            )
        }
        return
    }

    val playRow = { row: ChannelRow -> context.play(vm.playRequest(row)) }
    val headerActions: @Composable () -> Unit = {
        TvActionButton(categoryName, onClick = { showCategories = true }, icon = Icons.Filled.Category)
        TvActionButton(
            if (listView) "Guide" else "List",
            onClick = { listView = !listView },
            icon = if (listView) Icons.Outlined.CalendarViewWeek else Icons.AutoMirrored.Filled.ViewList,
        )
    }

    Column(Modifier.fillMaxSize()) {
        if (listView) {
            TvHeader("Live TV", subtitle = session.active.name, actions = { headerActions() })
        } else {
            val focusedRow = channels.itemCount.takeIf { it > 0 }?.let { channels.peek(guide.row.coerceIn(0, it - 1)) }
            GuideDetails(
                row = focusedRow,
                programme = guide.focusedProgramme(focusedRow),
                now = now,
                canReplay = { r, p -> vm.canReplay(r, p) },
                isRecording = { r, p -> vm.canRecord(r) && vm.recordingFor(r, p, recordings) != null },
                actions = headerActions,
            )
        }
        val refreshing = channels.loadState.refresh is LoadState.Loading
        val body = Modifier.weight(1f).fillMaxWidth()
        when {
            channels.itemCount == 0 && refreshing -> Box(body, Alignment.Center) { CircularProgressIndicator() }
            channels.itemCount == 0 && selected == Categories.FAVORITES -> TvEmptyState(
                Icons.Filled.FavoriteBorder,
                "No favourite channels",
                "Hold OK on a channel and choose Add to favourites to pin it here.",
                body,
            )
            channels.itemCount == 0 -> TvEmptyState(
                Icons.Filled.LiveTv,
                if (session.activeProgress != null) "Loading channels…" else "No channels",
                session.activeProgress?.message ?: "This playlist has no live channels in this category.",
                body,
            )
            listView -> TvChannelList(
                channels = channels,
                onPlay = playRow,
                onOptions = vm::showSchedule,
                onLeftEdge = { showCategories = true },
                modifier = body.focusRequester(contentFocus),
            )
            else -> TvGuideGrid(
                state = guide,
                channels = channels,
                now = now,
                version = version,
                loadProgrammes = { row -> vm.guideProgrammes(row, window.startMs, window.endMs) },
                canReplay = vm::canReplay,
                isRecording = { row, p -> vm.canRecord(row) && vm.recordingFor(row, p, recordings) != null },
                onPlay = playRow,
                onProgramme = { row, p ->
                    val t = System.currentTimeMillis()
                    when {
                        p.startMs <= t && p.stopMs > t -> playRow(row)
                        p.stopMs <= t && vm.canReplay(row, p) -> vm.replayRequest(row, p)?.let(context::play)
                        // Upcoming or not replayable: the channel's options and schedule.
                        else -> vm.showSchedule(row)
                    }
                },
                onOptions = vm::showSchedule,
                onLeftEdge = { showCategories = true },
                focusRequester = contentFocus,
                modifier = body.padding(start = TvSpacing.screenStart, end = TvSpacing.screenEnd, bottom = 12.dp),
            )
        }
    }
    RequestFocusOnStart(contentFocus, listView to (channels.itemCount > 0))

    if (showCategories) {
        TvCategoryPanel(
            title = "Live TV categories",
            options = options,
            selected = selected,
            onSelect = {
                vm.selectCategory(it)
                showCategories = false
            },
            onDismiss = { showCategories = false },
        )
    }

    schedule?.let { state ->
        TvChannelPanel(
            state = state,
            vm = vm,
            recordings = recordings,
            onShare = { label, url -> share = label to url },
        )
    }
    val sources by vm.sources.collectAsStateWithLifecycle()
    sources?.let { list -> TvSourcePicker(list, onPick = vm::pickSource) }
    share?.let { (label, url) ->
        TvQrDialog(title = label, url = url, note = "This link includes your login details.", onDismiss = { share = null })
    }
    LaunchedEffect(vm) {
        vm.events.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(vm) {
        vm.shareRequests.collect { (label, url) -> share = label to url }
    }
}

/** The top of the guide: what the highlight is on, with the screen's actions and the clock. */
@Composable
private fun GuideDetails(
    row: ChannelRow?,
    programme: ProgrammeEntity?,
    now: Long,
    canReplay: (ChannelRow, ProgrammeEntity) -> Boolean,
    isRecording: (ChannelRow, ProgrammeEntity) -> Boolean,
    actions: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .padding(start = TvSpacing.screenStart, end = TvSpacing.screenEnd, top = TvSpacing.screenTop, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (row != null) {
                Text(
                    listOfNotNull(row.stream.number?.toString(), row.stream.name).joinToString("  "),
                    style = MaterialTheme.typography.labelLarge,
                    color = TvPalette.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    programme?.title ?: row.nowTitle ?: row.stream.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (programme != null) {
                    val onNow = programme.startMs <= now && programme.stopMs > now
                    val status = when {
                        onNow -> "On now"
                        programme.stopMs <= now && canReplay(row, programme) -> "Catch-up available"
                        programme.stopMs > now && isRecording(row, programme) -> "Recording scheduled"
                        else -> null
                    }
                    Text(
                        listOfNotNull(
                            if (onNow) null else formatDay(programme.startMs),
                            "${formatTime(programme.startMs)} – ${formatTime(programme.stopMs)}",
                            status,
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    programme.description?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Text(
                        "Press OK to watch. Hold OK for the full schedule and more options.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TvClock()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { actions() }
        }
    }
}

/** Channels as a plain list, with what's on now. */
@Composable
private fun TvChannelList(
    channels: LazyPagingItems<ChannelRow>,
    onPlay: (ChannelRow) -> Unit,
    onOptions: (ChannelRow) -> Unit,
    onLeftEdge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier
            .focusRestorer()
            // Left opens the categories; Back opens the side menu.
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.keyCode == KEYCODE_DPAD_LEFT) {
                    if (event.type == KeyEventType.KeyDown) onLeftEdge()
                    true
                } else {
                    false
                }
            },
        contentPadding = PaddingValues(start = TvSpacing.screenStart, end = TvSpacing.screenEnd, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(channels.itemCount, key = channels.itemKey { "${it.stream.kind}:${it.stream.streamId}" }) { index ->
            val row = channels[index]
            if (row == null) {
                Box(Modifier.fillMaxWidth().height(64.dp))
            } else {
                val s = row.stream
                ListItem(
                    selected = false,
                    onClick = { onPlay(row) },
                    modifier = Modifier.okClicks(onClick = { onPlay(row) }, onLongClick = { onOptions(row) }),
                    headlineContent = {
                        Text(
                            listOfNotNull(s.number?.toString(), s.name).joinToString("   "),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = row.nowTitle?.let { title ->
                        {
                            val time = if (row.nowStart != null && row.nowStop != null) "${formatTime(row.nowStart)} – ${formatTime(row.nowStop)}  ·  " else ""
                            Text("$time$title", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    leadingContent = {
                        Artwork(
                            url = s.logo,
                            fallbackText = s.name,
                            contentScale = ContentScale.Fit,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.size(width = 64.dp, height = 44.dp),
                        )
                    },
                    trailingContent = if (row.isFavorite) {
                        { Icon(Icons.Filled.Favorite, "Favourite", Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
