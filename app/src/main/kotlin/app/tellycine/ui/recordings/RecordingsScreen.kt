package app.tellycine.ui.recordings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tellycine.AppContainer
import app.tellycine.core.ContentKind
import app.tellycine.core.dispatcharr.DvrRecording
import app.tellycine.core.dispatcharr.DvrRecording.State
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.repo.DvrState
import app.tellycine.player.PlayRequest
import app.tellycine.player.play
import app.tellycine.ui.components.Artwork
import app.tellycine.ui.components.EmptyState
import app.tellycine.ui.components.SectionHeader
import app.tellycine.ui.components.formatDay
import app.tellycine.ui.components.formatTime
import app.tellycine.ui.components.tellycineViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecordingsUi(
    val playlist: PlaylistEntity? = null,
    val dvr: DvrState = DvrState(),
    /** Dispatcharr channel id → channel in the playlist, for names and logos. */
    val channels: Map<Long, StreamEntity> = emptyMap(),
)

class RecordingsViewModel(private val container: AppContainer, private val playlistId: Long) : ViewModel() {
    private val channels = MutableStateFlow<Map<Long, StreamEntity>>(emptyMap())
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val playlist = container.playlists.playlists
        .map { list -> list.firstOrNull { it.id == playlistId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val ui: StateFlow<RecordingsUi> = combine(
        playlist,
        container.dvr.state.map { it[playlistId] ?: DvrState(loading = true) },
        channels,
    ) { p, dvr, ch -> RecordingsUi(p, dvr, ch) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordingsUi())

    init {
        refresh()
        viewModelScope.launch {
            container.dvr.state.collect { state ->
                val ids = state[playlistId]?.recordings?.map { it.channelId }?.toSet().orEmpty()
                val missing = ids - channels.value.keys
                if (missing.isNotEmpty()) {
                    val found = missing.mapNotNull { id ->
                        container.content.getStream(playlistId, ContentKind.LIVE, id.toString())?.let { id to it }
                    }
                    channels.value = channels.value + found
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val p = container.playlists.get(playlistId) ?: return@launch
            container.dvr.refresh(p)
        }
    }

    fun delete(recording: DvrRecording) {
        val p = playlist.value ?: return
        viewModelScope.launch {
            _message.value = try {
                container.dvr.delete(p, recording.id)
                null
            } catch (e: Exception) {
                e.message
            }
        }
    }

    fun dismissMessage() {
        _message.value = null
    }

    fun poster(recording: DvrRecording): String? {
        val p = playlist.value ?: return null
        return container.dvr.posterUrl(p, recording) ?: channels.value[recording.channelId]?.logo
    }

    fun playRequest(recording: DvrRecording): PlayRequest? {
        val p = playlist.value ?: return null
        val channel = channels.value[recording.channelId]
        return PlayRequest(
            playlistId = p.id,
            kind = ContentKind.LIVE,
            streamId = recording.channelId.toString(),
            historyKey = "",
            url = container.dvr.playbackUrl(p, recording.id),
            title = recording.title,
            subtitle = listOfNotNull(channel?.name, "${formatDay(recording.startMs)} ${formatTime(recording.startMs)}").joinToString(" · "),
            artwork = poster(recording),
            userAgent = p.userAgent,
            catchup = true,
            apiKey = p.dvrApiKey,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(playlistId: Long, onBack: () -> Unit) {
    val vm = tellycineViewModel(key = "recordings-$playlistId") { c, _ -> RecordingsViewModel(c, playlistId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf<DvrRecording?>(null) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Recordings")
                            ui.playlist?.let {
                                Text(it.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    actions = { IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, "Refresh") } },
                )
                if (ui.dvr.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
    ) { padding ->
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
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when {
                ui.dvr.error != null && ui.dvr.recordings.isEmpty() -> EmptyState(
                    Icons.Filled.ErrorOutline, "Couldn't load recordings", ui.dvr.error ?: "", actionLabel = "Try again", onAction = vm::refresh,
                )
                groups.isEmpty() && !ui.dvr.loading -> EmptyState(
                    Icons.Filled.VideoLibrary,
                    "No recordings yet",
                    "Long-press a channel and tap the record button next to a programme in its guide.",
                )
                else -> LazyColumn(
                    Modifier.widthIn(max = 840.dp).fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    ui.dvr.error?.let { error ->
                        item(key = "error") {
                            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                        }
                    }
                    groups.forEach { (label, list) ->
                        item(key = "h-$label") { SectionHeader(label) }
                        items(list, key = { it.id }) { rec ->
                            val state = rec.state(now)
                            RecordingRow(
                                recording = rec,
                                state = state,
                                channel = ui.channels[rec.channelId],
                                poster = vm.poster(rec),
                                onPlay = if (state == State.COMPLETED || state == State.RECORDING) {
                                    { vm.playRequest(rec)?.let(context::play) }
                                } else {
                                    null
                                },
                                onDelete = { confirmDelete = rec },
                                deleteLabel = if (state == State.SCHEDULED) "Cancel recording" else "Delete recording",
                            )
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { rec ->
        val scheduled = rec.state(System.currentTimeMillis()) == State.SCHEDULED
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (scheduled) "Cancel this recording?" else "Delete this recording?") },
            text = { Text(if (scheduled) rec.title else "\"${rec.title}\" will be deleted from the Dispatcharr server.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(rec)
                    confirmDelete = null
                }) { Text(if (scheduled) "Cancel recording" else "Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } },
        )
    }
    message?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::dismissMessage,
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = vm::dismissMessage) { Text("OK") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingRow(
    recording: DvrRecording,
    state: State,
    channel: StreamEntity?,
    poster: String?,
    onPlay: (() -> Unit)?,
    onDelete: () -> Unit,
    deleteLabel: String,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.combinedClickable(
            enabled = true,
            onClick = { onPlay?.invoke() ?: run { menu = true } },
            onLongClick = { menu = true },
        ),
        leadingContent = {
            Artwork(
                url = poster,
                fallbackText = channel?.name ?: recording.title,
                contentScale = if (recording.posterUrl != null || recording.posterLogoId != null) ContentScale.Crop else ContentScale.Fit,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(width = 96.dp, height = 54.dp),
            )
        },
        overlineContent = {
            Text(
                listOfNotNull(channel?.name, "${formatDay(recording.startMs)} ${formatTime(recording.startMs)} – ${formatTime(recording.endMs)}")
                    .joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        headlineContent = { Text(recording.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                val sub = listOfNotNull(
                    recording.season?.let { s -> "S$s" + (recording.episode?.let { " E$it" } ?: "") },
                    recording.subtitle,
                ).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                when (state) {
                    State.RECORDING -> StateChip("Recording", Icons.Filled.FiberManualRecord, MaterialTheme.colorScheme.error)
                    State.SCHEDULED -> StateChip("Scheduled", Icons.Filled.Schedule, MaterialTheme.colorScheme.primary)
                    State.FAILED -> StateChip("Failed", Icons.Filled.ErrorOutline, MaterialTheme.colorScheme.error)
                    State.COMPLETED -> Unit
                }
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(deleteLabel) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun StateChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: androidx.compose.ui.graphics.Color) {
    AssistChip(
        onClick = {},
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(16.dp), tint = color) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color),
        modifier = Modifier.padding(top = 4.dp),
    )
}
