package app.tellycine.tv.detail

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import app.tellycine.core.ContentKind
import app.tellycine.data.db.DownloadEntity
import app.tellycine.data.db.DownloadStatus
import app.tellycine.data.db.HistoryEntity
import app.tellycine.data.repo.ContentRepository
import app.tellycine.data.repo.DownloadRepository
import app.tellycine.player.openTrailer
import app.tellycine.player.play
import app.tellycine.tv.TvArtworkTint
import app.tellycine.tv.TvPalette
import app.tellycine.tv.components.RequestFocusOnStart
import app.tellycine.tv.components.TvActionButton
import app.tellycine.tv.components.TvConfirmDialog
import app.tellycine.tv.components.TvDialog
import app.tellycine.tv.components.TvPosterCard
import app.tellycine.tv.components.TvSpacing
import app.tellycine.tv.components.okClicks
import app.tellycine.ui.components.Artwork
import app.tellycine.ui.components.DetailBackdrop
import app.tellycine.ui.components.artworkTint
import app.tellycine.ui.components.formatDuration
import app.tellycine.ui.components.progress
import app.tellycine.ui.components.rememberArtworkColour
import app.tellycine.ui.components.resumePosition
import app.tellycine.ui.components.tellycineViewModel
import app.tellycine.ui.detail.DetailInfo
import app.tellycine.ui.detail.DetailViewModel
import app.tellycine.ui.detail.EpisodeUi
import app.tellycine.ui.downloads.downloadStatus
import app.tellycine.ui.downloads.fraction

