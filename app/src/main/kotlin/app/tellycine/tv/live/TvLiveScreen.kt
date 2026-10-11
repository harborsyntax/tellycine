package app.tellycine.tv.live

import android.view.KeyEvent.KEYCODE_DPAD_LEFT
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.tellycine.container
import app.tellycine.core.ContentKind
import app.tellycine.data.db.ChannelRow
import app.tellycine.data.db.NowPlaying
import app.tellycine.data.db.ProgrammeEntity
import app.tellycine.data.repo.Categories
import app.tellycine.player.MiniPlayback
import app.tellycine.player.play
import app.tellycine.tv.TvPalette
import app.tellycine.tv.components.RequestFocusOnStart
import app.tellycine.tv.components.TvActionButton
import app.tellycine.tv.components.TvCategoryPanel
import app.tellycine.tv.components.TvClock
import app.tellycine.tv.components.TvEmptyState
import app.tellycine.tv.components.TvHeader
import app.tellycine.tv.components.TvIconButton
import app.tellycine.tv.components.TvQrDialog
import app.tellycine.tv.components.TvSourceLabel
import app.tellycine.tv.components.TvSpacing
import app.tellycine.tv.components.okClicks
import app.tellycine.tv.components.fasterWhenHeld
import app.tellycine.ui.components.Artwork
import app.tellycine.ui.components.categoryOptions
import app.tellycine.ui.components.formatDay
import app.tellycine.ui.components.formatTime
import app.tellycine.ui.components.tellycineViewModel
import app.tellycine.ui.home.SessionState
import app.tellycine.ui.live.GuideWindow
import app.tellycine.ui.live.LiveViewModel
import app.tellycine.ui.live.rememberMinuteClock
import app.tellycine.ui.live.rememberNowPlaying
import kotlinx.coroutines.delay

