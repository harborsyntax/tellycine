package app.tellycine.tv.downloads

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import app.tellycine.data.db.DownloadStatus
import app.tellycine.player.play
import app.tellycine.tv.TvPalette
import app.tellycine.tv.components.RequestFocusOnStart
import app.tellycine.tv.components.TvActionButton
import app.tellycine.tv.components.TvConfirmDialog
import app.tellycine.tv.components.TvEmptyState
import app.tellycine.tv.components.TvHeader
import app.tellycine.tv.components.TvSectionLabel
import app.tellycine.tv.components.TvSpacing
import app.tellycine.tv.components.okClicks
import app.tellycine.ui.components.Artwork
import app.tellycine.ui.downloads.DownloadRow
import app.tellycine.ui.downloads.DownloadsViewModel
import app.tellycine.ui.downloads.downloadStatus
import app.tellycine.ui.downloads.formatBytes
import app.tellycine.ui.downloads.fraction
import app.tellycine.ui.components.tellycineViewModel
import kotlinx.coroutines.launch

/** Downloaded movies and episodes: OK plays (or retries), holding OK deletes or cancels. */
@Composable
fun TvDownloadsScreen() {
    val vm = tellycineViewModel { c, _ -> DownloadsViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<DownloadRow?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(vm) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    Column(Modifier.fillMaxSize()) {
        TvHeader(
            title = "Downloads",
            subtitle = if (state.loaded) "${formatBytes(context, state.usedBytes)} used · ${formatBytes(context, vm.freeBytes())} free" else null,
            actions = {
                if (!state.isEmpty) TvActionButton("Delete all", onClick = { confirmAll = true }, icon = Icons.Filled.Delete)
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            if (state.loaded && state.isEmpty) {
                TvEmptyState(
                    Icons.Filled.DownloadForOffline,
                    "No downloads",
                    "Open a movie or episode and choose Download to watch it without a connection.",
                )
            } else {
                LazyColumn(
                    Modifier.widthIn(max = 900.dp).fillMaxWidth(),
                    contentPadding = PaddingValues(start = TvSpacing.screenStart, end = TvSpacing.screenEnd, bottom = TvSpacing.screenBottom),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    section("Downloading", state.inProgress, first, onOpen = { row ->
                        val d = row.download
                        if (d.status == DownloadStatus.FAILED || d.status == DownloadStatus.COMPLETED) vm.retry(d) else confirm = row
                    }, onHold = { confirm = it })
                    section("On this device", state.done, first.takeIf { state.inProgress.isEmpty() }, onOpen = { row ->
                        scope.launch { context.play(vm.playRequest(row.download)) }
                    }, onHold = { confirm = it })
                }
            }
        }
    }
    RequestFocusOnStart(first, state.loaded)

    confirm?.let { row ->
        val d = row.download
        val finished = d.status == DownloadStatus.COMPLETED && row.fileOk
        val label = listOfNotNull(d.title, d.subtitle).joinToString(" · ")
        TvConfirmDialog(
            title = if (finished) "Delete download?" else "Cancel download?",
            message = if (finished) "$label will be removed from this device. You can still stream it." else "$label will stop downloading.",
            confirmLabel = if (finished) "Delete" else "Cancel download",
            dismissLabel = "Keep",
            onConfirm = {
                vm.remove(d)
                confirm = null
            },
            onDismiss = { confirm = null },
        )
    }
    if (confirmAll) {
        TvConfirmDialog(
            title = "Delete all downloads?",
            message = "Every downloaded movie and episode will be removed from this device, and downloads in progress stopped.",
            confirmLabel = "Delete all",
            onConfirm = {
                vm.removeAll()
                confirmAll = false
            },
            onDismiss = { confirmAll = false },
        )
    }
}

private fun LazyListScope.section(
    title: String,
    rows: List<DownloadRow>,
    focus: FocusRequester?,
    onOpen: (DownloadRow) -> Unit,
    onHold: (DownloadRow) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "h-$title") { TvSectionLabel(title) }
    items(rows, key = { "${it.download.playlistId}/${it.download.itemKey}" }) { row ->
        val d = row.download
        val context = LocalContext.current
        ListItem(
            selected = false,
            onClick = { onOpen(row) },
            headlineContent = { Text(d.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            overlineContent = d.subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(downloadStatus(context, d, row.fileOk), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val fraction = d.fraction()
                    if (d.status == DownloadStatus.RUNNING && fraction != null) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            color = TvPalette.primary,
                            trackColor = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.width(260.dp).height(4.dp),
                        )
                    }
                }
            },
            leadingContent = {
                Artwork(url = d.artwork, fallbackText = d.title, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(width = 60.dp, height = 90.dp))
            },
            modifier = Modifier
                .okClicks(onClick = { onOpen(row) }, onLongClick = { onHold(row) })
                .then(if (focus != null && row == rows.first()) Modifier.focusRequester(focus) else Modifier),
        )
    }
}
