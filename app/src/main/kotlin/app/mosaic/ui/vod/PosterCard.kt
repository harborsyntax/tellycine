package app.mosaic.ui.vod

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mosaic.ui.components.Artwork

@Composable
fun PosterCard(
    title: String,
    poster: String?,
    meta: String?,
    rating: Double?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box {
                Artwork(
                    url = poster,
                    fallbackText = title,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                )
                if (rating != null && rating > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Icon(Icons.Filled.Star, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                "%.1f".format(rating),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(start = 2.dp),
                            )
                        }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 4.dp).padding(bottom = 6.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!meta.isNullOrBlank()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
