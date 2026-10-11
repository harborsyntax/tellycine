package app.tellycine.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.tellycine.container
import app.tellycine.core.ContentKind
import app.tellycine.data.db.ChannelRow
import app.tellycine.data.repo.Categories
import app.tellycine.data.settings.AppSettings
import app.tellycine.tv.TvPalette
import app.tellycine.tv.components.RequestFocusOnStart
import app.tellycine.tv.components.TvSourceLabel
import app.tellycine.tv.live.TvGuideCategories
import app.tellycine.tv.live.TvGuideCategoriesState
import app.tellycine.tv.live.TvGuideGrid
import app.tellycine.tv.live.rememberTvGuideState
import app.tellycine.ui.components.categoryOptions
import app.tellycine.ui.components.formatTime
import app.tellycine.ui.components.tellycineViewModel
import app.tellycine.ui.live.GuideStart
import app.tellycine.ui.live.GuideWindow
import app.tellycine.ui.live.LiveViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

/** The playlists and category the guide over the player shows. */
private data class GuideView(val playlistIds: List<Long>, val category: String)

/**
 * The guide over the lower part of the picture, so the channel keeps playing above it. Like the
 * main guide, it shows Live TV's playlists (all of them together, or one), its categories are in
 * Live TV's order, and both can be changed from the categories column, opened with Left from the
 * channel column. Opens on the channel playing, in its category; when Live TV shows another
 * playlist than the channel's, it opens in the channel's own until another is picked. OK on a
 * programme on now changes channel, on a past one replays it when the provider keeps an archive;
 * Back closes it.
 */
