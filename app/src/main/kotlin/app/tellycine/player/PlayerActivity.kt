package app.tellycine.player

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import app.tellycine.MainActivity
import app.tellycine.container
import app.tellycine.core.ContentKind
import app.tellycine.player.multiview.MultiViewActivity
import app.tellycine.tv.TellycineTvTheme
import app.tellycine.tv.isTelevision
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The TV's player, driven by the remote. (On a phone, the player sits in the app itself: see
 * [PhonePlayback].) Playback is a [PlaybackEngine]; this adds the channel banner, number entry,
 * the guide over the picture, the mini player, picture in picture and the refresh rate.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : FragmentActivity() {

    private lateinit var engine: PlaybackEngine
    private val exoPlayer: ExoPlayer get() = engine.exoPlayer
    private val player: Player get() = engine.player

    private var inPip by mutableStateOf(false)
    private var resizeMode by mutableIntStateOf(0)

    /** The live channel playing, what's on and the channel watched before it (TV banner). */
    private var liveInfo by mutableStateOf(LiveInfo())
    private var liveInfoJob: Job? = null
    /** Lets the remote's play/pause and voice commands reach the player. */
    private var mediaSession: MediaSession? = null
    /** "Match the TV's refresh rate" setting. */
    private var matchFrameRate = false
    /** Set when Back hands the channel to the guide's mini player, which keeps it playing. */
    private var toMini = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isTelevision()) {
            // Phones play in the app itself.
            PlayRequest.fromIntent(intent)?.let { container.phonePlayback.play(it) }
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        // Shared with the guide's mini player, so a live channel can keep playing there.
        val playback = container.tvPlayback
        engine = PlaybackEngine(
            this,
            playback.factory,
            playback.claim(this),
            lifecycleScope,
            onStarted = { req, continuing ->
                loadLiveInfo(req)
                if (continuing) matchDisplayToVideo()
            },
            onVideoSizeChanged = {
                matchDisplayToVideo()
                updatePipParams()
            },
            onIsPlayingChanged = { updatePipParams() },
            currentChannel = { liveInfo.channel },
        )

        lifecycleScope.launch {
            container.settings.settings.collect { matchFrameRate = it.matchFrameRate }
        }
        // After the player's own panels have had their say.
        onBackPressedDispatcher.addCallback(this) { leave() }

        setContent {
            TellycineTvTheme {
                TvPlayerScreen(
                    player = engine.activePlayer ?: exoPlayer,
                    request = engine.request,
                    channels = engine.channels,
                    live = liveInfo,
                    error = engine.error,
                    inPip = inPip,
                    resizeMode = resizeMode,
                    pipSupported = pipSupported(),
                    onBack = { leave() },
                    onRetry = { engine.retry() },
                    onZap = { engine.zap(it) },
                    onSelectChannel = { engine.playChannel(it) },
                    onChannelNumber = { tuneToNumber(it) },
                    onPreviousChannel = { engine.previousChannel?.let { engine.playChannel(it) } },
                    onToggleFavorite = { toggleFavorite() },
                    onOpenWith = { engine.request?.let { openExternally(it, chooser = true) } },
                    onCycleResize = { resizeMode = (resizeMode + 1) % RESIZE_MODES.size },
                    onEnterPip = { enterPip() },
                    onPlay = { engine.playPicked(it) },
                    stats = ::statsRows,
                    onMultiView = ::openMultiView,
                    noSoundFormat = engine.unplayableAudioFormat,
                )
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (::engine.isInitialized) handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val req = PlayRequest.fromIntent(intent) ?: return finish()
        // The channel already playing in the guide's mini player carries on, full screen.
        val fromMini = req.isLive && engine.request == null &&
            exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString() == req.url
        engine.open(req, continuing = fromMini)
    }

    /** Back: a live channel carries on in the guide's mini player; anything else stops. */
    private fun leave() {
        val req = engine.request
        if (req != null && req.isLive && engine.error == null && !engine.isCasting()) {
            engine.detach()
            container.tvPlayback.toMini(this, req)
            toMini = true
        }
        finish()
    }

    /** Moves the channel playing into multi-view, where more can be added beside it. */
    private fun openMultiView() {
        val req = engine.request ?: return
        // Lets go of the stream first: some accounts allow only one connection.
        exoPlayer.stop()
        startActivity(MultiViewActivity.intent(this, req))
        finish()
    }

    // ---- TV: channel banner, number entry, favourites ----

    /** Loads the channel, what's on now and next, and whether it's a favourite, for the TV banner. */
    private fun loadLiveInfo(req: PlayRequest) {
        liveInfoJob?.cancel()
        if (!req.isLive) {
            liveInfo = LiveInfo()
            return
        }
        liveInfo = LiveInfo(channel = engine.channels.firstOrNull { it.streamId == req.streamId }, previous = engine.previousChannel)
        liveInfoJob = lifecycleScope.launch {
            val content = container.content
            val channel = content.getStream(req.playlistId, ContentKind.LIVE, req.streamId)
            liveInfo = liveInfo.copy(channel = channel ?: liveInfo.channel)
            launch {
                content.isFavorite(req.playlistId, ContentKind.LIVE, req.streamId).collect { liveInfo = liveInfo.copy(favorite = it) }
            }
            val epgId = channel?.epgChannelId ?: return@launch
            // Moves on to the next programme as each one ends.
            while (isActive) {
                val now = System.currentTimeMillis()
                val upcoming = content.schedule(req.playlistId, epgId, now, limit = 2)
                val current = upcoming.firstOrNull()?.takeIf { it.startMs <= now }
                liveInfo = liveInfo.copy(now = current, next = upcoming.firstOrNull { it.startMs > now })
                val nextChange = listOfNotNull(current?.stopMs, upcoming.firstOrNull { it.startMs > now }?.startMs).minOrNull()
                delay(((nextChange ?: (now + 300_000)) - now).coerceIn(5_000, 600_000))
            }
        }
    }

    /** Tunes to the channel with the number typed on the remote. */
    private fun tuneToNumber(number: Int) {
        val req = engine.request ?: return
        lifecycleScope.launch {
            val stream = engine.channels.firstOrNull { it.number == number }
                ?: container.content.channelList(req.playlistId, null).firstOrNull { it.number == number }
            if (stream != null) {
                engine.playChannel(stream)
            } else {
                Toast.makeText(this@PlayerActivity, "No channel $number", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleFavorite() {
        val req = engine.request ?: return
        if (!req.isLive) return
        lifecycleScope.launch { container.content.toggleFavorite(req.playlistId, ContentKind.LIVE, req.streamId) }
    }

    /** The stats panel's rows: the stream as the local player sees it. */
    private fun statsRows(): List<Pair<String, String>> {
        @Suppress("DEPRECATION")
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay
        return engine.statsRows(display)
    }

    // ---- TV: refresh rate ----

    /**
     * Switches the TV to a refresh rate that is a whole multiple of the video's frame rate
     * (50 Hz for 25 or 50 fps, 23.976 Hz for film...), at the current resolution, so motion is
     * smooth. Only when the setting is on and the stream says its frame rate.
     */
    private fun matchDisplayToVideo() {
        if (!matchFrameRate || engine.isCasting()) return
        val fps = exoPlayer.videoFormat?.frameRate?.takeIf { it > 1f } ?: return
        @Suppress("DEPRECATION")
        val display = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay) ?: return
        val current = display.mode
        val matching = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight && it.refreshRate >= 23f }
            .mapNotNull { mode ->
                val multiple = mode.refreshRate / fps
                val whole = Math.round(multiple)
                // Within 0.1%, so 59.94 Hz suits 29.97 fps but not 30 fps.
                if (whole >= 1 && kotlin.math.abs(multiple - whole) / whole < 0.001f) mode to whole else null
            }
            .sortedBy { (_, whole) -> whole }
        // Film (24 fps) is best shown at 24 Hz; anything else at 50 Hz or more, which also suits interlaced TV.
        val best = (if (fps < 24.5f) matching.firstOrNull() else matching.firstOrNull { (mode, _) -> mode.refreshRate >= 47f })
            ?.first ?: return
        if (best.modeId != current.modeId && window.attributes.preferredDisplayModeId != best.modeId) {
            window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
        }
    }

    // ---- Picture in picture & lifecycle ----

    private fun pipSupported() = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && !engine.isCasting()

    private fun updatePipParams() {
        if (pipSupported()) runCatching { setPictureInPictureParams(pipParams(player.videoSize)) }
    }

    private fun enterPip() {
        if (!pipSupported()) return
        runCatching { enterPictureInPictureMode(pipParams(player.videoSize)) }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        // The PiP window was dismissed rather than expanded.
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            player.pause()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::engine.isInitialized) return
        hideSystemBars()
        updatePipParams()
    }

    override fun onStart() {
        super.onStart()
        if (!::engine.isInitialized) return
        // Only while the player is on screen, so the remote's play button can't restart a
        // stream in the background after leaving.
        if (mediaSession == null) {
            mediaSession = runCatching {
                MediaSession.Builder(this, exoPlayer).setId("tellycine-player-${System.identityHashCode(this)}").build()
            }.getOrNull()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!::engine.isInitialized) return
        mediaSession?.release()
        mediaSession = null
        engine.saveProgress()
        // Keep the TV playing when the app goes to the background while casting, and the
        // channel playing on in the guide's mini player.
        if (!isInPictureInPictureMode && !engine.isCasting() && !toMini) player.pause()
    }

    override fun onDestroy() {
        if (::engine.isInitialized) {
            engine.saveProgress()
            engine.detach()
            // The player is shared: it stops only if nothing else has taken it over.
            container.tvPlayback.release(this)
        }
        super.onDestroy()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/**
 * Picture-in-picture settings for a video of [size]: its shape, and (from Android 12) going into
 * picture in picture by itself when the user leaves while it [autoEnter]s.
 */
fun pipParams(size: VideoSize, autoEnter: Boolean = false): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder()
    if (size.width > 0 && size.height > 0) {
        val ratio = (size.width.toFloat() * size.pixelWidthHeightRatio / size.height).coerceIn(0.42f, 2.38f)
        builder.setAspectRatio(Rational((ratio * 1000).toInt(), 1000))
    } else {
        builder.setAspectRatio(Rational(16, 9))
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.setAutoEnterEnabled(autoEnter)
        builder.setSeamlessResizeEnabled(true)
    }
    return builder.build()
}
