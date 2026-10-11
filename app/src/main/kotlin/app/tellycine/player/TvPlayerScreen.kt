package app.tellycine.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import app.tellycine.data.db.ProgrammeEntity
import app.tellycine.data.db.StreamEntity
import app.tellycine.tv.TvPalette
import app.tellycine.tv.components.OkKeyCodes
import app.tellycine.tv.components.RequestFocusOnStart
import app.tellycine.tv.components.TvClock
import app.tellycine.tv.components.TvDialog
import app.tellycine.tv.components.ignoreStrayOkRelease
import app.tellycine.ui.components.Artwork
import app.tellycine.ui.components.formatTime
import kotlinx.coroutines.delay
import java.util.Locale

private enum class TvOverlay { NONE, INFO, CONTROLS, CHANNELS, TRACKS, GUIDE }

/** Remote keys that control playback whatever has focus. */
private val MediaKeyCodes = setOf(
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
    KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
    KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_CAPTIONS, KeyEvent.KEYCODE_GUIDE,
)

/** Everything the TV player needs to show about the current live channel. */
data class LiveInfo(
    val channel: StreamEntity? = null,
    val now: ProgrammeEntity? = null,
    val next: ProgrammeEntity? = null,
    val favorite: Boolean = false,
    val previous: StreamEntity? = null,
)

/**
 * The player on a TV, worked with the remote. With nothing on screen: up/down (or CH+/CH−) change
 * channel, left/right open the channel list on live TV and skip back/forward otherwise, OK shows
 * the controls, number keys jump to a channel and Back leaves. Back first closes anything open.
 */
