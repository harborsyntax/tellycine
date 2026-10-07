package app.mosaic.ui.live

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import app.mosaic.data.db.ChannelRow
import app.mosaic.data.db.ProgrammeEntity
import app.mosaic.ui.components.Artwork
import app.mosaic.ui.components.formatDay
import app.mosaic.ui.components.formatTime
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt

private val CHANNEL_WIDTH = 156.dp
private val HOUR_WIDTH = 240.dp
private val ROW_HEIGHT = 64.dp
private val HEADER_HEIGHT = 40.dp
private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val SLOT = 30 * MINUTE

/** The span the grid covers: a couple of hours back (for catch-up) to the end of the stored guide. */
data class GuideWindow(val startMs: Long, val endMs: Long) {
    companion object {
        fun around(now: Long, days: Int): GuideWindow {
            val start = floorToSlot(now) - 2 * HOUR
            return GuideWindow(start, floorToSlot(now) + days.coerceAtLeast(1) * 24 * HOUR)
        }
    }
}

private fun floorToSlot(ms: Long) = ms - Math.floorMod(ms, SLOT)

/**
 * A traditional TV guide: channels down the side, time across the top, programmes as blocks.
 * Every row scrolls sideways together; a line marks the current time.
 */
@Composable
fun GuideGrid(
    channels: LazyPagingItems<ChannelRow>,
    window: GuideWindow,
    now: Long,
    version: Any?,
    loadProgrammes: suspend (ChannelRow) -> List<ProgrammeEntity>,
    canReplay: (ChannelRow, ProgrammeEntity) -> Boolean,
    isRecording: (ChannelRow, ProgrammeEntity) -> Boolean,
    onPlay: (ChannelRow) -> Unit,
    onProgramme: (ChannelRow, ProgrammeEntity) -> Unit,
    onLongPress: (ChannelRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val hourPx = with(density) { HOUR_WIDTH.toPx() }
    val pxPerMs = hourPx / HOUR
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier.fillMaxSize()) {
        val laneWidthPx = with(density) { (maxWidth - CHANNEL_WIDTH).toPx() }.coerceAtLeast(1f)
        val maxScroll = ((window.endMs - window.startMs) * pxPerMs - laneWidthPx).coerceAtLeast(0f)
        fun nowOffset() = ((floorToSlot(System.currentTimeMillis()) - window.startMs) * pxPerMs).coerceIn(0f, maxScroll)

        // Opens with the current half hour at the left edge.
        var offset by remember(window) { mutableFloatStateOf(nowOffset()) }
        val scrollState = rememberScrollableState { delta ->
            val old = offset
            offset = (old - delta).coerceIn(0f, maxScroll)
            old - offset
        }
        val readOffset = { offset }
        // Rows only compose programmes near the visible part, re-evaluated per hour scrolled.
        val firstHour by remember(hourPx, window) { derivedStateOf { (offset / hourPx).toInt() } }
        val visibleFrom = window.startMs + (firstHour - 1) * HOUR
        val visibleTo = window.startMs + (firstHour + 2) * HOUR + (laneWidthPx / pxPerMs).toLong()

        Box(Modifier.fillMaxSize().scrollable(scrollState, Orientation.Horizontal)) {
            Column(Modifier.fillMaxSize()) {
                TimeHeader(window, pxPerMs, readOffset, visibleFrom, visibleTo) {
                    scope.launch { scrollState.animateScrollBy(offset - nowOffset()) }
                }
                LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(channels.itemCount, key = channels.itemKey { "${it.stream.kind}:${it.stream.streamId}" }) { index ->
                        val row = channels[index]
                        if (row == null) {
                            Box(Modifier.fillMaxWidth().height(ROW_HEIGHT))
                        } else {
                            GuideRow(
                                row = row,
                                window = window,
                                pxPerMs = pxPerMs,
                                offset = readOffset,
                                visibleFrom = visibleFrom,
                                visibleTo = visibleTo,
                                now = now,
                                version = version,
                                loadProgrammes = loadProgrammes,
                                canReplay = canReplay,
                                isRecording = isRecording,
                                onPlay = onPlay,
                                onProgramme = onProgramme,
                                onLongPress = onLongPress,
                            )
                        }
                    }
                }
            }
            NowLine(window, pxPerMs, readOffset, now, Modifier.padding(start = CHANNEL_WIDTH).fillMaxSize())
        }
    }
}

