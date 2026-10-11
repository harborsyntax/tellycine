package app.tellycine.ui.live

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tellycine.data.db.ChannelRow
import app.tellycine.data.db.NowPlaying
import app.tellycine.ui.components.Artwork
import app.tellycine.ui.components.SourceLabel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelItem(
    row: ChannelRow,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    hasCatchup: Boolean = false,
    /** The playlist the channel is from, when the list mixes playlists. */
    source: String? = null,
    /** What's on now, at [now] (see [rememberNowPlaying]). */
    playing: NowPlaying? = null,
    now: Long = System.currentTimeMillis(),
) {
    val s = row.stream
    ListItem(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        leadingContent = {
            Artwork(
                url = s.logo,
                fallbackText = s.name,
                contentScale = ContentScale.Fit,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(width = 64.dp, height = 44.dp),
            )
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (hasCatchup) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = "Catch-up available",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp).size(16.dp),
                    )
                }
            }
        },
        supportingContent = if (playing?.nowTitle != null || source != null) {
            {
                Column {
                    playing?.nowTitle?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    source?.let { SourceLabel(it, Modifier.padding(top = 2.dp)) }
                    val start = playing?.nowStart
                    val stop = playing?.nowStop
                    if (start != null && stop != null && stop > start) {
                        val progress = ((now - start).toFloat() / (stop - start)).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp),
                            drawStopIndicator = {},
                        )
                    }
                }
            }
        } else {
            null
        },
        trailingContent = {
            IconButton(onClick = onToggleFavorite) {
                if (row.isFavorite) {
                    Icon(Icons.Filled.Favorite, "Remove from favourites", tint = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Filled.FavoriteBorder, "Add to favourites")
                }
            }
        },
    )
}

/** Shimmer-free placeholder row shown while a page loads. */
@Composable
fun ChannelPlaceholder() {
    ListItem(
        leadingContent = {
            Artwork(null, "", shape = RoundedCornerShape(10.dp), modifier = Modifier.size(width = 64.dp, height = 44.dp))
        },
        headlineContent = { Text("", style = MaterialTheme.typography.bodyLarge) },
    )
}