@OptIn(UnstableApi::class)
@Composable
fun TvPlayerScreen(
    player: Player,
    request: PlayRequest?,
    channels: List<StreamEntity>,
    live: LiveInfo,
    error: String?,
    inPip: Boolean,
    resizeMode: Int,
    pipSupported: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onZap: (Int) -> Unit,
    onSelectChannel: (StreamEntity) -> Unit,
    onChannelNumber: (Int) -> Unit,
    onPreviousChannel: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenWith: () -> Unit,
    onCycleResize: () -> Unit,
    onEnterPip: () -> Unit,
    /** Plays a programme picked in the guide over the picture (catch-up). */
    onPlay: (PlayRequest) -> Unit,
    stats: () -> List<Pair<String, String>>,
    /** Opens multi-view with the channel playing; null where it isn't offered. */
    onMultiView: (() -> Unit)? = null,
) {
    var showStats by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(TvOverlay.INFO) }
    var lastKey by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var digits by remember { mutableStateOf("") }
    var okDown by remember { mutableStateOf(false) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    val root = remember { FocusRequester() }
    // Keys reach the root after any focused control; it only acts on them when it has focus itself.
    var rootFocused by remember { mutableStateOf(false) }
    val isLive = request?.isLive == true

    LaunchedEffect(resizeMode) { playerView?.resizeMode = RESIZE_MODES[resizeMode].first }
    // Banners and controls hide themselves after a while without key presses.
    LaunchedEffect(overlay, lastKey) {
        val timeout = when (overlay) {
            TvOverlay.INFO -> 5_000L
            TvOverlay.CONTROLS -> 8_000L
            else -> return@LaunchedEffect
        }
        delay(timeout)
        overlay = TvOverlay.NONE
    }
    // A new channel or title shows its banner.
    LaunchedEffect(request?.url) {
        if (overlay == TvOverlay.NONE) overlay = TvOverlay.INFO
        lastKey = System.currentTimeMillis()
    }
    // Typed channel numbers are tuned once the typing stops.
    LaunchedEffect(digits) {
        if (digits.isEmpty()) return@LaunchedEffect
        delay(1_800)
        digits.toIntOrNull()?.let(onChannelNumber)
        digits = ""
    }
    // With nothing interactive on screen, the player itself takes the keys.
    LaunchedEffect(overlay, error) {
        if (error == null && (overlay == TvOverlay.NONE || overlay == TvOverlay.INFO)) {
            runCatching { root.requestFocus() }
        }
    }
    BackHandler(enabled = digits.isNotEmpty() || overlay in setOf(TvOverlay.CONTROLS, TvOverlay.CHANNELS, TvOverlay.TRACKS, TvOverlay.GUIDE)) {
        if (digits.isNotEmpty()) digits = "" else overlay = TvOverlay.NONE
    }

    fun seekBy(ms: Long) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        val target = (player.currentPosition + ms).coerceAtLeast(0).let { if (duration != null) it.coerceAtMost(duration) else it }
        player.seekTo(target)
        if (overlay == TvOverlay.NONE) overlay = TvOverlay.INFO
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
        if (overlay == TvOverlay.NONE) overlay = TvOverlay.INFO
    }

    fun handleKey(native: KeyEvent): Boolean {
        val down = native.action == KeyEvent.ACTION_DOWN
        val code = native.keyCode
        // With controls, the channel list or an error showing, arrows and OK belong to them;
        // only the media keys still work from anywhere.
        if (!rootFocused && code !in MediaKeyCodes) return false
        if (code in OkKeyCodes) {
            // Opened on release: the press would otherwise land on the first control.
            if (down && native.repeatCount == 0) okDown = true
            if (!down && okDown) {
                okDown = false
                overlay = TvOverlay.CONTROLS
            }
            return true
        }
        if (!down) return false
        return when (code) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                if (!isLive) return false
                digits = (digits + (code - KeyEvent.KEYCODE_0)).take(4)
                true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (isLive) {
                    onZap(1)
                    overlay = TvOverlay.INFO
                } else {
                    overlay = TvOverlay.CONTROLS
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (isLive) {
                    onZap(-1)
                    overlay = TvOverlay.INFO
                } else {
                    overlay = TvOverlay.CONTROLS
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!isLive) seekBy(-10_000) else if (channels.isNotEmpty()) overlay = TvOverlay.CHANNELS
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!isLive) seekBy(30_000) else if (channels.isNotEmpty()) overlay = TvOverlay.CHANNELS
                true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (!isLive) seekBy(-10_000)
                true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (!isLive) seekBy(30_000)
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> {
                togglePlay()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                player.play()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                player.pause()
                true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_CAPTIONS -> {
                overlay = TvOverlay.TRACKS
                true
            }
            KeyEvent.KEYCODE_INFO -> {
                overlay = if (overlay == TvOverlay.INFO) TvOverlay.NONE else TvOverlay.INFO
                true
            }
            KeyEvent.KEYCODE_LAST_CHANNEL -> {
                onPreviousChannel()
                true
            }
            KeyEvent.KEYCODE_GUIDE -> {
                onBack()
                true
            }
            else -> false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Any key keeps banners and controls on screen a little longer.
            .onPreviewKeyEvent {
                lastKey = System.currentTimeMillis()
                false
            }
            .focusRequester(root)
            .onFocusChanged { rootFocused = it.isFocused }
            .onKeyEvent { handleKey(it.nativeKeyEvent) }
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    // The TV controls are drawn in Compose and driven by the remote.
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    keepScreenOn = true
                    isFocusable = false
                    playerView = this
                }
            },
            update = { view ->
                if (view.player !== player) view.player = player
                view.resizeMode = RESIZE_MODES[resizeMode].first
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (inPip) return@Box

        AnimatedVisibility(
            visible = overlay == TvOverlay.INFO || overlay == TvOverlay.CONTROLS,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomPanel(
                player = player,
                request = request,
                live = live,
                showControls = overlay == TvOverlay.CONTROLS,
                pipSupported = pipSupported,
                resizeLabel = RESIZE_MODES[resizeMode].second,
                onTogglePlay = ::togglePlay,
                onSeek = ::seekBy,
                onChannels = { if (channels.isNotEmpty()) overlay = TvOverlay.CHANNELS },
                onPreviousChannel = onPreviousChannel,
                onToggleFavorite = onToggleFavorite,
                onTracks = { overlay = TvOverlay.TRACKS },
                onCycleResize = onCycleResize,
                onOpenWith = onOpenWith,
                onEnterPip = {
                    overlay = TvOverlay.NONE
                    onEnterPip()
                },
                onGuide = { overlay = TvOverlay.GUIDE },
                onMultiView = onMultiView,
                showingStats = showStats,
                onToggleStats = { showStats = !showStats },
            )
        }

        if (overlay == TvOverlay.GUIDE && request != null) {
            TvPlayerGuide(
                request = request,
                onPlay = {
                    onPlay(it)
                    overlay = TvOverlay.INFO
                },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.62f),
            )
        }

        if (showStats) {
            StatsPanel(stats, Modifier.align(Alignment.TopStart).padding(start = 48.dp, top = 32.dp, end = 48.dp))
        }

        AnimatedVisibility(
            visible = overlay == TvOverlay.CHANNELS && channels.isNotEmpty(),
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            ChannelPanel(
                channels = channels,
                currentId = request?.streamId,
                onSelect = {
                    onSelectChannel(it)
                    overlay = TvOverlay.INFO
                },
            )
        }

        if (digits.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                colors = SurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.7f)),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 48.dp),
            ) {
                Text(digits, style = MaterialTheme.typography.displayMedium, color = Color.White, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }
        }

        if (error != null) {
            ErrorPanel(error, onRetry, onBack, Modifier.align(Alignment.Center))
        }
    }

    if (overlay == TvOverlay.TRACKS) {
        TracksDialog(player, onDismiss = { overlay = TvOverlay.NONE })
    }
}

