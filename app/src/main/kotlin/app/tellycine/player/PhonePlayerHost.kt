package app.tellycine.player

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * The app with [PhonePlayback]'s player: in portrait, docked at the top with the app ([content])
 * below, so browsing carries on while it plays; full screen in landscape and in picture in
 * picture. The app stays in place underneath, so it's where it was when the player docks again.
 * [onFullScreen] asks for full screen (landscape) or leaves it.
 */
@Composable
fun PhonePlayerHost(
    playback: PhonePlayback,
    inPip: Boolean,
    pipSupported: Boolean,
    onFullScreen: (Boolean) -> Unit,
    onEnterPip: () -> Unit,
    content: @Composable () -> Unit,
) {
    val engine = playback.engine
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val full = engine != null && (landscape || inPip)
    val docked = engine != null && !full
    val view = LocalView.current

    SystemBars(full = full, docked = docked)
    BackHandler(enabled = full && !inPip) { onFullScreen(false) }

    Layout(
        content = {
            if (engine != null) {
                // The same player view either way, so the picture carries on when it docks or fills the screen.
                Box(
                    Modifier.layoutId(PLAYER)
                        .background(Color.Black)
                        .then(if (full) Modifier.fillMaxSize() else Modifier.fillMaxWidth().statusBarsPadding().aspectRatio(16f / 9f))
                ) {
                    PlayerScreen(
                        player = engine.activePlayer ?: engine.exoPlayer,
                        castingTo = engine.castingTo,
                        castNotice = engine.castAudioNotice,
                        request = engine.request,
                        channels = engine.channels,
                        error = engine.error,
                        inPip = inPip,
                        resizeMode = playback.resizeMode,
                        pipSupported = pipSupported && !engine.isCasting(),
                        onBack = { onFullScreen(false) },
                        onRetry = engine::retry,
                        onZap = engine::zap,
                        onSelectChannel = engine::playChannel,
                        onCycleResize = { playback.resizeMode = (playback.resizeMode + 1) % RESIZE_MODES.size },
                        onEnterPip = onEnterPip,
                        stats = { engine.statsRows(view.display) },
                        docked = docked,
                        // Browsing below a paused video lets the screen go off as usual.
                        keepScreenOn = full || playback.isPlaying,
                        onToggleFullScreen = { onFullScreen(!full) },
                        onClose = playback::close,
                    )
                }
            }
            // Below the docked player, the app starts under it rather than under the status bar.
            Box(Modifier.layoutId(APP).then(if (docked) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)) {
                content()
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val player = measurables.firstOrNull { it.layoutId == PLAYER }?.measure(Constraints(maxWidth = width, maxHeight = height))
        // Behind a full-screen player the app isn't laid out at all: it keeps its place, but isn't
        // squeezed to nothing, or to the size of a picture-in-picture window, which its lists can't take.
        val app = if (full) null else {
            val below = height - (player?.height ?: 0)
            measurables.first { it.layoutId == APP }.measure(Constraints.fixed(width, below.coerceAtLeast(0)))
        }
        layout(width, height) {
            player?.place(0, 0)
            app?.place(0, player?.height ?: 0)
        }
    }
}

private const val PLAYER = "player"
private const val APP = "app"

/** Hides the system bars while [full] screen; light status bar icons over the black above the [docked] player. */
@Composable
private fun SystemBars(full: Boolean, docked: Boolean) {
    val window = LocalActivity.current?.window ?: return
    // Nothing playing: the app's own bars, untouched.
    if (!full && !docked) return
    DisposableEffect(window, full, docked) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val lightIcons = controller.isAppearanceLightStatusBars
        if (full) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.isAppearanceLightStatusBars = false
        }
        onDispose {
            if (docked) controller.isAppearanceLightStatusBars = lightIcons
            if (full) controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
