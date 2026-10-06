package app.mosaic.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mosaic.R

/**
 * The Mosaic logo: the tile-grid mark followed by the lowercase wordmark.
 * The wordmark follows the theme's on-surface colour; the mark keeps its brand colours.
 */
@Composable
fun MosaicLogo(
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    showMark: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(height * 0.4f),
    ) {
        if (showMark) {
            Image(painterResource(R.drawable.mosaic_mark), contentDescription = null, modifier = Modifier.size(height))
        }
        // Wordmark viewport is 314×80 with the x-height filling the lower half, so scale from its height.
        Icon(
            painterResource(R.drawable.mosaic_wordmark),
            contentDescription = "Mosaic",
            tint = color,
            modifier = Modifier.height(height * 1.15f),
        )
    }
}