@Composable
private fun TimeHeader(
    window: GuideWindow,
    pxPerMs: Float,
    offset: () -> Float,
    visibleFrom: Long,
    visibleTo: Long,
    onNow: () -> Unit,
) {
    val labelPad = with(LocalDensity.current) { 8.dp.roundToPx() }
    Row(Modifier.fillMaxWidth().height(HEADER_HEIGHT)) {
        Box(Modifier.width(CHANNEL_WIDTH).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            TextButton(onClick = onNow, modifier = Modifier.padding(start = 4.dp)) { Text("Now") }
        }
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            var t = maxOf(floorToSlot(visibleFrom), window.startMs)
            while (t < minOf(visibleTo, window.endMs)) {
                val x = (t - window.startMs) * pxPerMs
                val label = if (isMidnight(t)) formatDay(t) else formatTime(t)
                key(t) {
                    Box(
                        Modifier
                            .offset { IntOffset((x - offset()).roundToInt(), 0) }
                            .width(1.dp)
                            .height(10.dp)
                            .align(Alignment.BottomStart)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isMidnight(t)) FontWeight.SemiBold else null,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset { IntOffset((x - offset()).roundToInt() + labelPad, 0) },
                    )
                }
                t += SLOT
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GuideRow(
    row: ChannelRow,
    window: GuideWindow,
    pxPerMs: Float,
    offset: () -> Float,
    visibleFrom: Long,
    visibleTo: Long,
    now: Long,
    version: Any?,
    loadProgrammes: suspend (ChannelRow) -> List<ProgrammeEntity>,
    canReplay: (ChannelRow, ProgrammeEntity) -> Boolean,
    isRecording: (ChannelRow, ProgrammeEntity) -> Boolean,
    onPlay: (ChannelRow) -> Unit,
    onProgramme: (ChannelRow, ProgrammeEntity) -> Unit,
    onLongPress: (ChannelRow) -> Unit,
) {
    val programmes by produceState<List<ProgrammeEntity>?>(null, row.stream.playlistId, row.stream.epgChannelId, window, version) {
        value = loadProgrammes(row)
    }
    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT).padding(vertical = 2.dp)) {
        ChannelCell(row, onClick = { onPlay(row) }, onLongClick = { onLongPress(row) })
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            val list = programmes
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyLane(row) { onPlay(row) }
                else -> list.forEach { p ->
                    if (p.stopMs > visibleFrom && p.startMs < visibleTo) {
                        key(p.id) {
                            val start = maxOf(p.startMs, window.startMs)
                            val stop = minOf(p.stopMs, window.endMs)
                            if (stop > start) {
                                ProgrammeCell(
                                    programme = p,
                                    xPx = (start - window.startMs) * pxPerMs,
                                    widthPx = (stop - start) * pxPerMs,
                                    offset = offset,
                                    now = now,
                                    replayable = p.startMs < now && canReplay(row, p),
                                    recording = p.stopMs > now && isRecording(row, p),
                                    onClick = { onProgramme(row, p) },
                                    onLongClick = { onLongPress(row) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelCell(row: ChannelRow, onClick: () -> Unit, onLongClick: () -> Unit) {
    val s = row.stream
    Row(
        Modifier
            .width(CHANNEL_WIDTH)
            .fillMaxHeight()
            .padding(start = 8.dp, end = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Artwork(
            url = s.logo,
            fallbackText = s.name,
            contentScale = ContentScale.Fit,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(width = 44.dp, height = 32.dp),
        )
        Column {
            s.number?.let {
                Text("$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(s.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProgrammeCell(
    programme: ProgrammeEntity,
    xPx: Float,
    widthPx: Float,
    offset: () -> Float,
    now: Long,
    replayable: Boolean,
    recording: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val density = LocalDensity.current
    val onNow = programme.startMs <= now && programme.stopMs > now
    val past = programme.stopMs <= now
    val colors = MaterialTheme.colorScheme
    val (background, content) = when {
        onNow -> colors.secondaryContainer to colors.onSecondaryContainer
        past -> colors.surfaceContainerLow to colors.onSurfaceVariant
        else -> colors.surfaceContainerHigh to colors.onSurface
    }
    // Keeps the title in view while a programme that started earlier is partly scrolled past.
    val keepVisiblePx = with(density) { 72.dp.toPx() }
    Box(
        Modifier
            .offset { IntOffset((xPx - offset()).roundToInt(), 0) }
            .width(with(density) { widthPx.toDp() })
            .fillMaxHeight()
            .padding(horizontal = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Column(
            Modifier.offset {
                IntOffset((offset() - xPx).coerceIn(0f, (widthPx - keepVisiblePx).coerceAtLeast(0f)).roundToInt(), 0)
            },
        ) {
            Text(
                programme.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (onNow) FontWeight.SemiBold else null,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (recording) Box(Modifier.size(8.dp).clip(CircleShape).background(colors.error))
                if (replayable && !onNow) Icon(Icons.Filled.Replay, "Catch-up available", Modifier.size(12.dp), tint = colors.primary)
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
private fun EmptyLane(row: ChannelRow, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            row.nowTitle ?: "No guide information",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val color = MaterialTheme.colorScheme.primary
    Box(modifier.clipToBounds()) {
        Box(
            Modifier
                .offset { IntOffset((x - offset() - half).roundToInt(), 0) }
                .width(2.dp)
                .fillMaxHeight()
                .background(color)
        )
        Box(
            Modifier
                .offset { IntOffset((x - offset() - half * 4).roundToInt(), 0) }
                .padding(top = HEADER_HEIGHT - 8.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

private fun isMidnight(ms: Long): Boolean {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return c.get(Calendar.HOUR_OF_DAY) == 0 && c.get(Calendar.MINUTE) == 0
}
