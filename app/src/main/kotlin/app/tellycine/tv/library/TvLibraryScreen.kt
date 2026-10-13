package app.tellycine.tv.library

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.HeartBroken
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Text
import app.tellycine.data.db.HistoryEntity
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.db.artwork
import app.tellycine.data.db.score
import app.tellycine.data.settings.LibrarySort
import app.tellycine.player.play
import app.tellycine.tv.components.RequestFocusOnStart
import app.tellycine.tv.components.TvActionButton
import app.tellycine.tv.components.TvChannelTile
import app.tellycine.tv.components.TvConfirmDialog
import app.tellycine.tv.components.TvDialog
import app.tellycine.tv.components.TvEmptyState
import app.tellycine.tv.components.TvHeader
import app.tellycine.tv.components.TvPosterCard
import app.tellycine.tv.components.TvProgressCard
import app.tellycine.tv.components.TvSpacing
import app.tellycine.tv.components.TvSwitchRow
import app.tellycine.ui.components.progress
import app.tellycine.ui.components.tellycineViewModel
import app.tellycine.ui.home.SessionState
import app.tellycine.ui.library.LibraryGroup
import app.tellycine.ui.library.LibraryViewModel
import kotlinx.coroutines.launch

/** Something to remove from a Library row, offered when OK is held on it. */
private class RemoveAction(val title: String, val label: String, val icon: ImageVector, val onConfirm: () -> Unit)

private enum class ClearTarget(val title: String, val message: String) {
    CONTINUE_WATCHING(
        "Clear continue watching?",
        "Everything you haven't finished will be removed from this list, and its resume point forgotten. " +
            "Your favourites and downloads aren't affected.",
    ),
    RECENT_CHANNELS(
        "Clear recently watched channels?",
        "Your recently watched channels will be cleared. Favourite channels aren't affected.",
    ),
}

