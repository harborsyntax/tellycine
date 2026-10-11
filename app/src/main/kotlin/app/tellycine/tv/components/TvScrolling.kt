package app.tellycine.tv.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.lazy.LazyListState

/**
 * Scrolling for a page whose first item ([topKey]) holds its details and main buttons: focus
 * anywhere in that item brings the page back to its top, so nothing above the buttons is cut off.
 * Everything else scrolls as [rest] does.
 *
 * A TV's own scrolling keeps the focused item a third of the way down the screen, which on a page
 * with plenty below its buttons (a series' episodes) pushes the title off the top.
 */
@OptIn(ExperimentalFoundationApi::class)
class TopFirstScrolling(
    private val list: LazyListState,
    private val topKey: Any,
    private val rest: BringIntoViewSpec,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val info = list.layoutInfo
        val top = info.visibleItemsInfo.firstOrNull { it.key == topKey }
        if (top != null) {
            // Where the item is on screen: the list measures from inside its content padding.
            val start = top.offset - info.viewportStartOffset
            // At the top of the page, the item is at offset 0.
            if (offset >= start && offset < start + top.size) return top.offset.toFloat()
        }
        return rest.calculateScrollDistance(offset, size, containerSize)
    }
}
