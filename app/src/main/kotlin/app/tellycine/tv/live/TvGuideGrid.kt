package app.tellycine.tv.live

import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_CHANNEL_DOWN
import android.view.KeyEvent.KEYCODE_CHANNEL_UP
import android.view.KeyEvent.KEYCODE_DPAD_CENTER
import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_LEFT
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import android.view.KeyEvent.KEYCODE_DPAD_UP
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.KeyEvent.KEYCODE_MENU
import android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
import android.view.KeyEvent.KEYCODE_PAGE_DOWN
import android.view.KeyEvent.KEYCODE_PAGE_UP
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.tellycine.data.db.ChannelRow
import app.tellycine.data.db.ProgrammeEntity
import app.tellycine.tv.TvPalette
import app.tellycine.ui.components.Artwork
import app.tellycine.ui.components.formatTime
import app.tellycine.ui.live.GuideWindow
import java.util.Calendar
import kotlin.math.roundToInt

private val CHANNEL_WIDTH = 220.dp
private val HOUR_WIDTH = 300.dp
private val ROW_HEIGHT = 56.dp
/** Compact rows fit about half as many channels again on screen. */
private val COMPACT_ROW_HEIGHT = 36.dp
private val HEADER_HEIGHT = 32.dp
private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val SLOT = 30 * MINUTE

private fun floorToSlot(ms: Long) = ms - Math.floorMod(ms, SLOT)

private fun rowKey(row: ChannelRow) = "${row.stream.kind}:${row.stream.streamId}"

/**
 * Where the guide's focus is: a channel row, and a moment in time on it (the programme airing
 * then is highlighted) or the channel itself. Kept by the Live TV screen so it can describe the
 * focused programme above the grid.
 */
@Stable
class TvGuideState(window: GuideWindow, now: Long) {
    /** The span of time the grid covers; changes when the guide is reloaded or the screen comes back much later. */
    var window by mutableStateOf(window)
        private set
    var row by mutableIntStateOf(0)
    var timeMs by mutableLongStateOf(now.coerceIn(window.startMs, window.endMs - 1))
    var onChannel by mutableStateOf(false)
    /** Time at the left edge of the programme lanes. */
    var viewStart by mutableLongStateOf(floorToSlot(now).coerceIn(window.startMs, window.endMs))

    /** Programmes of the rows on screen, by row key. */
    val programmes = mutableStateMapOf<String, List<ProgrammeEntity>>()
    val listState = LazyListState()

    fun programmesOf(row: ChannelRow?): List<ProgrammeEntity>? = row?.let { programmes[rowKey(it)] }

    fun focusedProgramme(row: ChannelRow?): ProgrammeEntity? =
        if (onChannel) null else programmesOf(row)?.firstOrNull { it.startMs <= timeMs && it.stopMs > timeMs }

    fun reset(now: Long) {
        row = 0
        onChannel = false
        timeMs = now.coerceIn(window.startMs, window.endMs - 1)
        viewStart = floorToSlot(now).coerceIn(window.startMs, window.endMs)
    }

    /**
     * Moves to a new [window], keeping the highlighted channel. The highlighted time is kept if
     * the new window still covers it, unless [backToNow] (returning to the screen much later).
     */
    fun moveTo(window: GuideWindow, now: Long, backToNow: Boolean) {
        this.window = window
        if (backToNow || timeMs !in window.startMs until window.endMs) {
            timeMs = now.coerceIn(window.startMs, window.endMs - 1)
            viewStart = floorToSlot(now).coerceIn(window.startMs, window.endMs)
        } else {
            viewStart = viewStart.coerceIn(window.startMs, window.endMs)
        }
    }
}

/** Guide state that survives the guide being reloaded (see [TvGuideState.moveTo]). */
@Composable
fun rememberTvGuideState(window: GuideWindow): TvGuideState = remember { TvGuideState(window, System.currentTimeMillis()) }

