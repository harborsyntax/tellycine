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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VideoLabel
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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

/** Tiles narrower than this (in dp) get a smaller name. */
private const val SMALL_TILE_WIDTH = 200f

/** How much of the screen the guide takes while picking a channel. */
private const val GuideShare = 0.62f

/**
 * The channels in the chosen [layout], with a tile to add one while there's room ([spare]). OK on
 * a channel opens its sound and other options.
 */
@Composable
fun MultiViewScreen(
    tiles: List<MultiViewTile>,
    spare: Int,
    picker: MultiViewActivity.PickerTarget?,
    layout: MultiViewLayout,
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
        // While the guide is open the channels keep playing in the space above it.
        val gridHeight = if (picker != null) maxHeight * (1f - GuideShare) else maxHeight
        val cells = layout.cells(slots, maxWidth.value, gridHeight.value, Gap.value)

        cells.forEachIndexed { index, cell ->
            val place = Modifier
                .offset(cell.x.dp, cell.y.dp)
                .size(cell.width.dp, cell.height.dp)
            if (index < tiles.size) {
                val tile = tiles[index]
                key(tile.key) {
                    ChannelTile(
                        tile = tile,
                        small = cell.width < SMALL_TILE_WIDTH,
                        onClick = { menuFor = tile },
                        modifier = place.then(if (index == 0) Modifier.focusRequester(firstTile) else Modifier),
                    )
                }
            } else {
                AddTile(spare, onClick = actions::add, modifier = place.focusRequester(addTile))
            }
        }

        if (picker != null) {
            val near = picker.replacing?.request ?: tiles.lastOrNull()?.request
            if (near != null) {
                TvPlayerGuide(
                    request = near,
                    onPlay = actions::pick,
                    hint = if (picker.replacing != null) "OK: show instead of ${picker.replacing.request.title}" else "OK: add to multi-view",
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(GuideShare),
                )
            }
        } else {
            // At the top, clear of the channel names along the bottom of each tile.
            Hint(Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
        }
    }

    // Focus comes back to the channels once the guide closes, and follows a channel made the big one.
    if (picker == null && tiles.isNotEmpty()) RequestFocusOnStart(firstTile, tiles.size to tiles.first().key)

    menuFor?.let { tile ->
        if (tile !in tiles) {
            menuFor = null
        } else {
            TileMenu(
                tile = tile,
                others = tiles.size > 1,
                first = tiles.firstOrNull() === tile,
                layout = layout,
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
private fun ChannelTile(tile: MultiViewTile, small: Boolean, onClick: () -> Unit, modifier: Modifier) {
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
                    .padding(horizontal = if (small) 8.dp else 12.dp, vertical = if (small) 4.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (small) 4.dp else 8.dp),
            ) {
                Icon(
                    if (tile.soundOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    if (tile.soundOn) "Sound on" else "Sound off",
                    tint = if (tile.soundOn) TvPalette.primary else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(if (small) 16.dp else 20.dp),
                )
                Text(
                    tile.request.title,
                    style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
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
private fun TileMenu(
    tile: MultiViewTile,
    others: Boolean,
    first: Boolean,
    layout: MultiViewLayout,
    actions: MultiViewActions,
    onDismiss: () -> Unit,
) {
    val firstButton = remember { FocusRequester() }
    TvSidePanel(onDismiss = onDismiss) {
        Text(tile.request.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.size(16.dp))
        // Scrolls if a Retry button makes it too tall for the screen.
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TvActionButton(
                if (tile.soundOn) "Sound off" else "Sound on",
                onClick = { actions.toggleSound(tile) },
                icon = if (tile.soundOn) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                modifier = Modifier.focusRequester(firstButton),
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
            if (others && !first && layout != MultiViewLayout.GRID) {
                TvActionButton(
                    "Enlarge",
                    onClick = {
                        actions.makeBig(tile)
                        onDismiss()
                    },
                    icon = Icons.Filled.OpenInFull,
                )
            }
            if (others) {
                TvActionButton(
                    "Remove from multi-view",
                    onClick = {
                        onDismiss()
                        actions.remove(tile)
                    },
                    icon = Icons.Filled.Close,
                )
                // The layout of the whole screen, with the one in use filled in.
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MultiViewLayout.entries.forEach { option ->
                        TvActionButton(
                            option.label,
                            onClick = {
                                actions.setLayout(option)
                                onDismiss()
                            },
                            icon = when (option) {
                                MultiViewLayout.GRID -> Icons.Filled.GridView
                                MultiViewLayout.BIG_SIDE -> Icons.Filled.ViewSidebar
                                MultiViewLayout.BIG_BOTTOM -> Icons.Filled.VideoLabel
                            },
                            primary = option == layout,
                        )
                    }
                }
            }
        }
    }
    RequestFocusOnStart(firstButton)
}
