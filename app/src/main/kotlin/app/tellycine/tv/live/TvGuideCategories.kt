package app.tellycine.tv.live

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.tellycine.data.repo.PlaylistFilter
import app.tellycine.data.settings.CategorySort
import app.tellycine.tv.TvPalette
import app.tellycine.ui.components.CategoryOption
import app.tellycine.ui.components.formatCount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter

/** Where the guide's categories column is: tucked away beside the channels, or open. */
class TvGuideCategoriesState {
    var open by mutableStateOf(false)
        internal set
    internal val focus = FocusRequester()

    /** Opens the column with the current category focused (Left from the channel column). */
    fun open() {
        open = true
    }
}

private val OpenWidth = 280.dp

/** Tucked away, the column is a thin edge: little width taken, but clearly something to the left. */
private val TuckedWidth = 16.dp

/**
 * The guide's categories, as a column to the left of the channels. Tucked away it's a thin edge
 * with an arrow, so it's clear there is more to the left; Left from the channel column opens it
 * on the current category ([TvGuideCategoriesState.open]). OK picks a category and returns to
 * the channels, which [onSelect] changes; Right or Back return without changing. With more than
 * one playlist, the [playlists] to show come first, and picking one also returns to the channels.
 * Given a [sort], the categories start with a Sort button, which switches between the provider's
 * order and A–Z ([onSortChange]) and leaves the column open.
 */
