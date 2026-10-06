package app.mosaic.ui.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.mosaic.AppContainer
import app.mosaic.core.ContentKind
import app.mosaic.data.db.CategoryVisibility
import app.mosaic.data.db.PlaylistEntity
import app.mosaic.ui.components.EmptyState
import app.mosaic.ui.components.formatCount
import app.mosaic.ui.components.mosaicViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryFilterViewModel(private val container: AppContainer, private val playlistId: Long) : ViewModel() {
    private val content = container.content

    val playlist: StateFlow<PlaylistEntity?> = flow { emit(container.playlists.get(playlistId)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _kind = MutableStateFlow(ContentKind.LIVE)
    val kind: StateFlow<ContentKind> = _kind.asStateFlow()

    /** null while loading. */
    val categories: StateFlow<List<CategoryVisibility>?> = _kind
        .flatMapLatest { content.categoryVisibility(playlistId, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectKind(kind: ContentKind) {
        _kind.value = kind
    }

    fun setVisible(categoryIds: List<String>, visible: Boolean) {
        val kind = _kind.value
        viewModelScope.launch { content.setCategoriesVisible(playlistId, kind, categoryIds, visible) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFilterScreen(playlistId: Long, onBack: () -> Unit) {
    val vm = mosaicViewModel(key = "categories-$playlistId") { c, _ -> CategoryFilterViewModel(c, playlistId) }
    val playlist by vm.playlist.collectAsStateWithLifecycle()
    val kind by vm.kind.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("") }

    val kinds = if (playlist?.vodEnabled == false) listOf(ContentKind.LIVE) else ContentKind.entries
    val visibleRows = remember(categories, filter) {
        val all = categories.orEmpty()
        if (filter.isBlank()) all else all.filter { it.category.name.contains(filter.trim(), ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Categories")
                            playlist?.let {
                                Text(it.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                )
                if (kinds.size > 1) {
                    PrimaryTabRow(selectedTabIndex = kinds.indexOf(kind).coerceAtLeast(0)) {
                        kinds.forEach { k ->
                            Tab(
                                selected = k == kind,
                                onClick = {
                                    filter = ""
                                    vm.selectKind(k)
                                },
                                text = {
                                    Text(
                                        when (k) {
                                            ContentKind.LIVE -> "Live TV"
                                            ContentKind.MOVIE -> "Movies"
                                            ContentKind.SERIES -> "Series"
                                        }
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        val rows = categories
        Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (rows != null && rows.isEmpty()) {
                EmptyState(
                    Icons.Filled.Category,
                    "No categories yet",
                    "Categories appear here once the playlist has been loaded.",
                )
                return@Column
            }
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                Text(
                    "Unticked categories are hidden from Live TV, Movies, Series, search and favourites. " +
                        "New categories from your provider are shown until you untick them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                val shown = rows.orEmpty().count { !it.hidden }
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "$shown of ${rows.orEmpty().size} shown",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    val ids = visibleRows.map { it.category.categoryId }
                    TextButton(onClick = { vm.setVisible(ids, true) }, enabled = ids.isNotEmpty()) {
                        Text(if (filter.isBlank()) "Tick all" else "Tick matches")
                    }
                    TextButton(onClick = { vm.setVisible(ids, false) }, enabled = ids.isNotEmpty()) {
                        Text(if (filter.isBlank()) "Untick all" else "Untick matches")
                    }
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(visibleRows, key = { it.category.categoryId }) { row ->
                        val visible = !row.hidden
                        ListItem(
                            modifier = Modifier.toggleable(
                                value = visible,
                                role = Role.Checkbox,
                                onValueChange = { vm.setVisible(listOf(row.category.categoryId), it) },
                            ),
                            leadingContent = { Checkbox(checked = visible, onCheckedChange = null) },
                            headlineContent = { Text(row.category.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            trailingContent = {
                                Text(
                                    formatCount(row.category.itemCount),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
