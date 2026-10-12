package app.tellycine.tv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.tellycine.data.repo.PlaylistFilter
import app.tellycine.data.settings.CategorySort
import app.tellycine.tv.live.next
import app.tellycine.ui.components.CategoryOption
import app.tellycine.ui.components.formatCount

/**
 * Side panel listing a playlist's categories. Opens on the current one; long lists get a filter.
 * With more than one playlist, the [playlists] to show come first. Given a [sort], the categories
 * start with a Sort button, which switches between the provider's order and A–Z ([onSortChange]).
 */
@Composable
fun TvCategoryPanel(
    title: String,
    options: List<CategoryOption>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    playlists: PlaylistFilter? = null,
    onSelectPlaylists: (Long) -> Unit = {},
    sort: CategorySort? = null,
    onSortChange: (CategorySort) -> Unit = {},
) {
    var filter by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    val visible = remember(options, filter) {
        if (filter.isBlank()) options else options.filter { it.name.contains(filter.trim(), ignoreCase = true) }
    }
    // Focus starts on the current category, or the first one when it is filtered out.
    val focusId = visible.firstOrNull { it.id == selected }?.id ?: visible.firstOrNull()?.id
    val startIndex = visible.indexOfFirst { it.id == focusId }.coerceAtLeast(0)
    // Rows above the categories: the playlists with their two headings, Sort and the filter.
    val lead = (if (playlists != null) playlists.options.size + 2 else 0) + (if (sort != null) 1 else 0) +
        (if (options.size > 12) 1 else 0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (lead + startIndex - 3).coerceAtLeast(0))
    val focus = remember { FocusRequester() }
    var focusOnTarget by remember { mutableStateOf(false) }
    val target = Modifier.focusRequester(focus).onFocusChanged { focusOnTarget = it.hasFocus }

    TvSidePanel(onDismiss = onDismiss, width = 440.dp) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp))
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.fasterWhenHeld(listState),
        ) {
            if (playlists != null) {
                item(key = "playlists-heading") { PanelHeading("Playlists") }
                items(playlists.options, key = { "playlist-${it.id}" }) { option ->
                    ListItem(
                        selected = option.id == playlists.selected,
                        onClick = { onSelectPlaylists(option.id) },
                        headlineContent = { Text(option.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(key = "categories-heading") { PanelHeading("Categories", Modifier.padding(top = 12.dp)) }
            }
            if (sort != null) {
                item(key = "sort") {
                    ListItem(
                        selected = false,
                        onClick = { onSortChange(sort.next()) },
                        headlineContent = { Text("Sort") },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.Sort, null) },
                        trailingContent = { Text(sort.label, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (options.size > 12) {
                item(key = "filter") {
                    ListItem(
                        selected = false,
                        onClick = { editing = true },
                        headlineContent = { Text(if (filter.isBlank()) "Filter categories" else "Filter: $filter") },
                        leadingContent = { Icon(Icons.Filled.FilterList, null) },
                        modifier = Modifier.fillMaxWidth().then(if (focusId == null) target else Modifier),
                    )
                }
            }
            items(visible, key = { it.id }) { option ->
                ListItem(
                    selected = option.id == selected,
                    onClick = { onSelect(option.id) },
                    headlineContent = { Text(option.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = option.count?.let { { Text(formatCount(it), style = MaterialTheme.typography.labelMedium) } },
                    modifier = Modifier.fillMaxWidth().then(if (option.id == focusId) target else Modifier),
                )
            }
        }
    }
    // The panel's window can move focus to its first row (a playlist) as it opens, after the
    // request; so for its first few frames, focus goes back to the current category. Not when
    // the categories are only sorted again, which leaves focus on Sort.
    LaunchedEffect(filter, options.isEmpty()) {
        repeat(12) {
            withFrameNanos { }
            if (!focusOnTarget) runCatching { focus.requestFocus() }
        }
    }
    if (editing) {
        TvTextInputDialog(
            title = "Filter categories",
            initial = filter,
            onDone = {
                filter = it
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun PanelHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 12.dp, bottom = 4.dp),
    )
}

/** Side panel to pick one of [choices] (such as the order of a list), opening on the [selected] one. */
@Composable
fun <T> TvChoicePanel(
    title: String,
    choices: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    var focusOnTarget by remember { mutableStateOf(false) }
    TvSidePanel(onDismiss = onDismiss, width = 400.dp) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp))
        choices.forEach { choice ->
            val current = choice == selected
            ListItem(
                selected = current,
                onClick = { onSelect(choice) },
                headlineContent = { Text(label(choice)) },
                trailingContent = if (current) ({ Icon(Icons.Filled.Check, "Current") }) else null,
                modifier = Modifier.fillMaxWidth().then(
                    if (current) Modifier.focusRequester(focus).onFocusChanged { focusOnTarget = it.hasFocus } else Modifier
                ),
            )
        }
    }
    // As in TvCategoryPanel: the panel's window can take focus to its first row as it opens.
    LaunchedEffect(Unit) {
        repeat(12) {
            withFrameNanos { }
            if (!focusOnTarget) runCatching { focus.requestFocus() }
        }
    }
}