@Composable
fun TvGuideCategories(
    state: TvGuideCategoriesState,
    options: List<CategoryOption>,
    selected: String,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    playlists: PlaylistFilter? = null,
    onSelectPlaylists: (Long) -> Unit = {},
    sort: CategorySort? = null,
    onSortChange: (CategorySort) -> Unit = {},
) {
    val width by animateDpAsState(if (state.open) OpenWidth else TuckedWidth, label = "categories-width")
    // The names fade in as the column opens; the edge fades out.
    val namesAlpha by animateFloatAsState(if (state.open) 1f else 0f, label = "categories-alpha")
    val listState = rememberLazyListState()
    // Focusable rows are numbered playlists first, then the Sort button and the categories; the
    // list also has a heading between them, which [listIndex] allows for.
    val playlistCount = playlists?.options?.size ?: 0
    val firstCategory = playlistCount + if (sort != null) 1 else 0
    val listIndex = { row: Int -> if (playlists != null && row >= playlistCount) row + 1 else row }
    val selectedIndex = firstCategory + options.indexOfFirst { it.id == selected }.coerceAtLeast(0)
    val lastIndex = firstCategory + options.lastIndex
    var hasFocus by remember { mutableStateOf(false) }
    var focusedIndex by remember { mutableStateOf(-1) }

    // Opening scrolls to the current category and focuses it.
    LaunchedEffect(state.open) {
        if (!state.open) return@LaunchedEffect
        val target = listIndex(selectedIndex)
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == target }) {
            listState.scrollToItem((target - 2).coerceAtLeast(0))
        }
        snapshotFlowOf { listState.layoutInfo.visibleItemsInfo.any { it.index == target } }
        runCatching { state.focus.requestFocus() }
    }
    // Moving focus out (Right, to the channels) tucks the column away again.
    LaunchedEffect(hasFocus) {
        if (!hasFocus && state.open) state.open = false
    }
    BackHandler(enabled = state.open) {
        state.open = false
        onClose()
    }

    Box(
        modifier
            .width(width)
            .fillMaxHeight()
            .clipToBounds()
            .onFocusChanged { hasFocus = it.hasFocus }
            // Focus stays in the column at its ends; Right is the way back to the channels.
            .onPreviewKeyEvent { event ->
                if (!hasFocus || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> true
                    Key.DirectionUp -> focusedIndex <= 0
                    Key.DirectionDown -> focusedIndex >= lastIndex
                    else -> false
                }
            },
    ) {
        // Laid out at full width and anchored left, so opening reveals the names from the start.
        Column(
            Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .width(OpenWidth)
                .fillMaxHeight()
                .graphicsLayer { alpha = namesAlpha },
        ) {
            // As tall as the guide's row of times, so the first category lines up with the first channel.
            ColumnHeading(if (playlists != null) "Playlists" else "Categories", Modifier.height(GuideHeaderHeight))
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (playlists != null) {
                    itemsIndexed(playlists.options, key = { _, o -> "playlist-${o.id}" }) { index, option ->
                        ColumnRow(
                            name = option.name,
                            count = null,
                            current = option.id == playlists.selected,
                            enabled = state.open,
                            onClick = { onSelectPlaylists(option.id) },
                            modifier = Modifier.onFocusChanged { if (it.isFocused) focusedIndex = index },
                        )
                    }
                    item(key = "categories-heading") { ColumnHeading("Categories", Modifier.padding(top = 8.dp)) }
                }
                if (sort != null) {
                    item(key = "sort") {
                        SortRow(
                            sort = sort,
                            enabled = state.open,
                            onClick = { onSortChange(sort.next()) },
                            modifier = Modifier.onFocusChanged { if (it.isFocused) focusedIndex = playlistCount },
                        )
                    }
                }
                itemsIndexed(options, key = { _, o -> o.id }) { i, option ->
                    val index = firstCategory + i
                    ColumnRow(
                        name = option.name,
                        count = option.count,
                        current = option.id == selected,
                        enabled = state.open,
                        onClick = { onSelect(option.id) },
                        modifier = Modifier
                            .onFocusChanged { if (it.isFocused) focusedIndex = index }
                            .then(if (index == selectedIndex) Modifier.focusRequester(state.focus) else Modifier),
                    )
                }
            }
        }
        // Tucked away: a thin edge beside the channels, with an arrow pointing to the categories.
        if (namesAlpha < 1f) {
            Box(
                Modifier
                    .padding(top = GuideHeaderHeight)
                    .width(TuckedWidth)
                    .fillMaxHeight()
                    .graphicsLayer { alpha = 1f - namesAlpha }
                    .background(TvPalette.surfaceHigh.copy(alpha = 0.7f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.TopCenter,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    "Categories",
                    Modifier.padding(top = 10.dp).size(16.dp),
                    tint = TvPalette.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ColumnHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = TvPalette.onSurfaceVariant,
        modifier = modifier.padding(start = 12.dp, top = 6.dp, bottom = 4.dp),
    )
}

/** The other order, for a button that switches between the two. */
fun CategorySort.next(): CategorySort = if (this == CategorySort.NAME) CategorySort.PROVIDER else CategorySort.NAME

/** The Sort button at the top of the categories: the order they're in, and OK for the other one. */
@Composable
private fun SortRow(sort: CategorySort, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = TvPalette.onSurface,
            focusedContainerColor = TvPalette.focus,
            focusedContentColor = TvPalette.onFocus,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = TvPalette.onSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "Sort: ${sort.label}" },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(18.dp))
            Text(
                "Sort",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                modifier = Modifier.padding(start = 10.dp).weight(1f),
            )
            Text(sort.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** A playlist or category in the column, marked when it's the one showing. */
@Composable
private fun ColumnRow(name: String, count: Int?, current: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (current) Color.White.copy(alpha = 0.1f) else Color.Transparent,
            contentColor = if (current) TvPalette.primary else TvPalette.onSurface,
            focusedContainerColor = TvPalette.focus,
            focusedContentColor = TvPalette.onFocus,
            disabledContainerColor = if (current) Color.White.copy(alpha = 0.1f) else Color.Transparent,
            disabledContentColor = if (current) TvPalette.primary else TvPalette.onSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth().semantics { if (current) selected = true },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (current) FontWeight.SemiBold else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            count?.let {
                Text(formatCount(it), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Suspends until [condition] is true, checking each frame's state. */
private suspend fun snapshotFlowOf(condition: () -> Boolean) {
    androidx.compose.runtime.snapshotFlow(condition).filter { it }.first()
}
