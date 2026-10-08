package app.tellycine.tv.recordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.tellycine.core.dispatcharr.DvrRecording
import app.tellycine.core.dispatcharr.DvrRecording.State
import app.tellycine.player.play
import app.tellycine.tv.components.RequestFocusOnStart
import app.tellycine.tv.components.TvActionButton
import app.tellycine.tv.components.TvConfirmDialog
import app.tellycine.tv.components.TvEmptyState
import app.tellycine.tv.components.TvHeader
import app.tellycine.tv.components.TvMessageDialog
import app.tellycine.tv.components.TvSectionLabel
import app.tellycine.tv.components.TvSpacing
import app.tellycine.tv.components.okClicks
import app.tellycine.ui.components.Artwork
import app.tellycine.ui.components.formatDay
import app.tellycine.ui.components.formatTime
import app.tellycine.ui.components.tellycineViewModel
import app.tellycine.ui.recordings.RecordingsViewModel

/** Dispatcharr recordings: OK plays a recording, holding OK cancels or deletes it. */
@Composable
fun TvRecordingsScreen(playlistId: Long) {
    val vm = tellycineViewModel(key = "recordings-$playlistId") { c, _ -> RecordingsViewModel(c, playlistId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf<DvrRecording?>(null) }
    val first = remember { FocusRequester() }
    val now = remember(ui.dvr.loadedAt) { System.currentTimeMillis() }
    val groups = remember(ui.dvr.recordings, now) {
        val byState = ui.dvr.recordings.groupBy { it.state(now) }
        listOf(
            "Recording now" to byState[State.RECORDING].orEmpty().sortedBy { it.startMs },
            "Scheduled" to byState[State.SCHEDULED].orEmpty().sortedBy { it.startMs },
            "Recorded" to byState[State.COMPLETED].orEmpty().sortedByDescending { it.startMs },
            "Failed" to byState[State.FAILED].orEmpty().sortedByDescending { it.startMs },
        ).filter { it.second.isNotEmpty() }
    }

    Column(Modifier.fillMaxSize()) {
        TvHeader(
            title = "Recordings",
            subtitle = ui.playlist?.name,
            actions = { TvActionButton("Refresh", onClick = vm::refresh, icon = Icons.Filled.Refresh) },
        )
        if (ui.dvr.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = TvSpacing.screenStart))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            when {
                ui.dvr.error != null && ui.dvr.recordings.isEmpty() -> TvEmptyState(
                    Icons.Filled.ErrorOutline, "Couldn't load recordings", ui.dvr.error ?: "", actionLabel = "Try again", onAction = vm::refresh,
                )
                groups.isEmpty() && !ui.dvr.loading -> TvEmptyState(
                    Icons.Filled.VideoLibrary,
                    "No recordings yet",
                    "In Live TV, hold OK on a channel and choose a programme in its schedule to record it.",
                )
                else -> LazyColumn(
                    Modifier.widthIn(max = 900.dp).fillMaxWidth(),
                    contentPadding = PaddingValues(start = TvSpacing.screenStart, end = TvSpacing.screenEnd, bottom = TvSpacing.screenBottom),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ui.dvr.error?.let { error ->
                        item(key = "error") { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                    }
                    groups.forEachIndexed { groupIndex, (label, list) ->
                        item(key = "h-$label") { TvSectionLabel(label) }
                        items(list, key = { it.id }) { rec ->
                            val state = rec.state(now)
                            val channel = ui.channels[rec.channelId]
                            val playable = state == State.COMPLETED || state == State.RECORDING
                            val open = { if (playable) vm.playRequest(rec)?.let(context::play) else confirmDelete = rec }
                            ListItem(
                                selected = false,
                                onClick = { open() },
                                overlineContent = {
                                    Text(
                                        listOfNotNull(channel?.name, "${formatDay(rec.startMs)} ${formatTime(rec.startMs)} – ${formatTime(rec.endMs)}").joinToString("  ·  "),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                headlineContent = { Text(rec.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    val sub = listOfNotNull(
                                        rec.season?.let { s -> "S$s" + (rec.episode?.let { " E$it" } ?: "") },
                                        rec.subtitle,
                                        when (state) {
                                            State.RECORDING -> "Recording"
                                            State.SCHEDULED -> "Scheduled · OK to cancel"
                                            State.FAILED -> "Failed"
                                            State.COMPLETED -> null
                                        },
                                    ).joinToString("  ·  ")
                                    if (sub.isNotBlank()) Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (state == State.RECORDING) Color(0xFFFF8A80) else Color.Unspecified)
                                },
                                leadingContent = {
                                    Artwork(
                                        url = vm.poster(rec),
                                        fallbackText = channel?.name ?: rec.title,
                                        contentScale = if (rec.posterUrl != null || rec.posterLogoId != null) ContentScale.Crop else ContentScale.Fit,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.size(width = 112.dp, height = 63.dp),
                                    )
                                },
                                modifier = Modifier
                                    .okClicks(onClick = { open() }, onLongClick = { confirmDelete = rec })
                                    .then(if (groupIndex == 0 && rec == list.first()) Modifier.focusRequester(first) else Modifier),
                            )
                        }
                    }
                }
            }
        }
    }
    RequestFocusOnStart(first, groups.isNotEmpty())

    confirmDelete?.let { rec ->
        val scheduled = rec.state(System.currentTimeMillis()) == State.SCHEDULED
        TvConfirmDialog(
            title = if (scheduled) "Cancel this recording?" else "Delete this recording?",
            message = if (scheduled) rec.title else "\"${rec.title}\" will be deleted from the Dispatcharr server.",
            confirmLabel = if (scheduled) "Cancel recording" else "Delete",
            dismissLabel = "Keep",
            onConfirm = {
                vm.delete(rec)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
    message?.let { TvMessageDialog("Recordings", it, onDismiss = vm::dismissMessage) }
}
