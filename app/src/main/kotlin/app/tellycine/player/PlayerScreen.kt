package app.tellycine.player

import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.foundation.layout.widthIn
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.tellycine.cast.CastButton
import app.tellycine.data.db.StreamEntity
import app.tellycine.ui.components.Artwork
import kotlinx.coroutines.delay

internal val RESIZE_MODES = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch",
)

/**
 * The phone's player: full screen, or [docked] at the top of the app with fewer controls. Its
 * control bar's full-screen button calls [onToggleFullScreen]; [onBack] leaves full screen and
 * [onClose] stops playing.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    player: Player,
    castingTo: String?,
    castNotice: String?,
    request: PlayRequest?,
    channels: List<StreamEntity>,
    error: String?,
    inPip: Boolean,
    resizeMode: Int,
    pipSupported: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onZap: (Int) -> Unit,
    onSelectChannel: (StreamEntity) -> Unit,
    onCycleResize: () -> Unit,
    onEnterPip: () -> Unit,
    stats: () -> List<Pair<String, String>>,
    docked: Boolean = false,
    /** Keeps the screen awake while the player is on screen. */
    keepScreenOn: Boolean = true,
    onToggleFullScreen: () -> Unit = {},
    onClose: () -> Unit = onBack,
) {
    val toggleFullScreen by rememberUpdatedState(onToggleFullScreen)
    var controlsVisible by remember { mutableStateOf(true) }
    var showChannels by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var resizeLabel by remember { mutableStateOf<String?>(null) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    LaunchedEffect(resizeMode) {
        playerView?.resizeMode = RESIZE_MODES[resizeMode].first
    }
    LaunchedEffect(resizeLabel) {
        if (resizeLabel != null) {
            delay(1200)
            resizeLabel = null
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 3500
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    setShowSubtitleButton(true)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        controlsVisible = visibility == android.view.View.VISIBLE
                    })
                    setFullscreenButtonClickListener { toggleFullScreen() }
                    playerView = this
                }
            },
            update = { view ->
                if (view.player !== player) view.player = player
                // While casting there is no local video, so keep the remote controls on screen.
                view.controllerShowTimeoutMs = if (castingTo != null) 0 else 3500
                view.controllerHideOnTouch = castingTo == null
                if (castingTo != null) view.showController()
                view.useController = !inPip
                view.resizeMode = RESIZE_MODES[resizeMode].first
                val live = request?.isLive == true
                view.setShowFastForwardButton(!live)
                view.setShowRewindButton(!live)
                view.setFullscreenButtonState(!docked)
                view.keepScreenOn = keepScreenOn
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (!inPip) {
            AnimatedVisibility(
                visible = controlsVisible || error != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                TopControls(
                    request = request,
                    hasChannels = channels.size > 1,
                    casting = castingTo != null,
                    pipSupported = pipSupported,
                    docked = docked,
                    onBack = onBack,
                    onClose = onClose,
                    onZap = onZap,
                    onToggleChannels = { showChannels = !showChannels },
                    onCycleResize = {
                        onCycleResize()
                        resizeLabel = RESIZE_MODES[(resizeMode + 1) % RESIZE_MODES.size].second
                    },
                    onEnterPip = onEnterPip,
                    onToggleStats = { showStats = !showStats },
                )
            }

            if (showStats) {
                StatsPanel(
                    stats,
                    Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(start = 16.dp, top = 64.dp, end = 16.dp),
                )
            }

            resizeLabel?.let { label ->
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 10.dp))
                }
            }

            if (castingTo != null && error == null) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.CastConnected, null, Modifier.size(56.dp), tint = Color.White)
                    Text("Casting to $castingTo", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    castNotice?.let { notice ->
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 440.dp),
                        ) {
                            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.AutoMirrored.Filled.VolumeOff, null, tint = Color(0xFFFFB74D))
                                Text(notice, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            if (error != null) {
                ErrorPanel(error, onRetry, onClose, docked, Modifier.align(Alignment.Center))
            }

            AnimatedVisibility(
                visible = showChannels && channels.isNotEmpty() && !docked,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                ChannelPanel(
                    channels = channels,
                    currentId = request?.streamId,
                    onSelect = {
                        onSelectChannel(it)
                        showChannels = false
                    },
                    onClose = { showChannels = false },
                )
            }
        }
    }
}

/** The bar over the top of the picture; [docked], just the title, channel switching, Cast and close. */
@Composable
private fun TopControls(
    request: PlayRequest?,
    hasChannels: Boolean,
    casting: Boolean,
    pipSupported: Boolean,
    docked: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onZap: (Int) -> Unit,
    onToggleChannels: () -> Unit,
    onCycleResize: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleStats: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!docked) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(
                request?.title.orEmpty(),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            request?.subtitle?.let {
                Text(
                    it,
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (request?.isLive == true && hasChannels) {
            IconButton(onClick = { onZap(-1) }) { Icon(Icons.Filled.KeyboardArrowUp, "Previous channel", tint = Color.White) }
            IconButton(onClick = { onZap(1) }) { Icon(Icons.Filled.KeyboardArrowDown, "Next channel", tint = Color.White) }
            if (!docked) IconButton(onClick = onToggleChannels) { Icon(Icons.AutoMirrored.Filled.List, "Channel list", tint = Color.White) }
        }
        // Recordings need an auth header the TV can't send, so they can't be cast.
        if (request?.apiKey == null) CastButton(onDark = true)
        if (docked) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Stop playing", tint = Color.White) }
            return@Row
        }
        if (!casting) {
            IconButton(onClick = onCycleResize) { Icon(Icons.Filled.AspectRatio, "Resize", tint = Color.White) }
        }
        IconButton(onClick = onToggleStats) { Icon(Icons.Filled.QueryStats, "Stats for nerds", tint = Color.White) }
        if (pipSupported) {
            IconButton(onClick = onEnterPip) { Icon(Icons.Filled.PictureInPictureAlt, "Picture in picture", tint = Color.White) }
        }
    }
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit, onBack: () -> Unit, compact: Boolean, modifier: Modifier) {
    if (compact) {
        // Small enough for the picture at the top of the app.
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(16.dp), modifier = modifier.padding(12.dp)) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(message, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onBack) { Text("Close") }
                    TextButton(onClick = onRetry) { Text("Try again") }
                }
            }
        }
        return
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(24.dp), modifier = modifier.padding(32.dp)) {
        Column(
            Modifier.padding(24.dp).width(320.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.ErrorOutline, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.error)
            Text(message, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack) { Text("Close") }
                Button(onClick = onRetry) { Text("Try again") }
            }
        }
    }
}

@Composable
private fun ChannelPanel(
    channels: List<StreamEntity>,
    currentId: String?,
    onSelect: (StreamEntity) -> Unit,
    onClose: () -> Unit,
) {
    val state = rememberLazyListState()
    LaunchedEffect(Unit) {
        val index = channels.indexOfFirst { it.streamId == currentId }
        if (index > 2) state.scrollToItem(index - 2)
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
        modifier = Modifier.width(340.dp).fillMaxHeight(),
    ) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
                Text("Channels", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Close") }
            }
            LazyColumn(state = state) {
                itemsIndexed(channels, key = { _, s -> s.streamId }) { _, s ->
                    val current = s.streamId == currentId
                    ListItem(
                        modifier = Modifier.clickable { onSelect(s) },
                        colors = if (current) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        else ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Artwork(
                                s.logo, s.name,
                                contentScale = ContentScale.Fit,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(48.dp, 34.dp),
                            )
                        },
                        headlineContent = { Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
    }
}
