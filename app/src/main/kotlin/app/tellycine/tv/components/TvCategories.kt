package app.tellycine.tv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.tellycine.ui.components.CategoryOption
import app.tellycine.ui.components.formatCount

/**
 * Side panel listing a playlist's categories. Opens on the current one; long lists get a filter.
 */
@Composable
fun TvCategoryPanel(
    title: String,
    options: List<CategoryOption>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    val visible = remember(options, filter) {
        if (filter.isBlank()) options else options.filter { it.name.contains(filter.trim(), ignoreCase = true) }
    }
    // Focus starts on the current category, or the first one when it is filtered out.
    val focusId = visible.firstOrNull { it.id == selected }?.id ?: visible.firstOrNull()?.id
    val startIndex = visible.indexOfFirst { it.id == focusId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (startIndex - 3).coerceAtLeast(0))
    val focus = remember { FocusRequester() }

    TvSidePanel(onDismiss = onDismiss, width = 440.dp) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp))
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (options.size > 12) {
                item(key = "filter") {
                    ListItem(
                        selected = false,
                        onClick = { editing = true },
                        headlineContent = { Text(if (filter.isBlank()) "Filter categories" else "Filter: $filter") },
                        leadingContent = { Icon(Icons.Filled.FilterList, null) },
                        modifier = Modifier.fillMaxWidth().then(if (focusId == null) Modifier.focusRequester(focus) else Modifier),
                    )
                }
            }
            items(visible, key = { it.id }) { option ->
                ListItem(
                    selected = option.id == selected,
                    onClick = { onSelect(option.id) },
                    headlineContent = { Text(option.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = option.count?.let { { Text(formatCount(it), style = MaterialTheme.typography.labelMedium) } },
                    modifier = Modifier.fillMaxWidth().then(if (option.id == focusId) Modifier.focusRequester(focus) else Modifier),
                )
            }
        }
    }
    RequestFocusOnStart(focus, visible)
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
