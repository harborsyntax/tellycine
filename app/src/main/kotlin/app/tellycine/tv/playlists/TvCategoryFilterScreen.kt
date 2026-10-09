package app.tellycine.tv.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Checkbox
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.tellycine.core.ContentKind
import app.tellycine.tv.components.RequestFocusOnStart
import app.tellycine.tv.components.TvActionButton
import app.tellycine.tv.components.TvEmptyState
import app.tellycine.tv.components.TvHeader
import app.tellycine.tv.components.TvSpacing
import app.tellycine.tv.components.TvTextInputDialog
import app.tellycine.ui.components.formatCount
import app.tellycine.ui.components.tellycineViewModel
import app.tellycine.ui.playlists.CategoryFilterViewModel

/** Tick the categories to show; unticked ones are hidden everywhere in the app. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvCategoryFilterScreen(playlistId: Long) {
    val vm = tellycineViewModel(key = "categories-$playlistId") { c, _ -> CategoryFilterViewModel(c, playlistId) }
    val playlist by vm.playlist.collectAsStateWithLifecycle()
    val kind by vm.kind.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("") }
    var editingFilter by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }

    val kinds = if (playlist?.vodEnabled == false) listOf(ContentKind.LIVE) else ContentKind.entries
    val rows = categories
    val visibleRows = remember(rows, filter) {
        val all = rows.orEmpty()
        if (filter.isBlank()) all else all.filter { it.category.name.contains(filter.trim(), ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize()) {
        TvHeader(title = "Categories", subtitle = playlist?.name)
        Row(
            Modifier.padding(start = TvSpacing.screenStart, end = TvSpacing.screenEnd, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (kinds.size > 1) {
                kinds.forEachIndexed { i, k ->
                    FilterChip(
                        selected = k == kind,
                        onClick = {
                            filter = ""
                            vm.selectKind(k)
                        },
                        modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    ) {
                        Text(
                            when (k) {
                                ContentKind.LIVE -> "Live TV"
                                ContentKind.MOVIE -> "Movies"
                                ContentKind.SERIES -> "Series"
                            }
                        )
                    }
                }
            }
        }
        if (rows != null && rows.isEmpty()) {
            TvEmptyState(Icons.Filled.Category, "No categories yet", "Categories appear here once the playlist has been loaded.")
            return@Column
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 820.dp).fillMaxWidth(),
                contentPadding = PaddingValues(start = TvSpacing.screenStart, end = TvSpacing.screenEnd, bottom = TvSpacing.screenBottom),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "about") {
                    Text(
                        "Unticked categories are hidden from Live TV, Movies, Series, search and favourites. " +
                            "New categories from your provider are shown until you untick them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                item(key = "tools") {
                    val shown = rows.orEmpty().count { !it.hidden }
                    val ids = visibleRows.map { it.category.categoryId }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("$shown of ${rows.orEmpty().size} shown", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TvActionButton(if (filter.isBlank()) "Filter" else "Filter: $filter", onClick = { editingFilter = true }, icon = Icons.Filled.FilterList)
                        TvActionButton(if (filter.isBlank()) "Tick all" else "Tick matches", onClick = { vm.setVisible(ids, true) }, enabled = ids.isNotEmpty())
                        TvActionButton(if (filter.isBlank()) "Untick all" else "Untick matches", onClick = { vm.setVisible(ids, false) }, enabled = ids.isNotEmpty())
                    }
                }
                items(visibleRows, key = { it.category.categoryId }) { row ->
                    val visible = !row.hidden
                    ListItem(
                        selected = false,
                        onClick = { vm.setVisible(listOf(row.category.categoryId), !visible) },
                        leadingContent = { Checkbox(checked = visible, onCheckedChange = null) },
                        headlineContent = { Text(row.category.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = { Text(formatCount(row.category.itemCount), style = MaterialTheme.typography.labelMedium) },
                        modifier = if (kinds.size == 1 && row == visibleRows.firstOrNull()) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
        }
    }
    RequestFocusOnStart(first, rows != null)

    if (editingFilter) {
        TvTextInputDialog(
            title = "Filter categories",
            initial = filter,
            onDone = {
                filter = it
                editingFilter = false
            },
            onDismiss = { editingFilter = false },
        )
    }
}