@Composable
fun TvLiveScreen(
    session: SessionState,
    onAddPlaylist: () -> Unit,
    /** True while the guide is on screen and wants the full width, without the side menu's rail. */
    onFullWidth: (Boolean) -> Unit = {},
) {
    val vm = tellycineViewModel { c, _ -> LiveViewModel(c) }
    val context = LocalContext.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selectedCategory.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    val channels = vm.channels.collectAsLazyPagingItems()
    val scope by vm.scope.collectAsStateWithLifecycle()
    val playlistFilter by vm.playlistFilter.collectAsStateWithLifecycle()
    val showSource by vm.showSource.collectAsStateWithLifecycle()
    val sort by vm.categorySort.collectAsStateWithLifecycle()
    val options = remember(categories, scope) {
        categoryOptions(ContentKind.LIVE, categories, if (categories.isEmpty()) scope.playlists.sumOf { it.liveCount } else categories.sumOf { it.itemCount })
    }
    val categoryName = options.firstOrNull { it.id == selected }?.name ?: "All channels"
    // The guide is the TV's main view; the plain list is one press away.
    var listView by rememberSaveable { mutableStateOf(false) }
    val compact by vm.tvGuideCompact.collectAsStateWithLifecycle()
    // A live channel left with Back plays on in the top corner of the guide.
    val playback = context.container.tvPlayback
    val mini = playback.mini.takeIf { !listView }
    val openMini = { m: MiniPlayback -> context.startActivity(m.request.toIntent(context)) }
    // It only plays while the guide is on screen: not in the list, other screens, or behind other apps.
    LaunchedEffect(listView) { if (listView) playback.stopMini() }
    // The guide takes the side menu rail's width too; Back still opens the menu.
    val fullWidth = !listView && session.active != null
    LaunchedEffect(fullWidth) { onFullWidth(fullWidth) }
    DisposableEffect(Unit) { onDispose { onFullWidth(false) } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { playback.stopMini() }
    DisposableEffect(playback) { onDispose { playback.stopMini() } }
    var showCategories by remember { mutableStateOf(false) }
    val categoryColumn = remember { TvGuideCategoriesState() }
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
    // Fixed while the screen is open, so the grid doesn't jump as the clock moves; re-anchored
    // on the current time when the screen comes back after a while.
    var anchor by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var backToNow by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        val t = System.currentTimeMillis()
        if (t - anchor > 30 * 60_000L) {
            backToNow = true
            anchor = t
        }
    }
    val window = remember(days, version, anchor) { GuideWindow.around(anchor, days) }
    val guide = rememberTvGuideState(window)
    LaunchedEffect(window) {
        if (guide.window != window) guide.moveTo(window, System.currentTimeMillis(), backToNow)
        backToNow = false
    }
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
        // The guide's categories are its left-hand column; the list has a button for its panel.
        if (listView) TvActionButton(categoryName, onClick = { showCategories = true }, icon = Icons.Filled.Category)
        // Icons only, leaving the width to the programme details.
        if (!listView) {
            TvIconButton(
                if (compact) Icons.Filled.UnfoldMore else Icons.Filled.UnfoldLess,
                if (compact) "Expand" else "Compact",
                onClick = { vm.setTvGuideCompact(!compact) },
            )
        }
        TvIconButton(
            if (listView) Icons.Outlined.CalendarViewWeek else Icons.AutoMirrored.Filled.ViewList,
            if (listView) "Guide" else "List",
            onClick = { listView = !listView },
        )
    }

    Column(Modifier.fillMaxSize()) {
        if (listView) {
            TvHeader("Live TV", subtitle = scope.label, actions = { headerActions() })
        } else {
            val focusedRow = channels.itemCount.takeIf { it > 0 }?.let { channels.peek(guide.row.coerceIn(0, it - 1)) }
            GuideDetails(
                mini = mini,
                onOpenMini = openMini,
                row = focusedRow,
                programme = guide.focusedProgramme(focusedRow),
                source = focusedRow?.takeIf { showSource }?.let { vm.playlistOf(it)?.name },
                now = now,
                canReplay = { r, p -> vm.canReplay(r, p) },
                isRecording = { r, p -> vm.canRecord(r) && vm.recordingFor(r, p, recordings) != null },
                actions = headerActions,
            )
        }
        val refreshing = channels.loadState.refresh is LoadState.Loading
        val body = Modifier.weight(1f).fillMaxWidth()
        val placeholder: (@Composable (Modifier) -> Unit)? = when {
            channels.itemCount == 0 && refreshing -> { m -> Box(m, Alignment.Center) { CircularProgressIndicator() } }
            channels.itemCount == 0 && selected == Categories.FAVORITES -> { m ->
                TvEmptyState(
                    Icons.Filled.FavoriteBorder,
                    "No favourite channels",
                    "Hold OK on a channel and choose Add to favourites to pin it here.",
                    m,
                )
            }
            channels.itemCount == 0 -> { m ->
                TvEmptyState(
                    Icons.Filled.LiveTv,
                    if (session.activeProgress != null) "Loading channels…" else "No channels",
                    session.activeProgress?.message
                        ?: if (scope.combined) "These playlists have no live channels in this category." else "This playlist has no live channels in this category.",
                    m,
                )
            }
            else -> null
        }
        if (listView) {
            placeholder?.invoke(body) ?: TvChannelList(
                channels = channels,
                sourceOf = { row -> if (scope.combined) vm.playlistOf(row)?.name else null },
                onPlay = playRow,
                onOptions = vm::showSchedule,
                onLeftEdge = { showCategories = true },
                nowPlaying = vm::nowPlaying,
                modifier = body.focusRequester(contentFocus),
            )
        } else {
            // The guide, with its categories as a column beside the channels.
            Row(body.padding(start = GuideEdge, end = TvSpacing.screenEnd, bottom = 12.dp)) {
                TvGuideCategories(
                    state = categoryColumn,
                    options = options,
                    selected = selected,
                    onSelect = {
                        vm.selectCategory(it)
                        categoryColumn.open = false
                        runCatching { contentFocus.requestFocus() }
                    },
                    onClose = { runCatching { contentFocus.requestFocus() } },
                    modifier = Modifier.padding(end = 8.dp),
                    playlists = playlistFilter,
                    onSelectPlaylists = {
                        vm.selectPlaylists(it)
                        categoryColumn.open = false
                        runCatching { contentFocus.requestFocus() }
                    },
                    sort = sort,
                    onSortChange = vm::setCategorySort,
                )
                val lanes = Modifier.weight(1f).fillMaxHeight()
                placeholder?.invoke(lanes) ?: TvGuideGrid(
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
                    onLeftEdge = { categoryColumn.open() },
                    focusRequester = contentFocus,
                    modifier = lanes,
                    compact = compact,
                )
            }
            // An empty category leaves nothing else to focus: open the column to pick another.
            val empty = channels.itemCount == 0 && !refreshing
            LaunchedEffect(empty) { if (empty) categoryColumn.open() }
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
            playlists = playlistFilter,
            onSelectPlaylists = {
                vm.selectPlaylists(it)
                showCategories = false
            },
            sort = sort,
            onSortChange = vm::setCategorySort,
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

/** With the side menu's rail hidden, the guide keeps the same TV-safe margin on the left as on the right. */
private val GuideEdge = TvSpacing.screenEnd

/** The top of the guide: what the highlight is on, with the screen's actions and the clock. */
@Composable
private fun GuideDetails(
    mini: MiniPlayback?,
    onOpenMini: (MiniPlayback) -> Unit,
    row: ChannelRow?,
    programme: ProgrammeEntity?,
    /** The playlist the channel is from, when there's more than one. */
    source: String?,
    now: Long,
    canReplay: (ChannelRow, ProgrammeEntity) -> Boolean,
    isRecording: (ChannelRow, ProgrammeEntity) -> Boolean,
    actions: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .padding(start = GuideEdge, end = TvSpacing.screenEnd, top = TvSpacing.screenTop, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (mini != null) TvMiniPlayer(mini, onOpen = { onOpenMini(mini) })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (row != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        row.stream.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = TvPalette.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    source?.let { TvSourceLabel(it) }
                }
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
    /** The playlist a channel is from, when the list mixes playlists. */
    sourceOf: (ChannelRow) -> String?,
    onPlay: (ChannelRow) -> Unit,
    onOptions: (ChannelRow) -> Unit,
    onLeftEdge: () -> Unit,
    /** What's on a channel now, once the programme it was loaded with has ended. */
    nowPlaying: suspend (ChannelRow) -> NowPlaying?,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val now = rememberMinuteClock()
    LazyColumn(
        state = listState,
        modifier = modifier
            .focusRestorer()
            .fasterWhenHeld(listState)
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
        items(channels.itemCount, key = channels.itemKey { "${it.stream.playlistId}:${it.stream.kind}:${it.stream.streamId}" }) { index ->
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
                            s.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = run {
                        val source = sourceOf(row)
                        val playing = rememberNowPlaying(row, now, nowPlaying)
                        if (playing?.nowTitle == null && source == null) return@run null
                        {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                playing?.nowTitle?.let { title ->
                                    val start = playing.nowStart
                                    val stop = playing.nowStop
                                    val time = if (start != null && stop != null) "${formatTime(start)} – ${formatTime(stop)}  ·  " else ""
                                    Text("$time$title", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                }
                                source?.let { TvSourceLabel(it) }
                            }
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
