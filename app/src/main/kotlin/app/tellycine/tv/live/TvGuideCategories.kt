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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
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
private val TuckedWidth = 112.dp

/**
 * The guide's categories, as a column to the left of the channels. Tucked away it shows only the
 * start of each name, faded, so it's clear there is more to the left; Left from the channel
 * column opens it on the current category ([TvGuideCategoriesState.open]). OK picks a category
 * and returns to the channels, which [onSelect] changes; Right or Back return without changing.
 */
@Composable
fun TvGuideCategories(
    state: TvGuideCategoriesState,
    options: List<CategoryOption>,
    selected: String,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** What the tucked column fades into: the colour behind the guide. */
    fadeTo: Color = Color.Black,
) {
    val width by animateDpAsState(if (state.open) OpenWidth else TuckedWidth, label = "categories-width")
    val alpha by animateFloatAsState(if (state.open) 1f else 0.45f, label = "categories-alpha")
    val listState = rememberLazyListState()
    val selectedIndex = options.indexOfFirst { it.id == selected }.coerceAtLeast(0)
    var hasFocus by remember { mutableStateOf(false) }
    var focusedIndex by remember { mutableStateOf(-1) }

    // Opening scrolls to the current category and focuses it.
    LaunchedEffect(state.open) {
        if (!state.open) return@LaunchedEffect
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex }) {
            listState.scrollToItem((selectedIndex - 2).coerceAtLeast(0))
        }
        snapshotFlowOf { listState.layoutInfo.visibleItemsInfo.any { it.index == selectedIndex } }
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
            .graphicsLayer { this.alpha = alpha }
            .onFocusChanged { hasFocus = it.hasFocus }
            // Focus stays in the column at its ends; Right is the way back to the channels.
            .onPreviewKeyEvent { event ->
                if (!hasFocus || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> true
                    Key.DirectionUp -> focusedIndex <= 0
                    Key.DirectionDown -> focusedIndex >= options.lastIndex
                    else -> false
                }
            },
    ) {
        // Laid out at full width and anchored left, so the tucked column shows the start of each name.
        Column(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).width(OpenWidth).fillMaxHeight()) {
            Text(
                "Categories",
                style = MaterialTheme.typography.labelLarge,
                color = TvPalette.onSurfaceVariant,
                // As tall as the guide's row of times, so the first category lines up with the first channel.
                modifier = Modifier.height(GuideHeaderHeight).padding(start = 12.dp, top = 6.dp),
            )
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(options, key = { _, o -> o.id }) { index, option ->
                    val current = option.id == selected
                    Surface(
                        onClick = { onSelect(option.id) },
                        enabled = state.open,
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (it.isFocused) focusedIndex = index }
                            .then(if (index == selectedIndex) Modifier.focusRequester(state.focus) else Modifier),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                option.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (current) FontWeight.SemiBold else null,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            option.count?.let {
                                Text(formatCount(it), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }
        // Tucked away, the names fade out towards the channels.
        if (!state.open) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(48.dp)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, fadeTo.copy(alpha = 0.6f)))),
            )
        }
    }
}

/** Suspends until [condition] is true, checking each frame's state. */
private suspend fun snapshotFlowOf(condition: () -> Boolean) {
    androidx.compose.runtime.snapshotFlow(condition).filter { it }.first()
}
