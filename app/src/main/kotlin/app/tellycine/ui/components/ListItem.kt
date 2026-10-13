package app.tellycine.ui.components

import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material's [androidx.compose.material3.ListItem], safe in very narrow windows. Material's own
 * (1.3) gives its text whatever width the leading and trailing content and padding leave, without
 * stopping at zero, and throws when they leave less than none: a small window, or a narrow
 * split-screen pane beside a side rail. Narrower than [ListItemMinWidth], this one is laid out at
 * that width and cut off at the end instead; at any other width it's exactly Material's.
 */
@Composable
fun ListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    colors: ListItemColors = ListItemDefaults.colors(),
    tonalElevation: Dp = ListItemDefaults.Elevation,
    shadowElevation: Dp = ListItemDefaults.Elevation,
) {
    androidx.compose.material3.ListItem(
        headlineContent = headlineContent,
        modifier = Modifier.laidOutAtLeast(ListItemMinWidth).then(modifier),
        overlineContent = overlineContent,
        supportingContent = supportingContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        colors = colors,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
    )
}

/** Room for the widest leading and trailing content the app's rows have, plus their padding and some text. */
val ListItemMinWidth = 280.dp

/** Lays the content out at least [width] wide, cut off to the space it's given when that's narrower. */
private fun Modifier.laidOutAtLeast(width: Dp) = clipToBounds().layout { measurable, constraints ->
    val min = width.roundToPx()
    if (constraints.maxWidth >= min) {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    } else {
        val placeable = measurable.measure(constraints.copy(minWidth = min, maxWidth = min))
        layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(0, 0) }
    }
}