/** A movie or series: artwork, details, play/resume, favourite, download, and a series' episodes. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvDetailScreen(kind: ContentKind, playlistId: Long, streamId: String) {
    val vm = tellycineViewModel(key = "detail-$kind-$playlistId-$streamId") { c, _ ->
        DetailViewModel(c, kind, playlistId, streamId)
    }
    val info by vm.info.collectAsStateWithLifecycle()
    val favorite by vm.isFavorite.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val season by vm.selectedSeason.collectAsStateWithLifecycle()
    val next by vm.nextEpisode.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val play = remember { FocusRequester() }
    var episodeMenu by remember { mutableStateOf<EpisodeUi?>(null) }
    var confirmRemove by remember { mutableStateOf<DownloadEntity?>(null) }
    LaunchedEffect(vm) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    val colour = rememberArtworkColour(info.backdrops.firstOrNull() ?: info.poster)
    val tint = remember(colour) { colour?.let { artworkTint(it, dark = true) } }
    val list = rememberLazyListState()
    // The artwork dims once the details scroll away, so the episodes are easy to read.
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 } }
    val dim by animateFloatAsState(if (scrolled) 1f else 0f, label = "dim")
    // With a backdrop filling the screen, the poster isn't needed as well. (It fades away if the
    // details bring a backdrop the playlist didn't have.)
    val showPoster = info.backdrops.isEmpty()

    TvArtworkTint(tint) { tinted ->
        val background = MaterialTheme.colorScheme.background
        // The main button takes the artwork's colour while it isn't focused.
        val accent = if (tinted) TvPalette.primary to TvPalette.onPrimary else null
        Box(Modifier.fillMaxSize().background(background)) {
            // The artwork fills the screen, faded into the background behind the text.
            DetailBackdrop(info.backdrops, info.poster, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to background,
                        0.4f to background.copy(alpha = 0.92f),
                        0.7f to background.copy(alpha = 0.45f),
                        1f to Color.Transparent,
                    )
                )
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to background)))
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = dim }.background(background.copy(alpha = 0.8f)))

            val seasonEpisodes = remember(info.episodes, season) { info.episodes.filter { it.season == season } }
            LazyColumn(
                Modifier.fillMaxSize(),
                state = list,
                contentPadding = PaddingValues(start = 56.dp, end = TvSpacing.screenEnd, top = 40.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") {
                    Header(info, showPoster)
                }
                item(key = "actions") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                        when (kind) {
                            ContentKind.MOVIE -> {
                                val h = history[ContentRepository.movieKey(streamId)]
                                val resume = h?.resumePosition() ?: 0L
                                TvActionButton(
                                    if (resume > 0) "Resume" else "Play",
                                    onClick = { vm.moviePlayRequest(resume)?.let(context::play) },
                                    icon = Icons.Filled.PlayArrow,
                                    primary = true,
                                    enabled = info.stream != null,
                                    accent = accent,
                                    modifier = Modifier.focusRequester(play),
                                )
                                if (resume > 0) {
                                    TvActionButton("From the beginning", onClick = { vm.moviePlayRequest(0)?.let(context::play) }, icon = Icons.Filled.Replay)
                                }
                            }
                            else -> {
                                val n = next
                                if (n != null) {
                                    val (ep, h) = n
                                    val resume = h?.resumePosition() ?: 0L
                                    TvActionButton(
                                        "${if (resume > 0) "Resume" else "Play"} S${ep.season} E${ep.number}",
                                        onClick = { context.play(vm.episodePlayRequest(ep, resume)) },
                                        icon = Icons.Filled.PlayArrow,
                                        primary = true,
                                        accent = accent,
                                        modifier = Modifier.focusRequester(play),
                                    )
                                }
                            }
                        }
                        TvActionButton(
                            if (favorite) "Favourite" else "Add to favourites",
                            onClick = vm::toggleFavorite,
                            icon = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            modifier = if (kind == ContentKind.SERIES && next == null) Modifier.focusRequester(play) else Modifier,
                        )
                        info.trailer?.let { trailer ->
                            TvActionButton("Trailer", onClick = { context.openTrailer(trailer, tv = true) }, icon = Icons.Filled.Theaters)
                        }
                        val stream = info.stream
                        if (kind == ContentKind.MOVIE && stream != null && DownloadRepository.isDownloadable(stream.url)) {
                            val d = downloads[ContentRepository.movieKey(streamId)]
                            DownloadButton(
                                download = d,
                                fileOk = d?.let(vm::isDownloadOk) ?: false,
                                onDownload = { vm.download() },
                                onRetry = vm::retryDownload,
                                onRemove = { confirmRemove = it },
                            )
                        }
                    }
                }
                item(key = "status") {
                    val d = if (kind == ContentKind.MOVIE) downloads[ContentRepository.movieKey(streamId)] else null
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (d != null) {
                            Text(downloadStatus(context, d, vm.isDownloadOk(d)), style = MaterialTheme.typography.bodyMedium, color = TvPalette.primary)
                        }
                        if (info.loading) LinearProgressIndicator(Modifier.widthIn(max = 480.dp).fillMaxWidth())
                        info.error?.let {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                if (info.seasons.size > 1) {
                    item(key = "seasons") {
                        if (info.seasonCovers.isNotEmpty()) {
                            // The seasons' posters, when the provider has them.
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                                contentPadding = PaddingValues(vertical = 10.dp),
                                modifier = Modifier.focusRestorer().padding(top = 8.dp),
                            ) {
                                items(info.seasons.entries.toList(), key = { it.key }) { (number, name) ->
                                    TvPosterCard(
                                        title = name,
                                        poster = info.seasonCovers[number],
                                        onClick = { vm.selectSeason(number) },
                                        selected = number == season,
                                        modifier = Modifier.width(104.dp),
                                    )
                                }
                            }
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.focusRestorer().padding(top = 8.dp)) {
                                items(info.seasons.entries.toList(), key = { it.key }) { (number, name) ->
                                    FilterChip(selected = number == season, onClick = { vm.selectSeason(number) }) { Text(name) }
                                }
                            }
                        }
                    }
                }
                if (kind == ContentKind.SERIES && !info.loading && info.episodes.isEmpty()) {
                    item(key = "no-episodes") {
                        Text("No episodes found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(seasonEpisodes, key = { "ep-${it.id}" }) { ep ->
                    val key = ContentRepository.episodeKey(ep.id)
                    val h = history[key]
                    val d = downloads[key]
                    EpisodeRow(
                        episode = ep,
                        history = h,
                        downloadText = d?.let { downloadStatus(context, it, vm.isDownloadOk(it)) },
                        onPlay = { context.play(vm.episodePlayRequest(ep, h?.resumePosition() ?: 0L)) },
                        onMenu = { episodeMenu = ep },
                    )
                }
            }
        }
    }
    // Focus the main play button once it's known (a series' next episode loads after its details).
    RequestFocusOnStart(play, next?.first?.id ?: info.stream?.streamId)

    episodeMenu?.let { ep ->
        val key = ContentRepository.episodeKey(ep.id)
        val h = history[key]
        val d = downloads[key]
        val first = remember { FocusRequester() }
        TvDialog(
            title = DetailViewModel.episodeLabel(ep),
            onDismiss = { episodeMenu = null },
            buttons = { OutlinedButton(onClick = { episodeMenu = null }) { Text("Close") } },
        ) {
            val resume = h?.resumePosition() ?: 0L
            ListItem(
                selected = false,
                onClick = {
                    episodeMenu = null
                    context.play(vm.episodePlayRequest(ep, resume))
                },
                headlineContent = { Text(if (resume > 0) "Resume" else "Play") },
                leadingContent = { Icon(Icons.Filled.PlayArrow, null) },
                modifier = Modifier.focusRequester(first),
            )
            if (resume > 0) {
                ListItem(
                    selected = false,
                    onClick = {
                        episodeMenu = null
                        context.play(vm.episodePlayRequest(ep, 0))
                    },
                    headlineContent = { Text("Play from the beginning") },
                    leadingContent = { Icon(Icons.Filled.Replay, null) },
                )
            }
            if (DownloadRepository.isDownloadable(ep.url)) {
                val failed = d != null && (d.status == DownloadStatus.FAILED || (d.status == DownloadStatus.COMPLETED && !vm.isDownloadOk(d)))
                ListItem(
                    selected = false,
                    onClick = {
                        episodeMenu = null
                        when {
                            d == null -> vm.download(ep)
                            failed -> vm.retryDownload(d)
                            else -> confirmRemove = d
                        }
                    },
                    headlineContent = {
                        Text(
                            when {
                                d == null -> "Download"
                                failed -> "Retry download"
                                d.status == DownloadStatus.COMPLETED -> "Delete download"
                                else -> "Cancel download"
                            }
                        )
                    },
                    leadingContent = { Icon(if (d == null || failed) Icons.Filled.Download else Icons.Filled.Delete, null) },
                )
            }
        }
        RequestFocusOnStart(first)
    }

    confirmRemove?.let { d ->
        val finished = d.status == DownloadStatus.COMPLETED
        val label = listOfNotNull(d.title, d.subtitle).joinToString(" · ")
        TvConfirmDialog(
            title = if (finished) "Delete download?" else "Cancel download?",
            message = if (finished) "$label will be removed from this device. You can still stream it." else "$label will stop downloading.",
            confirmLabel = if (finished) "Delete" else "Cancel download",
            dismissLabel = "Keep",
            onConfirm = {
                vm.removeDownload(d)
                confirmRemove = null
            },
            onDismiss = { confirmRemove = null },
        )
    }
}

@Composable
private fun Header(info: DetailInfo, showPoster: Boolean) {
    Row {
        AnimatedVisibility(visible = showPoster, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
            Artwork(
                url = info.poster,
                fallbackText = info.title,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.padding(end = 28.dp).width(170.dp).height(255.dp),
            )
        }
        // Beside a poster the text can run wider; over a backdrop it stays to the left, clear of the picture.
        val textWidth = if (showPoster) 720.dp else 600.dp
        Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                info.title,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = textWidth),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                info.rating?.takeIf { it > 0 }?.let { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, null, Modifier.size(18.dp), tint = TvPalette.primary)
                        Text(" %.1f".format(r), style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (info.meta.isNotEmpty()) {
                    Text(info.meta.joinToString("  ·  "), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            info.plot?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = textWidth))
            }
            info.director?.takeIf { it.isNotBlank() }?.let { Credit("Director", it, textWidth) }
            info.cast?.takeIf { it.isNotBlank() }?.let { Credit("Cast", it, textWidth) }
        }
    }
}

@Composable
private fun Credit(label: String, value: String, width: Dp) {
    Text(
        "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = width),
    )
}

@Composable
private fun EpisodeRow(
    episode: EpisodeUi,
    history: HistoryEntity?,
    downloadText: String?,
    onPlay: () -> Unit,
    onMenu: () -> Unit,
) {
    ListItem(
        selected = false,
        onClick = onPlay,
        headlineContent = {
            Text("${episode.number}. ${episode.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val meta = listOfNotNull(episode.durationSecs?.takeIf { it > 0 }?.let(::formatDuration), downloadText).joinToString("  ·  ")
                if (meta.isNotEmpty()) Text(meta, maxLines = 1)
                episode.plot?.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                history?.progress()?.takeIf { it > 0.01f }?.let { p ->
                    LinearProgressIndicator(
                        progress = { p },
                        color = TvPalette.primary,
                        trackColor = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.width(240.dp).height(4.dp),
                    )
                }
            }
        },
        leadingContent = {
            Artwork(
                url = episode.image,
                fallbackText = "E${episode.number}",
                shape = RoundedCornerShape(8.dp),
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(160.dp).height(90.dp),
            )
        },
        modifier = Modifier.okClicks(onClick = onPlay, onLongClick = onMenu),
    )
}

/** Download control for a movie: download, cancel or delete (asking first), or retry. */
@Composable
private fun DownloadButton(
    download: DownloadEntity?,
    fileOk: Boolean,
    onDownload: () -> Unit,
    onRetry: (DownloadEntity) -> Unit,
    onRemove: (DownloadEntity) -> Unit,
) {
    val failed = download != null && (download.status == DownloadStatus.FAILED || (download.status == DownloadStatus.COMPLETED && !fileOk))
    when {
        download == null -> TvActionButton("Download", onClick = onDownload, icon = Icons.Filled.Download)
        failed -> TvActionButton("Retry download", onClick = { onRetry(download) }, icon = Icons.Filled.ErrorOutline)
        download.status == DownloadStatus.COMPLETED -> TvActionButton("Downloaded", onClick = { onRemove(download) }, icon = Icons.Filled.DownloadDone)
        else -> {
            val percent = download.fraction()?.let { " ${(it * 100).toInt()}%" }.orEmpty()
            TvActionButton("Downloading$percent", onClick = { onRemove(download) }, icon = Icons.Filled.Download)
        }
    }
}
