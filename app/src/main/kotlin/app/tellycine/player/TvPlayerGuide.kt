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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.tellycine.container
import app.tellycine.core.ContentKind
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.repo.Categories
import app.tellycine.tv.TvPalette
import app.tellycine.tv.components.RequestFocusOnStart
import app.tellycine.tv.live.TvGuideCategories
import app.tellycine.tv.live.TvGuideCategoriesState
import app.tellycine.tv.live.TvGuideGrid
import app.tellycine.tv.live.rememberTvGuideState
import app.tellycine.ui.components.categoryOptions
import app.tellycine.ui.components.formatTime
import app.tellycine.ui.components.tellycineViewModel
import app.tellycine.ui.live.GuideWindow
import app.tellycine.ui.live.LiveViewModel
import kotlinx.coroutines.delay

/**
 * The guide over the lower part of the picture, so the channel keeps playing above it. Opens on
 * the channel playing, in its category; the categories are a column to the left of the channels,
 * opened with Left from the channel column. OK on a programme on now changes channel, on a past
 * one replays it when the provider keeps an archive; Back closes it.
 */
@Composable
fun TvPlayerGuide(
    request: PlayRequest,
    playerChannels: List<StreamEntity>,
    /** Plays a channel (a live request, with the guide's category) or a past programme. */
    onPlay: (PlayRequest) -> Unit,
    modifier: Modifier = Modifier,
    /** What picking a channel will do, at the end of the header line. */
    hint: String? = null,
) {
    val vm = tellycineViewModel { c, _ -> LiveViewModel(c) }
    val content = LocalContext.current.container.content
    val startCategory = request.categoryId ?: Categories.ALL
    // Each opening starts in the channel's category, whatever was browsed last time.
    var selected by remember { mutableStateOf(startCategory) }
    // Kept in step for vm.playRequest, which notes the category a channel was picked from.
    LaunchedEffect(selected) { vm.selectCategory(selected) }
    // A fresh list for each category and each opening, so rows of another category never show
    // (and the guide never places itself on them) while a category loads.
    val channels = remember(selected) {
        content.channels(request.playlistId, selected, now = { System.currentTimeMillis() })
    }.collectAsLazyPagingItems()
    // The guide stays in the playing channel's own playlist, as changing channel does.
    val categories by remember(request.playlistId) { content.categories(request.playlistId, ContentKind.LIVE) }
        .collectAsStateWithLifecycle(emptyList())
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

    // In the channel's own category, starts on the channel playing: found among the rows loaded
    // so far, or by its place in the player's channel list, which is in the same order. Another
    // category starts at the top.
    var placedIn by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selected, channels.itemCount) {
        val count = channels.itemCount
        if (placedIn == selected || count == 0) return@LaunchedEffect
        if (selected != startCategory) {
            guide.reset(System.currentTimeMillis())
            guide.listState.scrollToItem(0)
            placedIn = selected
            return@LaunchedEffect
        }
        val index = (0 until count).firstOrNull { channels.peek(it)?.stream?.streamId == request.streamId }
            ?: playerChannels.indexOfFirst { it.streamId == request.streamId }.takeIf { it in 0 until count }
            ?: return@LaunchedEffect
        guide.row = index
        guide.listState.scrollToItem((index - 1).coerceAtLeast(0))
        placedIn = selected
    }

    val focus = remember { FocusRequester() }
    Column(
        modifier
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.85f))))
            .padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 16.dp),
    ) {
        val row = channels.itemCount.takeIf { it > 0 }?.let { channels.peek(guide.row.coerceIn(0, it - 1)) }
        val programme = guide.focusedProgramme(row)
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
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
                    selected = id
                    categoryColumn.open = false
                    runCatching { focus.requestFocus() }
                },
                onClose = { runCatching { focus.requestFocus() } },
                modifier = Modifier.padding(end = 8.dp),
            )
            TvGuideGrid(
                state = guide,
                channels = channels,
                now = now,
                version = version,
                loadProgrammes = { r -> vm.guideProgrammes(r, window.startMs, window.endMs) },
                canReplay = vm::canReplay,
                isRecording = { r, p -> vm.canRecord(r) && vm.recordingFor(r, p, recordings) != null },
                onPlay = { onPlay(vm.playRequest(it)) },
                onProgramme = { r, p ->
                    val t = System.currentTimeMillis()
                    val replay = if (p.stopMs <= t && vm.canReplay(r, p)) vm.replayRequest(r, p) else null
                    onPlay(replay ?: vm.playRequest(r))
                },
                // The channel's options live in the main guide.
                onOptions = { onPlay(vm.playRequest(it)) },
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
