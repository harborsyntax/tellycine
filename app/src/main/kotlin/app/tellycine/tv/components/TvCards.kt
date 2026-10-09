package app.tellycine.tv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.compose.foundation.BorderStroke
import app.tellycine.tv.TvPalette
import app.tellycine.ui.components.Artwork

private val CardShape = RoundedCornerShape(12.dp)

@Composable
private fun focusBorder() = CardDefaults.border(
    focusedBorder = Border(BorderStroke(3.dp, TvPalette.focus), shape = CardShape),
)

/** A movie or series poster with its title underneath. */
@Composable
fun TvPosterCard(
    title: String,
    poster: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    meta: String? = null,
    rating: Double? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Card(
            onClick = onClick,
            shape = CardDefaults.shape(CardShape),
            border = focusBorder(),
            scale = CardDefaults.scale(focusedScale = 1.06f),
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).semantics { contentDescription = title }.okClicks(onClick, onLongClick),
        ) {
            Box(Modifier.fillMaxSize()) {
                Artwork(url = poster, fallbackText = title, shape = CardShape, modifier = Modifier.fillMaxSize())
                if (rating != null && rating > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        colors = SurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.7f)),
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Star, null, Modifier.size(12.dp), tint = TvPalette.primary)
                            Text("%.1f".format(rating), style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(start = 2.dp))
                        }
                    }
                }
            }
        }
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!meta.isNullOrBlank()) {
            Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** A channel's logo on a tile with its name underneath. */
@Composable
fun TvChannelTile(
    name: String,
    logo: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 168.dp,
    subtitle: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Card(
            onClick = onClick,
            shape = CardDefaults.shape(CardShape),
            border = focusBorder(),
            scale = CardDefaults.scale(focusedScale = 1.06f),
            colors = CardDefaults.colors(containerColor = TvPalette.surfaceHigh),
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).semantics { contentDescription = name }.okClicks(onClick, onLongClick),
        ) {
            Box(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
                Artwork(url = logo, fallbackText = name, contentScale = ContentScale.Fit, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxSize())
            }
        }
        Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A wide card for something part-watched: artwork, title, and how far through it is. */
@Composable
fun TvProgressCard(
    title: String,
    subtitle: String?,
    artwork: String?,
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 260.dp,
    onLongClick: (() -> Unit)? = null,
) {
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Card(
            onClick = onClick,
            shape = CardDefaults.shape(CardShape),
            border = focusBorder(),
            scale = CardDefaults.scale(focusedScale = 1.05f),
            colors = CardDefaults.colors(containerColor = TvPalette.surfaceHigh),
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).semantics { contentDescription = title }.okClicks(onClick, onLongClick),
        ) {
            Box(Modifier.fillMaxSize()) {
                Artwork(url = artwork, fallbackText = title, shape = CardShape, modifier = Modifier.fillMaxSize())
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = TvPalette.primary,
                        trackColor = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp),
                    )
                }
            }
        }
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