@Composable
fun TvLibraryScreen(
    session: SessionState,
    onOpen: (StreamEntity) -> Unit,
    onOpenRecordings: (Long) -> Unit,
    onOpenDownloads: () -> Unit,
    onConnectDvr: (Long) -> Unit,
) {
    val vm = tellycineViewModel { c, _ -> LibraryViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var remove by remember { mutableStateOf<RemoveAction?>(null) }
    var clear by remember { mutableStateOf<ClearTarget?>(null) }
    var arrange by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }

    fun removed(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    Column(Modifier.fillMaxSize()) {
        TvHeader(
            title = "Library",
            subtitle = session.active?.name,
            actions = {
                val dvr = state.playlist?.takeIf { it.isDispatcharr }
                if (dvr != null) {
                    TvActionButton(
                        "Recordings",
                        onClick = { if (dvr.hasDvr) onOpenRecordings(dvr.id) else onConnectDvr(dvr.id) },
                        icon = Icons.Filled.FiberManualRecord,
                    )
                }
                if (downloads.count > 0) {
                    TvActionButton("Downloads (${downloads.count})", onClick = onOpenDownloads, icon = Icons.Filled.DownloadForOffline)
                }
                if (!state.isEmpty) {
                    TvActionButton("Sort", onClick = { arrange = true }, icon = Icons.Filled.Sort)
                }
            },
        )
        if (state.isEmpty) {
            TvEmptyState(
                Icons.Filled.FavoriteBorder,
                "Your library is empty",
                "Favourites, recently watched channels and anything you haven't finished will show up here.",
            )
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = TvSpacing.screenBottom),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            row(
                key = "cw",
                title = "Continue watching",
                groups = state.continueWatching,
                onClearAll = { clear = ClearTarget.CONTINUE_WATCHING },
                focus = first,
            ) { h, modifier ->
                TvProgressCard(
                    title = h.history.title,
                    subtitle = h.history.subtitle,
                    artwork = h.history.artwork,
                    progress = h.history.progress(),
                    onClick = { scope.launch { context.play(vm.historyRequest(h.history)) } },
                    onLongClick = {
                        remove = RemoveAction(h.history.title, "Remove from continue watching", Icons.Outlined.RemoveCircleOutline) {
                            vm.remove(h.history)
                            removed("Removed from continue watching")
                        }
                    },
                    modifier = modifier,
                )
            }
            row(
                key = "rc",
                title = "Recently watched channels",
                groups = state.recentChannels,
                onClearAll = { clear = ClearTarget.RECENT_CHANNELS },
                focus = first.takeIf { state.continueWatching.isEmpty() },
            ) { h, modifier ->
                TvChannelTile(
                    name = h.history.title,
                    logo = h.history.artwork,
                    onClick = { scope.launch { context.play(vm.historyRequest(h.history)) } },
                    onLongClick = { remove = historyRemoval(h.history, vm, ::removed) },
                    modifier = modifier,
                )
            }
            row(
                key = "fc",
                title = "Favourite channels",
                groups = state.channels,
                focus = first.takeIf { state.continueWatching.isEmpty() && state.recentChannels.isEmpty() },
            ) { s, modifier ->
                TvChannelTile(
                    name = s.name,
                    logo = s.logo,
                    onClick = { scope.launch { context.play(vm.channelRequest(s)) } },
                    onLongClick = { remove = favouriteRemoval(s, vm, ::removed) },
                    modifier = modifier,
                )
            }
            for ((key, title, groups) in listOf(
                Triple("fm", "Favourite movies", state.movies),
                Triple("fs", "Favourite series", state.series),
            )) {
                row(
                    key = key,
                    title = title,
                    groups = groups,
                    focus = first.takeIf {
                        state.continueWatching.isEmpty() && state.recentChannels.isEmpty() && state.channels.isEmpty() &&
                            (key == "fm" || state.movies.isEmpty())
                    },
                ) { s, modifier ->
                    TvPosterCard(
                        title = s.name,
                        poster = s.artwork,
                        meta = s.year,
                        rating = s.score,
                        onClick = { onOpen(s) },
                        onLongClick = { remove = favouriteRemoval(s, vm, ::removed) },
                        modifier = modifier.width(130.dp),
                    )
                }
            }
        }
    }
    RequestFocusOnStart(first, state.isEmpty)

    remove?.let { action ->
        val confirm = remember { FocusRequester() }
        TvDialog(
            title = action.title,
            onDismiss = { remove = null },
            buttons = { OutlinedButton(onClick = { remove = null }) { Text("Cancel") } },
        ) {
            ListItem(
                selected = false,
                onClick = {
                    action.onConfirm()
                    remove = null
                },
                headlineContent = { Text(action.label) },
                leadingContent = { Icon(action.icon, null) },
                modifier = Modifier.focusRequester(confirm),
            )
        }
        RequestFocusOnStart(confirm)
    }

    clear?.let { target ->
        TvConfirmDialog(
            title = target.title,
            message = target.message,
            confirmLabel = "Clear all",
            onConfirm = {
                when (target) {
                    ClearTarget.CONTINUE_WATCHING -> vm.clearContinueWatching()
                    ClearTarget.RECENT_CHANNELS -> vm.clearRecentChannels()
                }
                clear = null
            },
            onDismiss = { clear = null },
        )
    }

    if (arrange) {
        val focus = remember { FocusRequester() }
        TvDialog(
            title = "Sort and group",
            onDismiss = { arrange = false },
            buttons = { OutlinedButton(onClick = { arrange = false }) { Text("Done") } },
        ) {
            listOf(
                LibrarySort.RECENT to "Most recent first",
                LibrarySort.NAME_ASC to "Name (A–Z)",
                LibrarySort.NAME_DESC to "Name (Z–A)",
                LibrarySort.PLAYLIST_ORDER to "Playlist order",
            ).forEach { (sort, label) ->
                ListItem(
                    selected = state.sort == sort,
                    onClick = { vm.setSort(sort) },
                    headlineContent = { Text(label) },
                    leadingContent = { RadioButton(selected = state.sort == sort, onClick = null) },
                    modifier = if (state.sort == sort) Modifier.focusRequester(focus) else Modifier,
                )
            }
            TvSwitchRow(
                title = "Group by category",
                summary = "One row per category",
                checked = state.groupByCategory,
                onChange = vm::setGroupByCategory,
            )
        }
        RequestFocusOnStart(focus)
    }
}

private fun historyRemoval(item: HistoryEntity, vm: LibraryViewModel, removed: (String) -> Unit) =
    RemoveAction(item.title, "Remove from recently watched", Icons.Outlined.RemoveCircleOutline) {
        vm.remove(item)
        removed("Removed from recently watched")
    }

private fun favouriteRemoval(stream: StreamEntity, vm: LibraryViewModel, removed: (String) -> Unit) =
    RemoveAction(stream.name, "Remove from favourites", Icons.Outlined.HeartBroken) {
        vm.setFavorite(stream, false)
        removed("Removed from favourites")
    }

/**
 * One Library section as horizontal rows (one per category when grouping), titled, with an
 * optional "Clear all". [focus] marks the section that takes focus first.
 */
private fun <T> LazyListScope.row(
    key: String,
    title: String,
    groups: List<LibraryGroup<T>>,
    onClearAll: (() -> Unit)? = null,
    focus: FocusRequester? = null,
    item: @Composable (T, Modifier) -> Unit,
) {
    groups.forEachIndexed { index, group ->
        item(key = "$key-${group.key}") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = TvSpacing.screenStart, end = TvSpacing.screenEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        listOfNotNull(title, group.label).joinToString("  ·  "),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (index == 0 && onClearAll != null) {
                        TvActionButton("Clear all", onClick = onClearAll)
                    }
                }
                LazyRow(
                    contentPadding = PaddingValues(start = TvSpacing.screenStart, end = TvSpacing.screenEnd, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.focusRestorer(),
                ) {
                    itemsIndexed(group.items) { i, value ->
                        item(value, if (i == 0 && index == 0 && focus != null) Modifier.focusRequester(focus) else Modifier)
                    }
                }
            }
        }
    }
}