/** Title, progress and (when [showControls]) the row of controls, along the bottom of the picture. */
@Composable
private fun BottomPanel(
    player: Player,
    request: PlayRequest?,
    live: LiveInfo,
    showControls: Boolean,
    pipSupported: Boolean,
    resizeLabel: String,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onChannels: () -> Unit,
    onPreviousChannel: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTracks: () -> Unit,
    onCycleResize: () -> Unit,
    onOpenWith: () -> Unit,
    onEnterPip: () -> Unit,
    onGuide: () -> Unit,
    onMultiView: (() -> Unit)?,
    showingStats: Boolean,
    onToggleStats: () -> Unit,
) {
    val isLive = request?.isLive == true
    val progress by produceState(PlaybackProgress(), player) {
        while (true) {
            value = PlaybackProgress(
                positionMs = player.currentPosition,
                durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0,
                playing = player.isPlaying,
            )
            delay(500)
        }
    }
    val firstControl = remember { FocusRequester() }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .padding(start = 56.dp, end = 56.dp, top = 48.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (isLive) {
                Artwork(
                    url = live.channel?.logo ?: request?.artwork,
                    fallbackText = request?.title.orEmpty(),
                    contentScale = ContentScale.Fit,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(width = 88.dp, height = 60.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    request?.title.orEmpty(),
                    style = if (isLive) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                    color = if (isLive) TvPalette.primary else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val headline = if (isLive) live.now?.title ?: request?.subtitle else request?.subtitle
                if (headline != null) {
                    Text(
                        headline,
                        style = if (isLive) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isLive) {
                    live.next?.let {
                        Text(
                            "Next  ${formatTime(it.startMs)}  ${it.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            TvClock()
        }

        // Progress: through the programme on live TV, through the file otherwise.
        val now = System.currentTimeMillis()
        val (fraction, start, end) = when {
            isLive && live.now != null -> Triple(
                ((now - live.now.startMs).toFloat() / (live.now.stopMs - live.now.startMs).coerceAtLeast(1)).coerceIn(0f, 1f),
                formatTime(live.now.startMs),
                formatTime(live.now.stopMs),
            )
            !isLive && progress.durationMs > 0 -> Triple(
                (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f),
                formatClock(progress.positionMs),
                formatClock(progress.durationMs),
            )
            else -> Triple(null, null, null)
        }
        if (fraction != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(start.orEmpty(), style = MaterialTheme.typography.labelLarge, color = Color.White)
                LinearProgressIndicator(
                    progress = { fraction },
                    color = TvPalette.primary,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.weight(1f).height(6.dp),
                )
                Text(end.orEmpty(), style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }

        if (showControls) {
            // Scrolls sideways as focus moves along it, so every control fits. A focused button
            // grows, so the row reaches into the panel's margins to leave it room: the scroll
            // clips whatever is outside it.
            Row(
                Modifier
                    .bleed(ControlRowBleed)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = ControlRowBleed, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isLive) {
                    ControlButton("Channels", Icons.AutoMirrored.Filled.List, onChannels, Modifier.focusRequester(firstControl))
                    if (live.previous != null) {
                        ControlButton(live.previous.name, Icons.Filled.History, onPreviousChannel)
                    }
                    ControlButton(
                        if (live.favorite) "Favourite" else "Add to favourites",
                        if (live.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        onToggleFavorite,
                    )
                    ControlButton("TV guide", Icons.Outlined.CalendarViewWeek, onGuide)
                    if (onMultiView != null) ControlButton("Multi-view", Icons.Filled.GridView, onMultiView)
                } else {
                    ControlButton("Back 10s", Icons.Filled.FastRewind, { onSeek(-10_000) })
                    ControlButton(
                        if (progress.playing) "Pause" else "Play",
                        if (progress.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        onTogglePlay,
                        Modifier.focusRequester(firstControl),
                    )
                    ControlButton("Forward 30s", Icons.Filled.FastForward, { onSeek(30_000) })
                }
                ControlButton("Audio & subtitles", Icons.Filled.ClosedCaption, onTracks)
                ControlButton(resizeLabel, Icons.Filled.AspectRatio, onCycleResize)
                if (request?.apiKey == null && request?.localUri == null) {
                    ControlButton("Open with…", Icons.AutoMirrored.Filled.OpenInNew, onOpenWith)
                }
                if (pipSupported) {
                    ControlButton("Picture in picture", Icons.Filled.PictureInPictureAlt, onEnterPip)
                }
                ControlButton(if (showingStats) "Hide stats" else "Stats for nerds", Icons.Filled.QueryStats, onToggleStats)
            }
            RequestFocusOnStart(firstControl)
        }
    }
}

/** Room either side of the control row for a focused button to grow into. */
private val ControlRowBleed = 20.dp

/** Lays the content out [amount] wider on each side than the space given, without moving its neighbours. */
private fun Modifier.bleed(amount: Dp) = layout { measurable, constraints ->
    val px = amount.roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = 2 * px))
    layout((placeable.width - 2 * px).coerceAtLeast(0), placeable.height) { placeable.place(-px, 0) }
}

private data class PlaybackProgress(val positionMs: Long = 0, val durationMs: Long = 0, val playing: Boolean = false)

private fun formatClock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun ControlButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        colors = ButtonDefaults.colors(
            containerColor = Color.Black.copy(alpha = 0.4f),
            contentColor = Color.White,
            focusedContainerColor = TvPalette.focus,
            focusedContentColor = TvPalette.onFocus,
        ),
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
    }
}

/** The channel list, over the left of the picture; opens on the channel playing. */
@Composable
private fun ChannelPanel(
    channels: List<StreamEntity>,
    currentId: String?,
    onSelect: (StreamEntity) -> Unit,
) {
    val start = channels.indexOfFirst { it.streamId == currentId }.coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (start - 3).coerceAtLeast(0))
    val current = remember { FocusRequester() }
    Surface(
        colors = SurfaceDefaults.colors(containerColor = TvPalette.past.copy(alpha = 0.95f)),
        modifier = Modifier.width(420.dp).fillMaxHeight().ignoreStrayOkRelease(),
    ) {
        Column(Modifier.padding(start = 40.dp, end = 16.dp, top = 28.dp)) {
            Text("Channels", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
            LazyColumn(state = state, contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(channels, key = { _, s -> s.streamId }) { index, s ->
                    ListItem(
                        selected = s.streamId == currentId,
                        onClick = { onSelect(s) },
                        headlineContent = {
                            Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = {
                            Artwork(
                                s.logo, s.name,
                                contentScale = ContentScale.Fit,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.size(52.dp, 36.dp),
                            )
                        },
                        modifier = if (index == start) Modifier.focusRequester(current) else Modifier,
                    )
                }
            }
        }
    }
    RequestFocusOnStart(current)
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit, onBack: () -> Unit, modifier: Modifier) {
    val retry = remember { FocusRequester() }
    Surface(
        shape = RoundedCornerShape(24.dp),
        colors = SurfaceDefaults.colors(containerColor = TvPalette.surfaceHigh),
        modifier = modifier.width(520.dp),
    ) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Filled.ErrorOutline, null, Modifier.size(44.dp), tint = Color(0xFFFFB4AB))
            Text(message, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack) { Text("Close") }
                Button(onClick = onRetry, modifier = Modifier.focusRequester(retry)) { Text("Try again") }
            }
        }
    }
    RequestFocusOnStart(retry, message)
}

/** One choosable soundtrack or subtitle track. */
private data class TrackChoice(val label: String, val group: Tracks.Group?, val index: Int, val selected: Boolean)

@OptIn(UnstableApi::class)
private fun trackChoices(player: Player, type: Int): List<TrackChoice> =
    player.currentTracks.groups.filter { it.type == type }.flatMap { group ->
        (0 until group.length).filter { group.isTrackSupported(it) }.map { i ->
            TrackChoice(describe(group.getTrackFormat(i), type), group, i, group.isTrackSelected(i))
        }
    }

private fun describe(format: Format, type: Int): String {
    val language = format.language?.takeUnless { it == "und" || it.isBlank() }?.let {
        Locale.forLanguageTag(it).displayLanguage.ifBlank { it }
    }
    val extra = when (type) {
        C.TRACK_TYPE_AUDIO -> listOfNotNull(
            when (format.channelCount) {
                1 -> "Mono"
                2 -> "Stereo"
                6 -> "5.1"
                8 -> "7.1"
                Format.NO_VALUE -> null
                else -> "${format.channelCount} channels"
            },
            format.sampleMimeType?.substringAfter('/')?.uppercase()?.removePrefix("MP4A-LATM")?.ifBlank { null },
        )
        else -> emptyList()
    }
    return listOfNotNull(format.label ?: language ?: "Unknown", if (format.label != null) language else null)
        .plus(extra)
        .distinct()
        .joinToString(" · ")
}

/** Pick the soundtrack and subtitles. */
@Composable
private fun TracksDialog(player: Player, onDismiss: () -> Unit) {
    val audio = remember { trackChoices(player, C.TRACK_TYPE_AUDIO) }
    val text = remember { trackChoices(player, C.TRACK_TYPE_TEXT) }
    val subtitlesOff = remember { player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) || text.none { it.selected } }
    val first = remember { FocusRequester() }

    fun choose(type: Int, choice: TrackChoice?) {
        val builder = player.trackSelectionParameters.buildUpon()
        if (choice?.group == null) {
            builder.setTrackTypeDisabled(type, true)
        } else {
            builder.setTrackTypeDisabled(type, false)
                .setOverrideForType(TrackSelectionOverride(choice.group.mediaTrackGroup, choice.index))
        }
        player.trackSelectionParameters = builder.build()
        onDismiss()
    }

    TvDialog(title = "Audio & subtitles", onDismiss = onDismiss, width = 620.dp, buttons = {
        OutlinedButton(onClick = onDismiss) { Text("Close") }
    }) {
        Text("Audio", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TvPalette.primary)
        if (audio.isEmpty()) {
            Text("No soundtrack choices for this stream.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        audio.forEachIndexed { i, choice ->
            ListItem(
                selected = choice.selected,
                onClick = { choose(C.TRACK_TYPE_AUDIO, choice) },
                headlineContent = { Text(choice.label) },
                leadingContent = { RadioButton(selected = choice.selected, onClick = null) },
                modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
            )
        }
        Text("Subtitles", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TvPalette.primary, modifier = Modifier.padding(top = 8.dp))
        ListItem(
            selected = subtitlesOff,
            onClick = { choose(C.TRACK_TYPE_TEXT, null) },
            headlineContent = { Text("Off") },
            leadingContent = { RadioButton(selected = subtitlesOff, onClick = null) },
            modifier = if (audio.isEmpty()) Modifier.focusRequester(first) else Modifier,
        )
        text.forEach { choice ->
            val on = choice.selected && !subtitlesOff
            ListItem(
                selected = on,
                onClick = { choose(C.TRACK_TYPE_TEXT, choice) },
                headlineContent = { Text(choice.label) },
                leadingContent = { RadioButton(selected = on, onClick = null) },
            )
        }
    }
    RequestFocusOnStart(first)
}