/**
 * A traditional TV guide driven by the remote. The grid takes focus as a whole and moves a
 * highlight itself: left/right step through programmes (scrolling time), up/down change channel
 * keeping the time, OK plays or replays, and holding OK opens the channel's options. Left from the
 * channel column calls [onLeftEdge] (the Live TV screen opens its categories there).
 */
@Composable
fun TvGuideGrid(
    state: TvGuideState,
    channels: LazyPagingItems<ChannelRow>,
    now: Long,
    version: Any?,
    loadProgrammes: suspend (ChannelRow) -> List<ProgrammeEntity>,
    canReplay: (ChannelRow, ProgrammeEntity) -> Boolean,
    isRecording: (ChannelRow, ProgrammeEntity) -> Boolean,
    onPlay: (ChannelRow) -> Unit,
    onProgramme: (ChannelRow, ProgrammeEntity) -> Unit,
    onOptions: (ChannelRow) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    /** Left from the channel column; when null, focus moves on to the side menu. */
    onLeftEdge: (() -> Unit)? = null,
    /** Shorter rows, with one line per channel and programme, so more channels fit. */
    compact: Boolean = false,
) {
    val rowHeight = if (compact) COMPACT_ROW_HEIGHT else ROW_HEIGHT
    val window = state.window
    val density = LocalDensity.current
    val pxPerMs = with(density) { HOUR_WIDTH.toPx() } / HOUR
    var hasFocus by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val laneWidthPx = with(density) { (maxWidth - CHANNEL_WIDTH).toPx() }.coerceAtLeast(1f)
        val laneMs = (laneWidthPx / pxPerMs).toLong()
        val maxViewStart = (window.endMs - laneMs).coerceAtLeast(window.startMs)
        val offset by animateFloatAsState(((state.viewStart - window.startMs) * pxPerMs), tween(160), label = "guide-scroll")
        val readOffset = { offset }

        fun ensureTimeVisible() {
            val t = state.timeMs
            var start = state.viewStart
            if (t < start) start = floorToSlot(t)
            else if (t > start + laneMs - 45 * MINUTE) start = floorToSlot(t) - SLOT
            state.viewStart = start.coerceIn(window.startMs, maxViewStart)
        }

        fun currentRow() = channels.itemCount.takeIf { it > 0 }?.let { channels.peek(state.row.coerceIn(0, it - 1)) }

        fun moveVertical(delta: Int): Boolean {
            val count = channels.itemCount
            if (count == 0) return false
            val target = (state.row + delta).coerceIn(0, count - 1)
            if (target == state.row) return delta > 0 // At the top, let focus move up to the header.
            state.row = target
            return true
        }

        fun moveRight(): Boolean {
            if (state.onChannel) {
                state.onChannel = false
                state.timeMs = state.timeMs.coerceIn(state.viewStart, state.viewStart + laneMs - 1)
                return true
            }
            val list = state.programmesOf(currentRow())
            val current = list?.firstOrNull { it.startMs <= state.timeMs && it.stopMs > state.timeMs }
            val next = list?.firstOrNull { it.startMs >= (current?.stopMs ?: (state.timeMs + 1)) && it.startMs < window.endMs }
            state.timeMs = next?.startMs ?: (floorToSlot(state.timeMs) + SLOT).coerceAtMost(window.endMs - 1)
            ensureTimeVisible()
            return true
        }

        fun moveLeft(): Boolean {
            if (state.onChannel) {
                onLeftEdge?.invoke()
                return onLeftEdge != null
            }
            val list = state.programmesOf(currentRow())
            val current = list?.firstOrNull { it.startMs <= state.timeMs && it.stopMs > state.timeMs }
            val boundary = current?.startMs ?: state.timeMs
            val previous = list?.lastOrNull { it.stopMs <= boundary && it.stopMs > window.startMs }
            val target = when {
                previous != null -> maxOf(previous.startMs, window.startMs)
                boundary - SLOT >= window.startMs -> floorToSlot(boundary - 1)
                else -> null
            }
            if (target == null) {
                state.onChannel = true
            } else {
                state.timeMs = target
                ensureTimeVisible()
            }
            return true
        }

        fun activate() {
            val row = currentRow() ?: return
            val programme = state.focusedProgramme(row)
            if (programme == null) onPlay(row) else onProgramme(row, programme)
        }

        // Rows on screen, for paging with channel up/down.
        fun pageSize() = (state.listState.layoutInfo.visibleItemsInfo.size - 1).coerceAtLeast(1)

        var pressed by remember { mutableStateOf(false) }
        var longPressed by remember { mutableStateOf(false) }

        Column(
            Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .onFocusChanged { hasFocus = it.isFocused }
                .onKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val down = event.type == KeyEventType.KeyDown
                    when (native.keyCode) {
                        KEYCODE_DPAD_CENTER, KEYCODE_ENTER, KEYCODE_NUMPAD_ENTER, KEYCODE_BUTTON_A -> {
                            // Acts on release, so the release can't land in the options panel it opens.
                            when {
                                down && native.repeatCount == 0 -> {
                                    pressed = true
                                    longPressed = false
                                }
                                down -> if (pressed) longPressed = true
                                else -> {
                                    if (pressed) {
                                        if (longPressed) currentRow()?.let(onOptions) else activate()
                                    }
                                    pressed = false
                                    longPressed = false
                                }
                            }
                            true
                        }
                        KEYCODE_MENU -> {
                            if (!down) currentRow()?.let(onOptions)
                            true
                        }
                        KEYCODE_DPAD_UP -> if (down) moveVertical(-1) else false
                        KEYCODE_DPAD_DOWN -> if (down) moveVertical(1) else false
                        KEYCODE_DPAD_LEFT -> if (down) moveLeft() else false
                        KEYCODE_DPAD_RIGHT -> if (down) moveRight() else false
                        KEYCODE_CHANNEL_UP, KEYCODE_PAGE_UP -> {
                            if (down) moveVertical(-pageSize())
                            true
                        }
                        KEYCODE_CHANNEL_DOWN, KEYCODE_PAGE_DOWN -> {
                            if (down) moveVertical(pageSize())
                            true
                        }
                        else -> false
                    }
                }
                .focusable(),
        ) {
            val visibleFrom = state.viewStart - HOUR
            val visibleTo = state.viewStart + laneMs + HOUR
            TimeHeader(window, pxPerMs, readOffset, visibleFrom, visibleTo)
            Box(Modifier.fillMaxSize()) {
                LazyColumn(state = state.listState, userScrollEnabled = false, modifier = Modifier.fillMaxSize()) {
                    items(channels.itemCount, key = channels.itemKey { rowKey(it) }) { index ->
                        val row = channels[index]
                        if (row == null) {
                            Box(Modifier.fillMaxWidth().height(rowHeight))
                        } else {
                            GuideRow(
                                row = row,
                                compact = compact,
                                focused = hasFocus && index == state.row,
                                state = state,
                                pxPerMs = pxPerMs,
                                offset = readOffset,
                                visibleFrom = visibleFrom,
                                visibleTo = visibleTo,
                                now = now,
                                version = version,
                                loadProgrammes = loadProgrammes,
                                canReplay = canReplay,
                                isRecording = isRecording,
                            )
                        }
                    }
                }
                NowLine(window, pxPerMs, readOffset, now, Modifier.padding(start = CHANNEL_WIDTH).fillMaxSize())
            }
        }

        // A shorter list (a favourite removed, a refresh) keeps the highlight on a real row.
        LaunchedEffect(channels.itemCount) {
            val count = channels.itemCount
            if (count > 0 && state.row >= count) state.row = count - 1
        }

        // Keep the focused row on screen, with a row of context above and below.
        LaunchedEffect(state) {
            snapshotFlow { state.row }.collect { row ->
                val visible = state.listState.layoutInfo.visibleItemsInfo
                if (visible.isEmpty()) return@collect
                val first = visible.first().index
                val last = visible.last().index
                when {
                    row <= first -> state.listState.scrollToItem((row - 1).coerceAtLeast(0))
                    row >= last -> state.listState.scrollToItem((row - (visible.size - 2)).coerceAtLeast(0))
                }
            }
        }
    }
}

