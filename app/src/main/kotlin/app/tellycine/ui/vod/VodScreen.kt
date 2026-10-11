package app.tellycine.ui.vod

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import app.tellycine.core.ContentKind
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.repo.Categories
import app.tellycine.ui.components.CategoryChips
import app.tellycine.ui.components.CategoryPane
import app.tellycine.ui.components.EmptyState
import app.tellycine.ui.components.categoryOptions
import app.tellycine.ui.components.tellycineViewModel
import app.tellycine.ui.home.PlaylistTopBar
import app.tellycine.ui.home.SessionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VodScreen(
    kind: ContentKind,
    session: SessionState,
    onSelectPlaylist: (Long) -> Unit,
    onSearch: () -> Unit,
    onOpen: (StreamEntity) -> Unit,
) {
    val vm = tellycineViewModel(key = "vod-$kind") { c, _ -> VodViewModel(c, kind) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selectedCategory.collectAsStateWithLifecycle()
    val pagingItems = vm.items.collectAsLazyPagingItems()
    val scope by vm.scope.collectAsStateWithLifecycle()
    val playlistFilter by vm.playlistFilter.collectAsStateWithLifecycle()
    val total = scope.playlists.sumOf { if (kind == ContentKind.MOVIE) it.movieCount else it.seriesCount }
    val options = remember(categories, total) {
        categoryOptions(kind, categories, if (categories.isEmpty()) total else categories.sumOf { it.itemCount })
    }
    val title = if (kind == ContentKind.MOVIE) "Movies" else "Series"
    val icon = if (kind == ContentKind.MOVIE) Icons.Filled.Movie else Icons.Filled.VideoLibrary
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        topBar = {
            PlaylistTopBar(
                session,
                onSelectPlaylist = onSelectPlaylist,
                onSearch = onSearch,
                scrollBehavior = scrollBehavior,
                // With more than one playlist, the choice is beside the categories instead.
                showPlaylist = playlistFilter == null,
            )
        },
        contentWindowInsets = WindowInsets(0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val twoPane = maxWidth >= 840.dp
            val grid = @Composable { modifier: Modifier ->
                when {
                    pagingItems.itemCount == 0 && pagingItems.loadState.refresh is LoadState.Loading ->
                        Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    pagingItems.itemCount == 0 && selected == Categories.FAVORITES -> EmptyState(
                        Icons.Filled.FavoriteBorder,
                        "No favourites yet",
                        "Open a title and tap the heart to save it here.",
                        modifier,
                    )
                    pagingItems.itemCount == 0 -> EmptyState(
                        icon,
                        if (session.activeProgress != null) "Loading…" else "Nothing here",
                        session.activeProgress?.message
                            ?: "${if (scope.combined) "These playlists have" else "This playlist has"} no ${title.lowercase()} in this category.",
                        modifier,
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (maxWidth >= 600.dp) 150.dp else 112.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = modifier.fillMaxSize(),
                    ) {
                        items(pagingItems.itemCount, key = pagingItems.itemKey { "${it.playlistId}:${it.streamId}" }) { index ->
                            val item = pagingItems[index]
                            if (item == null) {
                                PosterPlaceholder()
                            } else {
                                PosterCard(
                                    title = item.name,
                                    poster = item.logo,
                                    meta = listOfNotNull(item.year, item.genre?.substringBefore(',')).joinToString(" · "),
                                    rating = item.rating,
                                    onClick = { onOpen(item) },
                                    source = vm.sourceOf(item),
                                )
                            }
                        }
                    }
                }
            }

            if (session.active == null) {
                EmptyState(icon, "No playlist yet", "Add an M3U playlist or Xtream Codes account from the Playlists tab.")
            } else if (twoPane) {
                Row(Modifier.fillMaxSize()) {
                    CategoryPane(options, selected, vm::selectCategory, playlistFilter, vm::selectPlaylists)
                    VerticalDivider()
                    grid(Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    CategoryChips(options, selected, vm::selectCategory, Modifier.padding(bottom = 4.dp), playlistFilter, vm::selectPlaylists)
                    grid(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PosterPlaceholder() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
    ) {}
}
