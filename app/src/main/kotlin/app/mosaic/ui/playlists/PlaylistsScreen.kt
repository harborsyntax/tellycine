package app.mosaic.ui.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.mosaic.AppContainer
import app.mosaic.data.db.PlaylistEntity
import app.mosaic.data.db.PlaylistType
import app.mosaic.data.db.SyncState
import app.mosaic.data.sync.SyncProgress
import app.mosaic.ui.components.MosaicLogo
import app.mosaic.ui.components.formatCount
import app.mosaic.ui.components.formatDate
import app.mosaic.ui.components.formatRelative
import app.mosaic.ui.components.mosaicViewModel
import app.mosaic.ui.home.SessionState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlaylistsViewModel(private val container: AppContainer) : ViewModel() {
    val progress: StateFlow<Map<Long, SyncProgress>> = container.syncManager.progress

    fun refresh(id: Long) = container.syncManager.sync(id)
    fun refreshGuide(id: Long) = container.syncManager.sync(id, content = false, epg = true)
    fun cancel(id: Long) = container.syncManager.cancel(id)
    fun delete(id: Long) {
        viewModelScope.launch { container.playlists.delete(id) }
    }
    fun select(id: Long) {
        viewModelScope.launch { container.playlists.setActive(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    session: SessionState,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onCategories: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    val vm = mosaicViewModel { c, _ -> PlaylistsViewModel(c) }
    val progress by vm.progress.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<PlaylistEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { MosaicLogo(height = 26.dp) },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add playlist") },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (session.loaded && session.playlists.isEmpty()) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                MosaicLogo(height = 48.dp)
                Text(
                    "Add an M3U playlist, a local playlist file or your Xtream Codes login to get started.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 420.dp),
                )
                Button(onClick = onAdd) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add playlist")
                }
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(session.playlists, key = { it.id }) { p ->
                PlaylistCard(
                    playlist = p,
                    active = p.id == session.active?.id,
                    progress = progress[p.id],
                    onSelect = { vm.select(p.id) },
                    onRefresh = { vm.refresh(p.id) },
                    onRefreshGuide = { vm.refreshGuide(p.id) },
                    onCancel = { vm.cancel(p.id) },
                    onEdit = { onEdit(p.id) },
                    onCategories = { onCategories(p.id) },
                    onDelete = { confirmDelete = p },
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                )
            }
        }
    }

    confirmDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { Icon(Icons.Filled.Delete, null) },
            title = { Text("Delete ${p.name}?") },
            text = { Text("Its channels, guide, favourites and watch history will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(p.id)
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaylistCard(
    playlist: PlaylistEntity,
    active: Boolean,
    progress: SyncProgress?,
    onSelect: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshGuide: () -> Unit,
    onCancel: () -> Unit,
    onEdit: () -> Unit,
    onCategories: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(onClick = onSelect, modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(playlist.name, style = MaterialTheme.typography.titleLarge)
                        if (active) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Filled.CheckCircle, "Active", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(
                        when (playlist.type) {
                            PlaylistType.XTREAM ->
                                "${if (playlist.isDispatcharr) "Dispatcharr" else "Xtream Codes"} · ${playlist.username}"
                            PlaylistType.M3U_URL -> "M3U playlist"
                            PlaylistType.M3U_FILE -> "Local M3U file"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                var menu by remember { mutableStateOf(false) }
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Categories") },
                        leadingIcon = { Icon(Icons.Filled.Category, null) },
                        onClick = { menu = false; onCategories() },
                    )
                    DropdownMenuItem(
                        text = { Text("Update TV guide only") },
                        leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                        onClick = { menu = false; onRefreshGuide() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SuggestionChip(onClick = {}, label = { Text("${formatCount(playlist.liveCount)} channels") })
                if (!playlist.vodEnabled) SuggestionChip(onClick = {}, label = { Text("Movies & series off") })
                if (playlist.movieCount > 0) SuggestionChip(onClick = {}, label = { Text("${formatCount(playlist.movieCount)} movies") })
                if (playlist.seriesCount > 0) SuggestionChip(onClick = {}, label = { Text("${formatCount(playlist.seriesCount)} series") })
            }

            val details = buildList {
                add(playlist.lastSyncedAt?.let { "Updated ${formatRelative(it)}" } ?: "Not loaded yet")
                if (playlist.programmeCount > 0) add("${formatCount(playlist.programmeCount)} guide entries")
                playlist.expiresAt?.let { add("Expires ${formatDate(it)}") }
            }
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (progress != null) {
                Text(progress.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                val fraction = progress.fraction
                if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
            } else if (playlist.syncState == SyncState.ERROR && playlist.syncMessage != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                    Text(playlist.syncMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (progress != null) {
                    OutlinedButton(onClick = onCancel) {
                        Icon(Icons.Filled.Stop, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Stop")
                    }
                } else {
                    FilledTonalButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Refresh")
                    }
                }
                if (!active) {
                    TextButton(onClick = onSelect) { Text("Use this playlist") }
                }
            }
        }
    }
}
