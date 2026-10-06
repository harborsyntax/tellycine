package app.mosaic.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mosaic.core.ContentKind
import app.mosaic.data.db.CategoryEntity
import app.mosaic.data.repo.Categories

data class CategoryOption(val id: String, val name: String, val count: Int?)

fun categoryOptions(kind: ContentKind, categories: List<CategoryEntity>, total: Int?): List<CategoryOption> {
    val allLabel = when (kind) {
        ContentKind.LIVE -> "All channels"
        ContentKind.MOVIE -> "All movies"
        ContentKind.SERIES -> "All series"
    }
    return buildList {
        add(CategoryOption(Categories.ALL, allLabel, total))
        add(CategoryOption(Categories.FAVORITES, "Favourites", null))
        categories.forEach { add(CategoryOption(it.categoryId, it.name, it.itemCount)) }
    }
}

/** Horizontal chip row for compact screens, with a button that opens the full list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryChips(
    options: List<CategoryOption>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(selected, options.size) {
        val index = options.indexOfFirst { it.id == selected }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { showSheet = true }, modifier = Modifier.padding(start = 12.dp)) {
            Icon(Icons.AutoMirrored.Filled.List, "All categories")
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(options, key = { it.id }) { option ->
                FilterChip(
                    selected = option.id == selected,
                    onClick = { onSelect(option.id) },
                    label = { Text(option.name, maxLines = 1) },
                )
            }
        }
    }
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        ) {
            CategoryList(
                options = options,
                selected = selected,
                onSelect = {
                    onSelect(it)
                    showSheet = false
                },
                modifier = Modifier.fillMaxWidth().height(560.dp),
            )
        }
    }
}

/** Filterable vertical category list, used as a side pane on tablets and in the sheet on phones. */
@Composable
fun CategoryList(
    options: List<CategoryOption>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableStateOf("") }
    val visible = remember(options, filter) {
        if (filter.isBlank()) options else options.filter { it.name.contains(filter.trim(), ignoreCase = true) }
    }
    Column(modifier) {
        if (options.size > 12) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text("Filter categories") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (filter.isNotEmpty()) IconButton(onClick = { filter = "" }) { Icon(Icons.Filled.Close, "Clear") }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            items(visible, key = { it.id }) { option ->
                NavigationDrawerItem(
                    label = { Text(option.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    badge = if (option.count != null) {
                        { Text(formatCount(option.count), style = MaterialTheme.typography.labelMedium) }
                    } else {
                        null
                    },
                    selected = option.id == selected,
                    onClick = { onSelect(option.id) },
                )
            }
        }
    }
}

/** Fixed-width side pane wrapper for list-detail layouts on large screens. */
@Composable
fun CategoryPane(
    options: List<CategoryOption>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    CategoryList(options, selected, onSelect, Modifier.width(300.dp).fillMaxHeight())
}
