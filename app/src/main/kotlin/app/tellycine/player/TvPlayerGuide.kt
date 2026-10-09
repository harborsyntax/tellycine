package app.tellycine.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.repo.Categories
import app.tellycine.tv.TvPalette
import app.tellycine.tv.components.RequestFocusOnStart
import app.tellycine.tv.live.TvGuideGrid
import app.tellycine.tv.live.rememberTvGuideState
import app.tellycine.ui.components.formatTime
import app.tellycine.ui.components.tellycineViewModel
import app.tellycine.ui.live.GuideWindow
import app.tellycine.ui.live.LiveViewModel
import kotlinx.coroutines.delay

/**
 * The guide over the lower part of the picture, so the channel keeps playing above it. Opens on
 * the channel playing, in its category. OK on a programme on now changes channel, on a past one
 * replays it when the provider keeps an archive; Back closes it.
 */
@Composable
fun TvPlayerGuide(
    request: PlayRequest,
    playerChannels: List<StreamEntity>,
    onPlayChannel: (StreamEntity) -> Unit,
    onPlay: (PlayRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = tellycineViewModel { c, _ -> LiveViewModel(c) }
    LaunchedEffect(request.categoryId) { vm.selectCategory(request.categoryId ?: Categories.ALL) }
    val channels = vm.channels.collectAsLazyPagingItems()
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

    // Starts on the channel playing: found among the rows loaded so far, or by its place in the
    // player's channel list, which is in the same order.
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(channels.itemCount) {
        val count = channels.itemCount
        if (placed || count == 0) return@LaunchedEffect
        val index = (0 until count).firstOrNull { channels.peek(it)?.stream?.streamId == request.streamId }
            ?: playerChannels.indexOfFirst { it.streamId == request.streamId }.takeIf { it in 0 until count }
            ?: return@LaunchedEffect
        guide.row = index
        guide.listState.scrollToItem((index - 1).coerceAtLeast(0))
        placed = true
    }

    val focus = remember { FocusRequester() }
    Column(
        modifier
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.85f))))
            .padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 16.dp),
    ) {
        val row = channels.itemCount.takeIf { it > 0 }?.let { channels.peek(guide.row.coerceIn(0, it - 1)) }
        val programme = guide.focusedProgramme(row)
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
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        TvGuideGrid(
            state = guide,
            channels = channels,
            now = now,
            version = version,
            loadProgrammes = { r -> vm.guideProgrammes(r, window.startMs, window.endMs) },
            canReplay = vm::canReplay,
            isRecording = { r, p -> vm.canRecord(r) && vm.recordingFor(r, p, recordings) != null },
            onPlay = { onPlayChannel(it.stream) },
            onProgramme = { r, p ->
                val t = System.currentTimeMillis()
                val replay = if (p.stopMs <= t && vm.canReplay(r, p)) vm.replayRequest(r, p) else null
                if (replay != null) onPlay(replay) else onPlayChannel(r.stream)
            },
            // The channel's options live in the main guide.
            onOptions = { onPlayChannel(it.stream) },
            focusRequester = focus,
            // Nothing to the left of the guide here.
            onLeftEdge = {},
            compact = compact,
            translucent = true,
            modifier = Modifier.weight(1f),
        )
    }
    RequestFocusOnStart(focus)
}