@Composable
private fun TimeHeader(window: GuideWindow, pxPerMs: Float, offset: () -> Float, visibleFrom: Long, visibleTo: Long) {
    Row(Modifier.fillMaxWidth().height(HEADER_HEIGHT)) {
        Box(Modifier.width(CHANNEL_WIDTH))
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            var t = maxOf(window.startMs, floorToSlot(visibleFrom))
            while (t < minOf(window.endMs, visibleTo)) {
                val x = (t - window.startMs) * pxPerMs
                key(t) {
                    Text(
                        if (isMidnight(t)) "${app.tellycine.ui.components.formatDay(t)} ${formatTime(t)}" else formatTime(t),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier
                            .offset { IntOffset((x - offset()).roundToInt(), 0) }
                            .padding(start = 8.dp, top = 6.dp),
                    )
                }
                t += SLOT
            }
        }
    }
}

@Composable
private fun GuideRow(
    row: ChannelRow,
    compact: Boolean,
    focused: Boolean,
    state: TvGuideState,
    pxPerMs: Float,
    offset: () -> Float,
    visibleFrom: Long,
    visibleTo: Long,
    now: Long,
    version: Any?,
    loadProgrammes: suspend (ChannelRow) -> List<ProgrammeEntity>,
    canReplay: (ChannelRow, ProgrammeEntity) -> Boolean,
    isRecording: (ChannelRow, ProgrammeEntity) -> Boolean,
) {
    val window = state.window
    val key = rowKey(row)
    LaunchedEffect(key, version, window) {
        state.programmes[key] = loadProgrammes(row)
    }
    // Rows that scroll away let go of their programmes.
    DisposableEffect(key) {
        onDispose { state.programmes.remove(key) }
    }
    val programmes = state.programmes[key]
    Row(Modifier.fillMaxWidth().height(if (compact) COMPACT_ROW_HEIGHT else ROW_HEIGHT).padding(vertical = 2.dp)) {
        ChannelCell(row, compact, highlighted = focused && state.onChannel)
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            when {
                programmes == null -> Unit
                programmes.isEmpty() -> EmptyLane(row, highlighted = focused && !state.onChannel)
                else -> programmes.forEach { p ->
                    if (p.stopMs > visibleFrom && p.startMs < visibleTo) {
                        key(p.id) {
                            val start = maxOf(p.startMs, window.startMs)
                            val stop = minOf(p.stopMs, window.endMs)
                            if (stop > start) {
                                ProgrammeCell(
                                    programme = p,
                                    compact = compact,
                                    xPx = (start - window.startMs) * pxPerMs,
                                    widthPx = (stop - start) * pxPerMs,
                                    offset = offset,
                                    now = now,
                                    highlighted = focused && !state.onChannel && p.startMs <= state.timeMs && p.stopMs > state.timeMs,
                                    replayable = p.startMs < now && canReplay(row, p),
                                    recording = p.stopMs > now && isRecording(row, p),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val CellShape = RoundedCornerShape(8.dp)

@Composable
private fun ChannelCell(row: ChannelRow, compact: Boolean, highlighted: Boolean) {
    val s = row.stream
    Row(
        Modifier
            .width(CHANNEL_WIDTH)
            .fillMaxHeight()
            .padding(end = 4.dp)
            .clip(CellShape)
            .background(if (highlighted) TvPalette.focus else Color.Transparent)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val content = if (highlighted) Color(0xFF2A1800) else MaterialTheme.colorScheme.onSurface
        s.number?.let {
            Text(
                "$it",
                style = MaterialTheme.typography.labelLarge,
                color = content.copy(alpha = 0.75f),
                maxLines = 1,
                modifier = Modifier.width(36.dp),
            )
        }
        Artwork(
            url = s.logo,
            fallbackText = s.name,
            contentScale = ContentScale.Fit,
            shape = RoundedCornerShape(6.dp),
            modifier = if (compact) Modifier.size(width = 40.dp, height = 26.dp) else Modifier.size(width = 52.dp, height = 36.dp),
        )
        Text(
            s.name,
            style = MaterialTheme.typography.titleSmall,
            color = content,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (row.isFavorite) Icon(Icons.Filled.Favorite, "Favourite", Modifier.size(14.dp), tint = if (highlighted) content else TvPalette.primary)
    }
}

@Composable
private fun ProgrammeCell(
    programme: ProgrammeEntity,
    compact: Boolean,
    xPx: Float,
    widthPx: Float,
    offset: () -> Float,
    now: Long,
    highlighted: Boolean,
    replayable: Boolean,
    recording: Boolean,
) {
    val density = LocalDensity.current
    val onNow = programme.startMs <= now && programme.stopMs > now
    val past = programme.stopMs <= now
    val (background, content) = when {
        highlighted -> TvPalette.focus to Color(0xFF2A1800)
        onNow -> Color(0xFF56442A) to Color(0xFFFADEBC)
        past -> Color(0xFF1A1611) to TvPalette.onSurfaceVariant
        else -> TvPalette.surfaceHigh to TvPalette.onSurface
    }
    // Keeps the title in view while a programme that started earlier is partly scrolled past.
    val keepVisiblePx = with(density) { 80.dp.toPx() }
    val minVisiblePx = with(density) { 48.dp.toPx() }
    Box(
        Modifier
            .offset { IntOffset((xPx - offset()).roundToInt(), 0) }
            .width(with(density) { widthPx.toDp() })
            .fillMaxHeight()
            .padding(horizontal = 1.dp)
            .clip(CellShape)
            .background(background)
            .then(if (highlighted) Modifier.border(2.dp, TvPalette.primary, CellShape) else Modifier)
            .padding(horizontal = 10.dp, vertical = if (compact) 0.dp else 6.dp),
        contentAlignment = if (compact) Alignment.CenterStart else Alignment.TopStart,
    ) {
        Column(
            Modifier
                .offset {
                    IntOffset((offset() - xPx).coerceIn(0f, (widthPx - keepVisiblePx).coerceAtLeast(0f)).roundToInt(), 0)
                }
                .graphicsLayer { alpha = if (xPx + widthPx - offset() < minVisiblePx) 0f else 1f },
        ) {
            Text(
                programme.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (onNow || highlighted) FontWeight.SemiBold else null,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Compact rows have room for the title only; the details above the guide give the times.
            if (!compact) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (recording) Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF5449)))
                if (replayable && !onNow) Icon(Icons.Filled.Replay, "Catch-up available", Modifier.size(12.dp), tint = content)
                Text(
                    "${formatTime(programme.startMs)} – ${formatTime(programme.stopMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.8f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun EmptyLane(row: ChannelRow, highlighted: Boolean) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 1.dp)
            .clip(CellShape)
            .background(if (highlighted) TvPalette.focus else Color(0xFF1A1611))
            .then(if (highlighted) Modifier.border(2.dp, TvPalette.primary, CellShape) else Modifier)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            row.nowTitle ?: "No guide information",
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlighted) Color(0xFF2A1800) else TvPalette.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A vertical line at the current time, drawn over the programme lanes. */
@Composable
private fun NowLine(window: GuideWindow, pxPerMs: Float, offset: () -> Float, now: Long, modifier: Modifier) {
    if (now !in window.startMs until window.endMs) return
    val x = (now - window.startMs) * pxPerMs
    val half = with(LocalDensity.current) { 1.dp.toPx() }
    Box(modifier.clipToBounds()) {
        Box(
            Modifier
                .offset { IntOffset((x - offset() - half).roundToInt(), 0) }
                .width(2.dp)
                .fillMaxHeight()
                .background(TvPalette.primary)
        )
    }
}

private fun isMidnight(ms: Long): Boolean {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return c.get(Calendar.HOUR_OF_DAY) == 0 && c.get(Calendar.MINUTE) == 0
}
