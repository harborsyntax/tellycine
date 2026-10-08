package app.tellycine.ui.components

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
import app.tellycine.R

/**
 * The Tellycine logo: the tile-grid mark followed by the wordmark.
 * The wordmark follows the theme's on-surface colour; the mark keeps its brand colours.
 */
@Composable
fun TellycineLogo(
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    showMark: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(height * 0.32f),
    ) {
        if (showMark) {
            Image(painterResource(R.drawable.tellycine_mark), contentDescription = null, modifier = Modifier.size(height))
        }
        // The capitals are 3/4 of the mark's height. The drawable is padded above to balance the
        // "y" descender, so its box is taller than the letters and stays centred on the mark.
        Icon(
            painterResource(R.drawable.tellycine_wordmark),
            contentDescription = "Tellycine",
            tint = color,
            modifier = Modifier.height(height * WORDMARK_HEIGHT),
        )
    }
}

/** Wordmark drawable height relative to the mark (capitals at 3/4, see tellycine_wordmark.xml). */
private const val WORDMARK_HEIGHT = 1.1527f
