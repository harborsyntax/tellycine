package app.mosaic.ui.downloads

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.mosaic.AppContainer
import app.mosaic.data.db.DownloadEntity
import app.mosaic.data.db.DownloadStatus
import app.mosaic.player.PlayRequest
import app.mosaic.player.play
import app.mosaic.ui.components.Artwork
import app.mosaic.ui.components.EmptyState
import app.mosaic.ui.components.SectionHeader
import app.mosaic.ui.components.mosaicViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class DownloadRow(val download: DownloadEntity, val fileOk: Boolean)

data class DownloadsState(
    val loaded: Boolean = false,
    val inProgress: List<DownloadRow> = emptyList(),
    val done: List<DownloadRow> = emptyList(),
    /** Space taken by finished downloads. */
    val usedBytes: Long = 0,
) {
    val isEmpty get() = inProgress.isEmpty() && done.isEmpty()
}

class DownloadsViewModel(private val container: AppContainer) : ViewModel() {
    private val downloads = container.downloads

    val state: StateFlow<DownloadsState> = downloads.all
        .map { list ->
            val rows = list.map { DownloadRow(it, downloads.fileOk(it)) }
            // Finished files that went missing are listed with failures, so they can be downloaded again.
            val (done, inProgress) = rows.partition { it.download.status == DownloadStatus.COMPLETED && it.fileOk }
            DownloadsState(
                loaded = true,
                inProgress = inProgress,
                done = done,
                usedBytes = done.sumOf { it.download.bytesTotal.coerceAtLeast(0) },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsState())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        downloads.ensurePolling()
    }

    fun freeBytes() = downloads.freeBytes()

    fun remove(d: DownloadEntity) {
        viewModelScope.launch { downloads.remove(d) }
    }

    fun removeAll() {
        viewModelScope.launch { downloads.removeAll() }
    }

    fun retry(d: DownloadEntity) {
        viewModelScope.launch {
            downloads.retry(d).onFailure { _messages.tryEmit(it.message ?: "Couldn't start the download.") }
        }
    }

    /** Plays the downloaded file, resuming where the title was left off. */
    suspend fun playRequest(d: DownloadEntity): PlayRequest {
        val h = container.content.history(d.playlistId, listOf(d.itemKey)).first().firstOrNull()
        val resume = h?.takeIf { it.durationMs <= 0 || it.positionMs < it.durationMs * 0.95 }?.positionMs ?: 0L
        return PlayRequest(
            playlistId = d.playlistId,
            kind = d.kind,
            streamId = d.streamId,
            historyKey = d.itemKey,
            url = d.sourceUrl,
            title = d.title,
            subtitle = d.subtitle,
            artwork = d.artwork,
            userAgent = d.userAgent,
            referrer = d.referrer,
            startPositionMs = resume,
            localUri = Uri.fromFile(File(d.filePath)).toString(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val vm = mosaicViewModel { c, _ -> DownloadsViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var confirmAll by remember { mutableStateOf(false) }
    var confirmOne by remember { mutableStateOf<DownloadEntity?>(null) }
    LaunchedEffect(vm) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (!state.isEmpty) {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Delete all downloads") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = {
                                    menu = false
                                    confirmAll = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.loaded && state.isEmpty) {
            EmptyState(
                Icons.Outlined.Download,
                "No downloads",
                "Download movies and episodes from their details page to watch them without a connection.",
                Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "storage") {
                val free = remember(state.usedBytes) { vm.freeBytes() }
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.SdStorage, null) },
                    headlineContent = { Text("${formatBytes(context, state.usedBytes)} used by downloads") },
                    supportingContent = { Text("${formatBytes(context, free)} free on this device") },
                )
            }
            if (state.inProgress.isNotEmpty()) {
                item(key = "h-progress") { SectionHeader("In progress") }
                items(state.inProgress, key = { "p-${it.download.playlistId}-${it.download.itemKey}" }) { row ->
                    val d = row.download
                    val failed = !d.status.isActive
                    DownloadItem(
                        row = row,
                        progress = if (d.status.isActive) d.fraction() ?: -1f else null,
                        onClick = if (failed) ({ vm.retry(d) }) else null,
                        trailing = {
                            Row {
                                if (failed) IconButton(onClick = { vm.retry(d) }) { Icon(Icons.Filled.Refresh, "Retry") }
                                IconButton(onClick = { confirmOne = d }) {
                                    if (failed) Icon(Icons.Outlined.Delete, "Remove") else Icon(Icons.Filled.Close, "Cancel download")
                                }
                            }
                        },
                    )
                }
            }
            if (state.done.isNotEmpty()) {
                item(key = "h-done") { SectionHeader("Downloaded") }
                items(state.done, key = { "d-${it.download.playlistId}-${it.download.itemKey}" }) { row ->
                    val d = row.download
                    DownloadItem(
                        row = row,
                        progress = null,
                        onClick = { scope.launch { context.play(vm.playRequest(d)) } },
                        trailing = { IconButton(onClick = { confirmOne = d }) { Icon(Icons.Outlined.Delete, "Delete download") } },
                    )
                }
            }
        }
    }

    confirmOne?.let { d ->
        val finished = d.status == DownloadStatus.COMPLETED
        RemoveDownloadDialog(
            title = when {
                d.status.isActive -> "Cancel download?"
                finished -> "Delete download?"
                else -> "Remove download?"
            },
            message = "${listOfNotNull(d.title, d.subtitle).joinToString(" · ")} will be removed from this device. You can still stream it.",
            confirmLabel = if (d.status.isActive) "Cancel download" else "Delete",
            onConfirm = {
                vm.remove(d)
                confirmOne = null
            },
            onDismiss = { confirmOne = null },
        )
    }
    if (confirmAll) {
        RemoveDownloadDialog(
            title = "Delete all downloads?",
            message = "Every downloaded movie and episode will be removed from this device, and unfinished downloads cancelled.",
            confirmLabel = "Delete all",
            onConfirm = {
                vm.removeAll()
                confirmAll = false
            },
            onDismiss = { confirmAll = false },
        )
    }
}

/**
 * A downloaded or downloading title. [progress] is 0–1, -1 for an unknown size, or null when
 * no progress bar is needed.
 */
@Composable
private fun DownloadItem(
    row: DownloadRow,
    progress: Float?,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val d = row.download
    ListItem(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        leadingContent = {
            Artwork(
                url = d.artwork,
                fallbackText = d.title,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(44.dp, 64.dp),
            )
        },
        headlineContent = { Text(d.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column(Modifier.fillMaxWidth()) {
                d.subtitle?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text(
                    downloadStatus(context, d, row.fileOk),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (d.status == DownloadStatus.FAILED || !d.status.isActive && !row.fileOk) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    progress == null -> Unit
                    progress < 0f -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                    else -> LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        drawStopIndicator = {},
                    )
                }
            }
        },
        trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) { trailing() } },
    )
}
