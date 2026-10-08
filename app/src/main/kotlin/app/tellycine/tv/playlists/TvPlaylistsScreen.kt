package app.tellycine.tv.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import app.tellycine.data.db.SyncState
import app.tellycine.data.sync.SyncProgress
import app.tellycine.tv.TvPalette
import app.tellycine.tv.components.RequestFocusOnStart
import app.tellycine.tv.components.TvActionButton
import app.tellycine.tv.components.TvConfirmDialog
import app.tellycine.tv.components.TvHeader
import app.tellycine.tv.components.TvSpacing
import app.tellycine.ui.components.TellycineLogo
import app.tellycine.ui.components.formatCount
import app.tellycine.ui.components.formatDate
import app.tellycine.ui.components.formatRelative
import app.tellycine.ui.components.tellycineViewModel
import app.tellycine.ui.home.SessionState
import app.tellycine.ui.playlists.PlaylistsViewModel

@Composable
fun TvPlaylistsScreen(
    session: SessionState,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
    onAddFromPhone: () -> Unit,
    onEdit: (Long) -> Unit,
    onCategories: (Long) -> Unit,
) {
    val vm = tellycineViewModel { c, _ -> PlaylistsViewModel(c) }
    val progress by vm.progress.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    val first = remember { FocusRequester() }

    if (session.loaded && session.playlists.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            TellycineLogo(height = 56.dp)
            Text(
                "Add an M3U playlist, a playlist file, or your Xtream Codes or Dispatcharr login to get started.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 560.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvActionButton("Add playlist", onClick = onAdd, icon = Icons.Filled.Add, primary = true, modifier = Modifier.focusRequester(first))
                TvActionButton("Type it on your phone", onClick = onAddFromPhone, icon = Icons.Filled.PhoneAndroid)
            }
            Text(
                "Typing long addresses and passwords is easier on a phone: it opens a page where you can enter them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 520.dp),
            )
        }
        RequestFocusOnStart(first)
        return
    }

    Column(Modifier.fillMaxSize()) {
        TvHeader(
            title = "Playlists",
            actions = {
                TvActionButton("Add playlist", onClick = onAdd, icon = Icons.Filled.Add, primary = true)
                TvActionButton("Add from phone", onClick = onAddFromPhone, icon = Icons.Filled.PhoneAndroid)
            },
        )
        LazyColumn(
            Modifier.fillMaxSize().focusRestorer(),
            contentPadding = TvSpacing.content,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(session.playlists, key = { it.id }) { p ->
                PlaylistCard(
                    playlist = p,
                    active = p.id == session.active?.id,
                    progress = progress[p.id],
                    onSelect = { onSelect(p.id) },
                    onRefresh = { vm.refresh(p.id) },
                    onRefreshGuide = { vm.refreshGuide(p.id) },
                    onCancel = { vm.cancel(p.id) },
                    onEdit = { onEdit(p.id) },
                    onCategories = { onCategories(p.id) },
                    onDelete = { confirmDelete = p },
                    firstButton = if (p.id == (session.active?.id ?: session.playlists.first().id)) first else null,
                )
            }
        }
    }
    RequestFocusOnStart(first)

    confirmDelete?.let { p ->
        TvConfirmDialog(
            title = "Delete ${p.name}?",
            message = "Its channels, guide, favourites and watch history will be removed from this device.",
            confirmLabel = "Delete",
            onConfirm = {
                vm.delete(p.id)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

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
    firstButton: FocusRequester?,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        colors = SurfaceDefaults.colors(containerColor = if (active) TvPalette.surfaceHigh else MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(playlist.name, style = MaterialTheme.typography.headlineSmall)
                if (active) Icon(Icons.Filled.CheckCircle, "In use", tint = TvPalette.primary)
            }
            Text(
                listOfNotNull(
                    when (playlist.type) {
                        PlaylistType.XTREAM -> "${if (playlist.isDispatcharr) "Dispatcharr" else "Xtream Codes"} · ${playlist.username}"
                        PlaylistType.M3U_URL -> "M3U playlist"
                        PlaylistType.M3U_FILE -> "Playlist file"
                    },
                    "${formatCount(playlist.liveCount)} channels",
                    if (!playlist.vodEnabled) "movies & series off" else null,
                    playlist.movieCount.takeIf { it > 0 }?.let { "${formatCount(it)} movies" },
                    playlist.seriesCount.takeIf { it > 0 }?.let { "${formatCount(it)} series" },
                    playlist.effectiveMaxConnections?.let { "$it connection${if (it == 1) "" else "s"}" },
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                buildList {
                    add(playlist.lastSyncedAt?.let { "Updated ${formatRelative(it)}" } ?: "Not loaded yet")
                    if (playlist.programmeCount > 0) add("${formatCount(playlist.programmeCount)} guide entries")
                    playlist.expiresAt?.let { add("Expires ${formatDate(it)}") }
                }.joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (progress != null) {
                Text(progress.message, style = MaterialTheme.typography.bodyMedium, color = TvPalette.primary)
                val fraction = progress.fraction
                if (fraction != null) {
                    LinearProgressIndicator(progress = { fraction }, color = TvPalette.primary, modifier = Modifier.fillMaxWidth().height(4.dp))
                } else {
                    LinearProgressIndicator(color = TvPalette.primary, modifier = Modifier.fillMaxWidth().height(4.dp))
                }
            } else if (playlist.syncState == SyncState.ERROR && playlist.syncMessage != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                    Text(playlist.syncMessage, color = MaterialTheme.colorScheme.error)
                }
            }
            Row(
                Modifier.padding(top = 6.dp).focusRestorer(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val firstModifier = firstButton?.let { Modifier.focusRequester(it) } ?: Modifier
                if (!active) {
                    TvActionButton("Use this playlist", onClick = onSelect, icon = Icons.Filled.Tv, primary = true, modifier = firstModifier)
                }
                if (progress != null) {
                    TvActionButton("Stop", onClick = onCancel, icon = Icons.Filled.Stop, modifier = if (active) firstModifier else Modifier)
                } else {
                    TvActionButton("Refresh", onClick = onRefresh, icon = Icons.Filled.Refresh, modifier = if (active) firstModifier else Modifier)
                }
                TvActionButton("Update guide", onClick = onRefreshGuide, icon = Icons.Filled.Refresh)
                TvActionButton("Edit", onClick = onEdit, icon = Icons.Filled.Edit)
                TvActionButton("Categories", onClick = onCategories, icon = Icons.Filled.Category)
                TvActionButton("Delete", onClick = onDelete, icon = Icons.Filled.Delete)
            }
        }
    }
}
