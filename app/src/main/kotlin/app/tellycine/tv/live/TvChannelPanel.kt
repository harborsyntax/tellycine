package app.tellycine.tv.live

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import app.tellycine.core.dispatcharr.DispatcharrSource
import app.tellycine.core.dispatcharr.DvrRecording
import app.tellycine.player.openExternally
import app.tellycine.player.play
import app.tellycine.tv.components.RequestFocusOnStart
import app.tellycine.tv.components.TvActionButton
import app.tellycine.tv.components.TvDialog
import app.tellycine.tv.components.TvSidePanel
import app.tellycine.ui.components.Artwork
import app.tellycine.ui.components.formatDay
import app.tellycine.ui.components.formatTime
import app.tellycine.ui.live.LiveViewModel
import app.tellycine.ui.live.ScheduleState

/**
 * A channel's options and full schedule: watch, replay from the start, favourite, open in another
 * app or show its stream URL, and replay or record individual programmes.
 */
@Composable
fun TvChannelPanel(
    state: ScheduleState,
    vm: LiveViewModel,
    recordings: List<DvrRecording>,
    onShare: (label: String, url: String) -> Unit,
) {
    val context = LocalContext.current
    val row = state.channel
    val canRecord = vm.canRecord(row)
    var favorite by remember(row.stream.streamId) { mutableStateOf(row.isFavorite) }
    val watch = remember { FocusRequester() }
    val now = remember(state.programmes) { System.currentTimeMillis() }
    val current = state.programmes?.firstOrNull { it.startMs <= now && it.stopMs > now }

    TvSidePanel(onDismiss = vm::dismissSchedule, width = 620.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Artwork(
                url = row.stream.logo,
                fallbackText = row.stream.name,
                contentScale = ContentScale.Fit,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(width = 80.dp, height = 56.dp),
            )
            Column {
                Text(
                    listOfNotNull(row.stream.number?.toString(), row.stream.name).joinToString("  "),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.catchup) {
                    val days = row.stream.catchupDays ?: 0
                    Text(
                        "Catch-up available for $days day${if (days == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvActionButton(
                "Watch live",
                onClick = {
                    context.play(vm.playRequest(row))
                    vm.dismissSchedule()
                },
                icon = Icons.Filled.PlayArrow,
                primary = true,
                modifier = Modifier.focusRequester(watch),
            )
            if (current != null && vm.canReplay(row, current)) {
                TvActionButton(
                    "From the start",
                    onClick = {
                        vm.replayRequest(row, current)?.let {
                            context.play(it)
                            vm.dismissSchedule()
                        }
                    },
                    icon = Icons.Filled.Replay,
                )
            }
            TvActionButton(
                if (favorite) "Favourite" else "Add to favourites",
                onClick = {
                    vm.toggleFavorite(row)
                    favorite = !favorite
                },
                icon = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            )
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvActionButton(
                "Open with…",
                onClick = {
                    context.openExternally(vm.playRequest(row), chooser = true)
                    vm.dismissSchedule()
                },
                icon = Icons.AutoMirrored.Filled.OpenInNew,
            )
            TvActionButton(
                if (vm.isDispatcharr()) "Dispatcharr URL" else "Stream URL",
                onClick = { onShare(row.stream.name, row.stream.url) },
                icon = Icons.Filled.Link,
            )
            if (vm.isDispatcharr()) {
                TvActionButton("Source URL", onClick = { vm.shareSourceUrl(row) }, icon = Icons.Filled.Link)
            }
        }

        Text(
            "Schedule",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
        )
        val programmes = state.programmes
        when {
            programmes == null -> Box(Modifier.fillMaxWidth().height(160.dp), Alignment.Center) { CircularProgressIndicator() }
            programmes.isEmpty() -> Text(
                "No guide information for this channel.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                // Open on what's on now, with a little of what came before.
                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = (programmes.indexOfFirst { it.stopMs > now } - 1).coerceAtLeast(0),
                )
                LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(programmes, key = { it.id }) { p ->
                        val live = p.startMs <= now && p.stopMs > now
                        val past = p.stopMs <= now
                        val replayable = (past || live) && vm.canReplay(row, p)
                        val recording = if (canRecord && !past) vm.recordingFor(row, p, recordings) else null
                        ListItem(
                            selected = live,
                            onClick = {
                                when {
                                    live && !replayable -> {
                                        context.play(vm.playRequest(row))
                                        vm.dismissSchedule()
                                    }
                                    replayable -> vm.replayRequest(row, p)?.let {
                                        context.play(it)
                                        vm.dismissSchedule()
                                    }
                                    canRecord && !past -> vm.toggleRecording(row, p)
                                }
                            },
                            overlineContent = {
                                Text(
                                    listOfNotNull(
                                        if (live) "On now" else formatDay(p.startMs),
                                        "${formatTime(p.startMs)} – ${formatTime(p.stopMs)}",
                                    ).joinToString("  ·  "),
                                )
                            },
                            headlineContent = {
                                Text(p.title, fontWeight = if (live) FontWeight.SemiBold else null, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = p.description?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
                            trailingContent = when {
                                replayable -> {
                                    { Icon(Icons.Filled.Replay, if (live) "Watch from the start" else "Watch again") }
                                }
                                recording != null -> {
                                    { Icon(Icons.Filled.FiberManualRecord, "Recording", tint = Color(0xFFFF5449)) }
                                }
                                canRecord && !past -> {
                                    { Icon(Icons.Outlined.FiberManualRecord, "Record") }
                                }
                                else -> null
                            },
                            modifier = Modifier.alpha(if (past && !replayable) 0.55f else 1f),
                        )
                    }
                }
            }
        }
    }
    RequestFocusOnStart(watch)
}

/** Choose which of a Dispatcharr channel's provider streams to show. */
@Composable
fun TvSourcePicker(sources: List<DispatcharrSource>, onPick: (DispatcharrSource?) -> Unit) {
    val first = remember { FocusRequester() }
    TvDialog(
        title = "Which source?",
        onDismiss = { onPick(null) },
        buttons = { OutlinedButton(onClick = { onPick(null) }) { Text("Cancel") } },
    ) {
        sources.forEachIndexed { index, source ->
            ListItem(
                selected = false,
                onClick = { onPick(source) },
                headlineContent = { Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(Uri.parse(source.url).host ?: source.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
            )
        }
    }
    RequestFocusOnStart(first)
}