@Composable
fun TvPlayerGuide(
    request: PlayRequest,
    /** Plays a channel (a live request, with the guide's category) or a past programme. */
    onPlay: (PlayRequest) -> Unit,
    modifier: Modifier = Modifier,
    /** What picking a channel will do, at the end of the header line. */
    hint: String? = null,
) {
    val vm = tellycineViewModel { c, _ -> LiveViewModel(c) }
    val content = LocalContext.current.container.content
    val scope by vm.scope.collectAsStateWithLifecycle()
    val filter by vm.playlistFilter.collectAsStateWithLifecycle()
    val sort by vm.categorySort.collectAsStateWithLifecycle()
    val showSource by vm.showSource.collectAsStateWithLifecycle()

    // A playlist picked here (or "All playlists"), which also becomes Live TV's.
    var picked by remember { mutableStateOf<Long?>(null) }
    val ids = when (val choice = picked) {
        AppSettings.ALL_PLAYLISTS -> filter?.options.orEmpty().map { it.id }.filter { it != AppSettings.ALL_PLAYLISTS }
        null -> scope.ids.takeIf { request.playlistId in it || it.isEmpty() } ?: listOf(request.playlistId)
        else -> listOf(choice)
    }
    val playlists = filter?.copy(selected = if (ids.size > 1) AppSettings.ALL_PLAYLISTS else ids.firstOrNull() ?: AppSettings.ALL_PLAYLISTS)

    // Each opening (and each change of playlists) starts on the channel playing, in its category,
    // whatever was browsed last time; found before any rows show, so the guide never places
    // itself on rows of another category while one loads.
    var start by remember { mutableStateOf<GuideStart?>(null) }
    var view by remember { mutableStateOf<GuideView?>(null) }
    LaunchedEffect(ids) {
        if (ids.isEmpty()) return@LaunchedEffect
        val s = vm.guideStart(request, ids)
        start = s
        view = GuideView(ids, s.category)
    }
    val shownIds = view?.playlistIds.orEmpty()
    val selected = view?.category ?: Categories.ALL
    // A fresh list for each category and each opening.
    val channels = remember(view) {
        view?.let { content.channels(it.playlistIds, it.category, now = { System.currentTimeMillis() }) } ?: flowOf(PagingData.empty())
    }.collectAsLazyPagingItems()
    val categories by remember(shownIds) { vm.categoriesOf(shownIds) }.collectAsStateWithLifecycle(emptyList())
    val options = remember(categories) {
        categoryOptions(ContentKind.LIVE, categories, categories.takeIf { it.isNotEmpty() }?.sumOf { it.itemCount })
    }
    val categoryColumn = remember { TvGuideCategoriesState() }
    val days by vm.guideDays.collectAsStateWithLifecycle()
    val version by vm.guideVersion.collectAsStateWithLifecycle()
    val compact by vm.tvGuideCompact.collectAsStateWithLifecycle()
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            value = System.currentTimeMillis()
        }
    }
    val window = remember(days, version) { GuideWindow.around(System.currentTimeMillis(), days) }
    val guide = rememberTvGuideState(window)
    LaunchedEffect(window) {
        if (guide.window != window) guide.moveTo(window, System.currentTimeMillis(), backToNow = false)
    }

    // In the channel's own category, starts on the channel playing; another category starts at the top.
    var placed by remember { mutableStateOf<GuideView?>(null) }
    LaunchedEffect(view, channels.itemCount) {
        val shown = view ?: return@LaunchedEffect
        val count = channels.itemCount
        if (placed == shown || count == 0) return@LaunchedEffect
        val row = start?.takeIf { it.category == shown.category }?.row?.takeIf { it < count }
        if (row == null) {
            guide.reset(System.currentTimeMillis())
            guide.listState.scrollToItem(0)
        } else {
            guide.row = row
            guide.listState.scrollToItem((row - 1).coerceAtLeast(0))
        }
        placed = shown
    }

    val focus = remember { FocusRequester() }
    val backToChannels = {
        categoryColumn.open = false
        runCatching { focus.requestFocus() }
    }
    Column(
        modifier
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.85f))))
            .padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 16.dp),
    ) {
        val row = channels.itemCount.takeIf { it > 0 }?.let { channels.peek(guide.row.coerceIn(0, it - 1)) }
        val programme = guide.focusedProgramme(row)
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(
                    row?.stream?.name,
                    programme?.title,
                    programme?.let { "${formatTime(it.startMs)} – ${formatTime(it.stopMs)}" },
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.titleMedium,
                color = TvPalette.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            sourceOf(vm, row, showSource)?.let { TvSourceLabel(it, Modifier.padding(start = 24.dp)) }
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 24.dp),
                )
            }
        }
        Row(Modifier.weight(1f)) {
            TvGuideCategories(
                state = categoryColumn,
                options = options,
                selected = selected,
                onSelect = { id ->
                    view = GuideView(shownIds, id)
                    backToChannels()
                },
                onClose = { runCatching { focus.requestFocus() } },
                modifier = Modifier.padding(end = 8.dp),
                playlists = playlists,
                onSelectPlaylists = { id ->
                    picked = id
                    vm.selectPlaylists(id)
                    backToChannels()
                },
                sort = sort,
                onSortChange = vm::setCategorySort,
            )
            TvGuideGrid(
                state = guide,
                channels = channels,
                now = now,
                version = version,
                loadProgrammes = { r -> vm.guideProgrammes(r, window.startMs, window.endMs) },
                canReplay = vm::canReplay,
                isRecording = { r, p -> vm.canRecord(r) && vm.recordingFor(r, p, recordings) != null },
                onPlay = { onPlay(vm.playRequest(it, selected)) },
                onProgramme = { r, p ->
                    val t = System.currentTimeMillis()
                    val replay = if (p.stopMs <= t && vm.canReplay(r, p)) vm.replayRequest(r, p) else null
                    onPlay(replay ?: vm.playRequest(r, selected))
                },
                // The channel's options live in the main guide.
                onOptions = { onPlay(vm.playRequest(it, selected)) },
                focusRequester = focus,
                onLeftEdge = { categoryColumn.open() },
                compact = compact,
                translucent = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
    RequestFocusOnStart(focus)
}

/** The playlist [row] is from, when there's more than one playlist. */
private fun sourceOf(vm: LiveViewModel, row: ChannelRow?, showSource: Boolean): String? =
    row?.takeIf { showSource }?.let { vm.playlistOf(it)?.name }
