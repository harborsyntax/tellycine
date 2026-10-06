package app.tube.player

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.tube.container
import app.tube.core.ContentKind
import app.tube.data.db.HistoryEntity
import app.tube.data.db.StreamEntity
import app.tube.data.repo.Categories
import app.tube.data.repo.ContentRepository
import app.tube.data.settings.ThemeMode
import app.tube.ui.theme.TubeTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private lateinit var factory: PlayerFactory
    private lateinit var player: ExoPlayer

    // State observed by the Compose UI.
    private var request by mutableStateOf<PlayRequest?>(null)
    private var channels by mutableStateOf<List<StreamEntity>>(emptyList())
    private var error by mutableStateOf<String?>(null)
    private var inPip by mutableStateOf(false)
    private var resizeMode by mutableIntStateOf(0)

    private var progressJob: Job? = null
    private var liveRetries = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        factory = PlayerFactory(this, container.httpClient)
        player = factory.create()
        player.addListener(listener)

        setContent {
            TubeTheme(themeMode = ThemeMode.DARK, dynamicColor = true) {
                PlayerScreen(
                    player = player,
                    request = request,
                    channels = channels,
                    error = error,
                    inPip = inPip,
                    resizeMode = resizeMode,
                    pipSupported = pipSupported(),
                    onBack = { finish() },
                    onRetry = { retry() },
                    onZap = { zap(it) },
                    onSelectChannel = { playChannel(it) },
                    onCycleResize = { resizeMode = (resizeMode + 1) % RESIZE_MODES.size },
                    onEnterPip = { enterPip() },
                )
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val req = PlayRequest.fromIntent(intent) ?: return finish()
        saveProgress()
        start(req)
        channels = emptyList()
        if (req.isLive) loadChannels(req)
    }

    private fun start(req: PlayRequest) {
        request = req
        error = null
        liveRetries = 0
        factory.applyHeaders(req)
        player.setMediaItem(factory.mediaItem(req), if (req.isLive) C.TIME_UNSET else req.startPositionMs)
        player.prepare()
        player.playWhenReady = true
        recordHistory(req, 0, 0)
        startProgressUpdates()
    }

    private fun loadChannels(req: PlayRequest) {
        lifecycleScope.launch {
            val content = container.content
            channels = if (req.categoryId == Categories.FAVORITES) {
                content.favoriteItems(req.playlistId, ContentKind.LIVE, 2000).first()
            } else {
                content.channelList(req.playlistId, req.categoryId)
            }
        }
    }

    /** Switch to the previous (-1) or next (+1) channel in the current list. */
    private fun zap(direction: Int) {
        val list = channels
        val current = request ?: return
        if (list.isEmpty()) return
        val index = list.indexOfFirst { it.streamId == current.streamId }
        val next = list[((if (index < 0) 0 else index + direction) % list.size + list.size) % list.size]
        playChannel(next)
    }

    private fun playChannel(stream: StreamEntity) {
        val current = request ?: return
        saveProgress()
        start(
            current.copy(
                streamId = stream.streamId,
                historyKey = ContentRepository.liveKey(stream.streamId),
                url = stream.url,
                title = stream.name,
                subtitle = null,
                artwork = stream.logo,
                userAgent = stream.userAgent ?: current.userAgent,
                referrer = stream.referrer,
                startPositionMs = 0,
            )
        )
        lifecycleScope.launch {
            val channelId = stream.epgChannelId ?: return@launch
            val now = container.content.schedule(stream.playlistId, channelId, 1).firstOrNull()
            if (now != null && now.startMs <= System.currentTimeMillis() && request?.streamId == stream.streamId) {
                request = request?.copy(subtitle = now.title)
            }
        }
    }

    private fun retry() {
        val req = request ?: return
        error = null
        if (req.isLive) {
            start(req)
        } else {
            val position = player.currentPosition
            start(req.copy(startPositionMs = position))
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlayerError(e: PlaybackException) {
            val req = request ?: return
            if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                player.seekToDefaultPosition()
                player.prepare()
                return
            }
            // Live IPTV streams frequently drop; retry a few times before giving up.
            if (req.isLive && liveRetries < 3) {
                liveRetries++
                lifecycleScope.launch {
                    delay(1500L * liveRetries)
                    if (request == req) {
                        player.prepare()
                        player.play()
                    }
                }
                return
            }
            error = describe(e)
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                liveRetries = 0
                error = null
            }
            if (state == Player.STATE_ENDED) saveProgress()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && pipSupported()) {
                runCatching { setPictureInPictureParams(pipParams()) }
            }
        }
    }

    private fun describe(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "The server refused the stream (${e.cause?.message ?: "HTTP error"})."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Couldn't connect to the stream. Check your connection."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> "This stream format isn't supported."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "This device can't decode the stream."
        else -> e.message ?: "Playback failed."
    }

    // ---- Watch history ----

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = lifecycleScope.launch {
            while (isActive) {
                delay(15_000)
                if (player.isPlaying) saveProgress()
            }
        }
    }

    private fun saveProgress() {
        val req = request ?: return
        if (req.isLive) return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        val position = player.currentPosition
        if (position <= 0) return
        recordHistory(req, position, duration)
    }

    private fun recordHistory(req: PlayRequest, position: Long, duration: Long) {
        val content = container.content
        val entity = HistoryEntity(
            playlistId = req.playlistId,
            itemKey = req.historyKey,
            kind = req.kind,
            streamId = req.streamId,
            title = req.title,
            subtitle = if (req.isLive) null else req.subtitle,
            artwork = req.artwork,
            url = req.url,
            positionMs = position,
            durationMs = duration,
            lastWatchedAt = System.currentTimeMillis(),
        )
        container.appScope.launch {
            if (!req.isLive && position == 0L) {
                // Starting playback: keep any existing resume point.
                val existing = content.getHistory(req.playlistId, req.historyKey)
                content.saveHistory(existing?.copy(lastWatchedAt = entity.lastWatchedAt) ?: entity)
            } else {
                content.saveHistory(entity)
            }
        }
    }

    // ---- Picture in picture & lifecycle ----

    private fun pipSupported() = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun pipParams(): PictureInPictureParams {
        val size = player.videoSize
        val builder = PictureInPictureParams.Builder()
        if (size.width > 0 && size.height > 0) {
            val ratio = (size.width.toFloat() * size.pixelWidthHeightRatio / size.height).coerceIn(0.42f, 2.38f)
            builder.setAspectRatio(Rational((ratio * 1000).toInt(), 1000))
        } else {
            builder.setAspectRatio(Rational(16, 9))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(player.isPlaying)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun enterPip() {
        if (!pipSupported()) return
        runCatching { enterPictureInPictureMode(pipParams()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player.isPlaying && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) enterPip()
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
        hideSystemBars()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && pipSupported()) {
            runCatching { setPictureInPictureParams(pipParams()) }
        }
    }

    override fun onStop() {
        super.onStop()
        saveProgress()
        if (!isInPictureInPictureMode) player.pause()
    }

    override fun onDestroy() {
        saveProgress()
        progressJob?.cancel()
        player.removeListener(listener)
        player.release()
        super.onDestroy()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
