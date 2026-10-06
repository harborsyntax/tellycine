package app.mosaic.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mosaic.core.ContentKind
import app.mosaic.data.db.HistoryEntity
import app.mosaic.data.repo.ContentRepository
import app.mosaic.player.play
import app.mosaic.ui.components.Artwork
import app.mosaic.ui.components.formatDuration
import app.mosaic.ui.components.mosaicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    kind: ContentKind,
    playlistId: Long,
    streamId: String,
    onBack: () -> Unit,
) {
    val vm = mosaicViewModel(key = "detail-$kind-$playlistId-$streamId") { c, _ ->
        DetailViewModel(c, kind, playlistId, streamId)
    }
    val info by vm.info.collectAsStateWithLifecycle()
    val favorite by vm.isFavorite.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val season by vm.selectedSeason.collectAsStateWithLifecycle()
    val next by vm.nextEpisode.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 720.dp
            val seasonEpisodes = remember(info.episodes, season) { info.episodes.filter { it.season == season } }

            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item(key = "header") {
                    Header(info, wide)
                }
                item(key = "actions") {
                    Column(
                        Modifier.padding(horizontal = 16.dp).widthIn(max = 900.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            when (kind) {
                                ContentKind.MOVIE -> {
                                    val h = history[ContentRepository.movieKey(streamId)]
                                    val resume = h?.resumePosition() ?: 0L
                                    PlayButton(
                                        label = if (resume > 0) "Resume" else "Play",
                                        progress = h?.progress(),
                                        enabled = info.stream != null,
                                    ) { vm.moviePlayRequest(resume)?.let(context::play) }
                                }
                                else -> {
                                    val n = next
                                    if (n != null) {
                                        val (ep, h) = n
                                        val resume = h?.resumePosition() ?: 0L
                                        PlayButton(
                                            label = "${if (resume > 0) "Resume" else "Play"} S${ep.season} E${ep.number}",
                                            progress = h?.progress(),
                                            enabled = true,
                                        ) { context.play(vm.episodePlayRequest(ep, resume)) }
                                    }
                                }
                            }
                            FilledTonalIconToggleButton(checked = favorite, onCheckedChange = { vm.toggleFavorite() }) {
                                if (favorite) Icon(Icons.Filled.Favorite, "Remove from favourites")
                                else Icon(Icons.Filled.FavoriteBorder, "Add to favourites")
                            }
                        }
                        if (info.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        info.error?.let { error ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        info.plot?.let { plot -> ExpandableText(plot) }
                        info.director?.takeIf { it.isNotBlank() }?.let { Credit("Director", it) }
                        info.cast?.takeIf { it.isNotBlank() }?.let { Credit("Cast", it) }
                    }
                }

                if (info.seasons.isNotEmpty()) {
                    item(key = "seasons") {
                        val seasons = info.seasons.keys.toList()
                        val index = seasons.indexOf(season).coerceAtLeast(0)
                        if (seasons.size > 1) {
                            ScrollableTabRow(
                                selectedTabIndex = index,
                                edgePadding = 16.dp,
                                modifier = Modifier.padding(top = 16.dp),
                            ) {
                                seasons.forEach { s ->
                                    Tab(
                                        selected = s == season,
                                        onClick = { vm.selectSeason(s) },
                                        text = { Text(info.seasons[s] ?: "Season $s") },
                                    )
                                }
                            }
                        } else {
                            Text(
                                info.seasons[seasons.first()] ?: "Episodes",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
                            )
                        }
                    }
                    items(seasonEpisodes, key = { "ep-${it.id}" }) { ep ->
                        val h = history[ContentRepository.episodeKey(ep.id)]
                        EpisodeRow(ep, h, wide) {
                            context.play(vm.episodePlayRequest(ep, h?.resumePosition() ?: 0L))
                        }
                    }
                } else if (kind == ContentKind.SERIES && !info.loading && info.stream != null) {
                    item(key = "no-episodes") {
                        Text(
                            "No episodes found.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }

        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.padding(6.dp))
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun Header(info: DetailInfo, wide: Boolean) {
    val surface = MaterialTheme.colorScheme.surface
    Box(Modifier.fillMaxWidth()) {
        // Backdrop with a scrim fading into the page.
        Box(Modifier.fillMaxWidth().aspectRatio(if (wide) 21f / 9f else 16f / 10f)) {
            Artwork(
                url = info.backdrop ?: info.poster,
                fallbackText = "",
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to surface.copy(alpha = 0.25f), 0.6f to surface.copy(alpha = 0.6f), 1f to surface)
                )
            )
        }
        Row(
            Modifier
                .padding(top = if (wide) 160.dp else 120.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Artwork(
                url = info.poster,
                fallbackText = info.title,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.width(if (wide) 180.dp else 120.dp).aspectRatio(2f / 3f),
            )
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    info.title,
                    style = if (wide) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                MetaChips(info)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaChips(info: DetailInfo) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        info.rating?.let { rating ->
            AssistChip(
                onClick = {},
                label = { Text("%.1f".format(rating)) },
                leadingIcon = { Icon(Icons.Filled.Star, null, Modifier.size(16.dp)) },
            )
        }
        info.meta.filter { it.isNotBlank() }.forEach { m ->
            AssistChip(onClick = {}, label = { Text(m, maxLines = 1, overflow = TextOverflow.Ellipsis) })
        }
    }
}

@Composable
private fun PlayButton(label: String, progress: Float?, enabled: Boolean, onClick: () -> Unit) {
    Column {
        Button(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)) {
            Icon(Icons.Filled.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
        if (progress != null && progress > 0f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.padding(top = 6.dp, start = 8.dp, end = 8.dp).width(120.dp),
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun ExpandableText(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = if (expanded) Int.MAX_VALUE else 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clickable { expanded = !expanded },
    )
}

@Composable
private fun Credit(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EpisodeRow(ep: EpisodeUi, history: HistoryEntity?, wide: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.width(if (wide) 200.dp else 140.dp)) {
            Artwork(
                url = ep.image,
                fallbackText = "E${ep.number}",
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
            history?.progress()?.let { p ->
                LinearProgressIndicator(
                    progress = { p },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(6.dp).height(3.dp),
                    drawStopIndicator = {},
                )
            }
        }
        Column(Modifier.weight(1f).heightIn(min = 56.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${ep.number}. ${ep.title}",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            ep.durationSecs?.takeIf { it > 0 }?.let {
                Text(formatDuration(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ep.plot?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (wide) 3 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun HistoryEntity.progress(): Float? =
    if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else null

/** Resume position, or 0 when the item was (almost) finished. */
private fun HistoryEntity.resumePosition(): Long =
    if (durationMs > 0 && positionMs > durationMs * 0.95) 0L else positionMs
