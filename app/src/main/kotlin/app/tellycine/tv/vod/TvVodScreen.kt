package app.tellycine.tv.vod

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import app.tellycine.core.ContentKind
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.repo.Categories
import app.tellycine.tv.components.RequestFocusOnStart
import app.tellycine.tv.components.TvActionButton
import app.tellycine.tv.components.TvCategoryPanel
import app.tellycine.tv.components.TvEmptyState
import app.tellycine.tv.components.TvHeader
import app.tellycine.tv.components.TvPosterCard
import app.tellycine.tv.components.TvSpacing
import app.tellycine.ui.components.categoryOptions
import app.tellycine.ui.components.tellycineViewModel
import app.tellycine.ui.home.SessionState
import app.tellycine.ui.vod.VodViewModel

/** Movies or series as a poster grid, one category at a time. Left from the first column opens the categories. */
@Composable
fun TvVodScreen(kind: ContentKind, session: SessionState, onOpen: (StreamEntity) -> Unit) {
    val vm = tellycineViewModel(key = "vod-$kind") { c, _ -> VodViewModel(c, kind) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selectedCategory.collectAsStateWithLifecycle()
    val items = vm.items.collectAsLazyPagingItems()
    val scope by vm.scope.collectAsStateWithLifecycle()
    val playlistFilter by vm.playlistFilter.collectAsStateWithLifecycle()
    val total = scope.playlists.sumOf { if (kind == ContentKind.MOVIE) it.movieCount else it.seriesCount }
    val options = remember(categories, total) {
        categoryOptions(kind, categories, if (categories.isEmpty()) total else categories.sumOf { it.itemCount })
    }
    val title = if (kind == ContentKind.MOVIE) "Movies" else "Series"
    val icon = if (kind == ContentKind.MOVIE) Icons.Filled.Movie else Icons.Filled.VideoLibrary
    var showCategories by remember { mutableStateOf(false) }
    val gridFocus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    // The poster last focused, so coming back from a title's page lands on it again.
    var lastFocused by rememberSaveable(selected) { mutableIntStateOf(0) }
    var shownCategory by rememberSaveable { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected != shownCategory) {
            shownCategory = selected
            gridState.scrollToItem(0)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TvHeader(
            title = title,
            subtitle = scope.label.takeIf { session.active != null },
            actions = {
                if (session.active != null) {
                    TvActionButton(
                        options.firstOrNull { it.id == selected }?.name ?: "All",
                        onClick = { showCategories = true },
                        icon = Icons.Filled.Category,
                    )
                }
            },
        )
        val body = Modifier.weight(1f).fillMaxWidth()
        when {
            session.active == null -> TvEmptyState(icon, "No playlist yet", "Add a playlist from Playlists in the menu.", body)
            items.itemCount == 0 && items.loadState.refresh is LoadState.Loading ->
                Box(body, Alignment.Center) { CircularProgressIndicator() }
            items.itemCount == 0 && selected == Categories.FAVORITES -> TvEmptyState(
                Icons.Filled.FavoriteBorder,
                "No favourites yet",
                "Open a title and choose Add to favourites to save it here.",
                body,
            )
            items.itemCount == 0 -> TvEmptyState(
                icon,
                if (session.activeProgress != null) "Loading…" else "Nothing here",
                session.activeProgress?.message
                    ?: "${if (scope.combined) "These playlists have" else "This playlist has"} no ${title.lowercase()} in this category.",
                body,
                actionLabel = "Choose a category",
                onAction = { showCategories = true },
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                state = gridState,
                contentPadding = TvSpacing.content,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = body
                    .focusRestorer()
                    // Left from the first column opens the categories; Back opens the menu.
                    .focusProperties {
                        onExit = {
                            if (requestedFocusDirection == FocusDirection.Left) {
                                showCategories = true
                                cancelFocusChange()
                            }
                        }
                    },
            ) {
                items(items.itemCount, key = items.itemKey { "${it.playlistId}:${it.streamId}" }) { index ->
                    val item = items[index]
                    if (item == null) {
                        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                    } else {
                        TvPosterCard(
                            title = item.name,
                            poster = item.logo,
                            meta = listOfNotNull(item.year, item.genre?.substringBefore(',')).joinToString(" · "),
                            rating = item.rating,
                            onClick = { onOpen(item) },
                            source = vm.sourceOf(item),
                            modifier = Modifier
                                .onFocusChanged { if (it.hasFocus) lastFocused = index }
                                .then(if (index == lastFocused) Modifier.focusRequester(gridFocus) else Modifier),
                        )
                    }
                }
            }
        }
    }
    RequestFocusOnStart(gridFocus, items.itemCount > 0)

    if (showCategories) {
        TvCategoryPanel(
            title = "$title categories",
            options = options,
            selected = selected,
            onSelect = {
                vm.selectCategory(it)
                showCategories = false
            },
            onDismiss = { showCategories = false },
            playlists = playlistFilter,
            onSelectPlaylists = {
                vm.selectPlaylists(it)
                showCategories = false
            },
        )
    }
}
