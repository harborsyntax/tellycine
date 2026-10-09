package app.tellycine.player.multiview

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.tellycine.player.PlayRequest
import app.tellycine.player.TvPlayerGuide
import app.tellycine.tv.TvPalette
import app.tellycine.tv.components.RequestFocusOnStart
import app.tellycine.tv.components.TvActionButton
import app.tellycine.tv.components.TvSidePanel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val Gap = 8.dp

/** How much of the screen the guide takes while picking a channel. */
private const val GuideShare = 0.62f

/**
 * The channels in a grid: two side by side, three or four in a 2×2 grid, with a tile to add one
 * while there's room ([spare]). OK on a channel opens its sound and other options.
 */
@Composable
fun MultiViewScreen(
    tiles: List<MultiViewTile>,
    spare: Int,
    picker: MultiViewActivity.PickerTarget?,
    actions: MultiViewActions,
) {
    var menuFor by remember { mutableStateOf<MultiViewTile?>(null) }
    val firstTile = remember { FocusRequester() }
    val addTile = remember { FocusRequester() }
    val canAdd = spare > 0

    BackHandler(enabled = picker != null) { actions.closePicker() }
    BackHandler(enabled = picker == null) { actions.exit() }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val slots = (tiles.size + if (canAdd) 1 else 0).coerceIn(1, MultiViewLimits.MAX_CHANNELS)
        val columns = if (slots == 1) 1 else 2
        val rows = if (slots <= 2) 1 else 2
        // While the guide is open the channels keep playing in the space above it.
        val gridHeight = if (picker != null) maxHeight * (1f - GuideShare) else maxHeight
        // The largest 16:9 cell that fits the grid.
        val cellWidth = min((maxWidth - Gap * (columns + 1)) / columns, ((gridHeight - Gap * (rows + 1)) / rows) * 16f / 9f)
        val cellHeight = cellWidth * 9f / 16f

        Column(Modifier.fillMaxWidth().height(gridHeight), verticalArrangement = Arrangement.spacedBy(Gap, Alignment.CenterVertically)) {
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap, Alignment.CenterHorizontally)) {
                    for (c in 0 until columns) {
                        val index = r * columns + c
                        val cell = Modifier.size(cellWidth, cellHeight)
                        when {
                            index < tiles.size -> {
                                val tile = tiles[index]
                                ChannelTile(
                                    tile = tile,
                                    onClick = { menuFor = tile },
                                    modifier = cell.then(if (index == 0) Modifier.focusRequester(firstTile) else Modifier),
                                )
                            }
                            index == tiles.size && canAdd -> AddTile(spare, onClick = actions::add, modifier = cell.focusRequester(addTile))
                            else -> Spacer(cell)
                        }
                    }
                }
            }
        }

        if (picker != null) {
            val near = picker.replacing?.request ?: tiles.lastOrNull()?.request
            if (near != null) {
                TvPlayerGuide(
                    request = near,
                    playerChannels = emptyList(),
                    onPlay = actions::pick,
                    hint = if (picker.replacing != null) "OK: show instead of ${picker.replacing.request.title}" else "OK: add to multi-view",
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(GuideShare),
                )
            }
        } else {
            Hint(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
        }
    }

    // Focus comes back to the channels once the guide closes.
    if (picker == null && tiles.isNotEmpty()) RequestFocusOnStart(firstTile, tiles.size)

    menuFor?.let { tile ->
        if (tile !in tiles) {
            menuFor = null
        } else {
            TileMenu(
                tile = tile,
                others = tiles.size > 1,
                actions = actions,
                onDismiss = { menuFor = null },
            )
        }
    }
}

/** How to work multi-view, shown for a few seconds. */
@Composable
private fun Hint(modifier: Modifier) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(6_000)
        visible = false
    }
    AnimatedVisibility(visible, exit = fadeOut(), modifier = modifier) {
        Text(
            "Arrows: move between channels  ·  OK: sound and options  ·  Back: leave multi-view",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ChannelTile(tile: MultiViewTile, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Black, focusedContainerColor = Color.Black),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(4.dp, TvPalette.focus), shape = RoundedCornerShape(10.dp))),
        // Tiles sit edge to edge, so the focused one is outlined rather than enlarged.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        isFocusable = false
                        player = tile.player
                    }
                },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
            tile.error?.let {
                Text(
                    "$it Press OK to retry or change it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    if (tile.soundOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    if (tile.soundOn) "Sound on" else "Sound off",
                    tint = if (tile.soundOn) TvPalette.primary else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    tile.request.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (tile.soundOn && tile.volume < 1f) {
                    Text("${(tile.volume * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun AddTile(spare: Int, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvPalette.surface,
            contentColor = TvPalette.onSurface,
            focusedContainerColor = TvPalette.focus,
            focusedContentColor = TvPalette.onFocus,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Icon(Icons.Filled.Add, null, Modifier.size(48.dp))
            Text("Add a channel", style = MaterialTheme.typography.titleMedium)
            Text(
                "Room for $spare more",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** A channel's sound and other options. */
@Composable
private fun TileMenu(tile: MultiViewTile, others: Boolean, actions: MultiViewActions, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    TvSidePanel(onDismiss = onDismiss) {
        Text(tile.request.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.size(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TvActionButton(
                if (tile.soundOn) "Sound off" else "Sound on",
                onClick = { actions.toggleSound(tile) },
                icon = if (tile.soundOn) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                modifier = Modifier.focusRequester(first),
            )
            if (others) {
                TvActionButton(
                    "Only this channel's sound",
                    onClick = {
                        actions.soloSound(tile)
                        onDismiss()
                    },
                    icon = Icons.Filled.Headphones,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvActionButton("Quieter", onClick = { actions.changeVolume(tile, -0.1f) }, icon = Icons.Filled.Remove, enabled = tile.volume > 0.1f)
                Text(
                    "Volume ${(tile.volume * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.width(140.dp),
                )
                TvActionButton("Louder", onClick = { actions.changeVolume(tile, 0.1f) }, icon = Icons.Filled.Add, enabled = tile.volume < 1f)
            }
            if (tile.error != null) {
                TvActionButton(
                    "Retry",
                    onClick = {
                        actions.retry(tile)
                        onDismiss()
                    },
                    icon = Icons.Filled.Refresh,
                )
            }
            TvActionButton(
                "Change channel",
                onClick = {
                    onDismiss()
                    actions.change(tile)
                },
                icon = Icons.Filled.SwapHoriz,
            )
            TvActionButton(
                "Watch full screen",
                onClick = {
                    onDismiss()
                    actions.fullScreen(tile)
                },
                icon = Icons.Filled.Fullscreen,
            )
            if (others) {
                TvActionButton(
                    "Remove from multi-view",
                    onClick = {
                        onDismiss()
                        actions.remove(tile)
                    },
                    icon = Icons.Filled.Close,
                )
            }
        }
    }
    RequestFocusOnStart(first)
}
