package app.mosaic.player

import android.util.Log
import java.util.concurrent.Executor
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.common.MimeTypes
import app.mosaic.core.CastAudio
import app.mosaic.core.AudioTrackInfo
import app.mosaic.data.db.PlaylistEntity
import app.mosaic.cast.CastMedia
import app.mosaic.cast.CastMediaItemConverter
import android.widget.Toast
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.cast.CastPlayer
import androidx.fragment.app.FragmentActivity
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
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
import app.mosaic.container
import app.mosaic.core.ContentKind
import app.mosaic.data.db.HistoryEntity
import app.mosaic.data.db.StreamEntity
import app.mosaic.data.repo.Categories
import app.mosaic.data.repo.ContentRepository
import app.mosaic.data.settings.ThemeMode
import app.mosaic.ui.theme.MosaicTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlayerActivity : FragmentActivity() {

    private lateinit var factory: PlayerFactory
    private lateinit var exoPlayer: ExoPlayer
    /** Media3 player that drives a Chromecast while a Cast session is connected. */
    private var castPlayer: CastPlayer? = null
    private var activePlayer by mutableStateOf<Player?>(null)
    /** Whichever player currently has the stream: local ExoPlayer or the Cast device. */
    private val player: Player get() = activePlayer ?: exoPlayer
    private var castingTo by mutableStateOf<String?>(null)
    private var playlist: PlaylistEntity? = null

    // State observed by the Compose UI.
    private var request by mutableStateOf<PlayRequest?>(null)
    private var channels by mutableStateOf<List<StreamEntity>>(emptyList())
    private var error by mutableStateOf<String?>(null)
    private var inPip by mutableStateOf(false)
    private var resizeMode by mutableIntStateOf(0)
    /** Why a movie's sound may not play on the Chromecast, shown while casting. */
    private var castAudioNotice by mutableStateOf<String?>(null)
    private var castJob: Job? = null

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
        exoPlayer = factory.create()
        exoPlayer.addListener(listener)
        activePlayer = exoPlayer
        setUpCast()

        setContent {
            MosaicTheme(themeMode = ThemeMode.DARK, dynamicColor = true) {
                PlayerScreen(
                    player = activePlayer ?: exoPlayer,
                    castingTo = castingTo,
                    castNotice = castAudioNotice,
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
        channels = emptyList()
        lifecycleScope.launch {
            // The playlist decides how a stream is adapted for Chromecast.
            if (playlist?.id != req.playlistId) playlist = container.playlists.get(req.playlistId)
            start(req)
            if (req.isLive) loadChannels(req)
        }
    }

    private fun start(req: PlayRequest) {
        request = req
        error = null
        liveRetries = 0
        val position = if (req.isLive) C.TIME_UNSET else req.startPositionMs
        val cast = castPlayer?.takeIf { it.isCastSessionAvailable && req.apiKey == null }
        castJob?.cancel()
        castAudioNotice = null
        if (cast != null) {
            // What the phone already knows about this file's soundtracks, read before it stops.
            val knownAudio = if (req.kind != ContentKind.LIVE && activePlayer === exoPlayer) localAudioTracks(req) else null
            if (activePlayer !== cast) exoPlayer.stop()
            activePlayer = cast
            castingTo = container.cast.deviceName() ?: "TV"
            castJob = lifecycleScope.launch {
                if (req.kind != ContentKind.LIVE) {
                    // Checked before the TV starts, so a provider allowing one connection isn't asked for two.
                    val audio = knownAudio ?: withTimeoutOrNull(8_000) { probeAudioTracks(req) }
                    castAudioNotice = audio?.let(CastAudio::notice)
                }
                cast.setMediaItem(CastMedia.mediaItem(req, playlist), position)
                cast.prepare()
                cast.playWhenReady = true
            }
        } else {
            activePlayer = exoPlayer
            castingTo = null
            factory.applyHeaders(req)
            exoPlayer.setMediaItem(factory.mediaItem(req), position)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
        recordHistory(req, 0, 0)
        startProgressUpdates()
    }

    // ---- Chromecast ----

    private fun setUpCast() {
        val castContext = container.cast.castContext ?: return
        castPlayer = CastPlayer(castContext, CastMediaItemConverter()).also { cast ->
            cast.addListener(listener)
            cast.setSessionAvailabilityListener(object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() = moveToCast()
                override fun onCastSessionUnavailable() = moveToLocal()
            })
        }
    }

    /** A Cast session started: continue the current stream on the TV from the same point. */
    private fun moveToCast() {
        val req = request ?: return
        if (req.apiKey != null) {
            Toast.makeText(this, "Recordings can only play on this device", Toast.LENGTH_SHORT).show()
            return
        }
        val position = exoPlayer.currentPosition
        start(req.copy(startPositionMs = position))
    }

    /** The Cast session ended: bring playback back to the phone. */
    private fun moveToLocal() {
        val cast = castPlayer ?: return
        if (activePlayer !== cast) return
        val req = request ?: return
        val position = cast.currentPosition
        cast.stop()
        start(req.copy(startPositionMs = position))
    }

    private fun isCasting() = castPlayer != null && activePlayer === castPlayer

    /** Soundtracks of [req] from the phone's player, if that's what it has loaded. */
    private fun localAudioTracks(req: PlayRequest): List<AudioTrackInfo>? {
        val loaded = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString() ?: return null
        if (loaded != (req.localUri ?: req.url)) return null
        return exoPlayer.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .map { group -> group.getTrackFormat(0).let { AudioTrackInfo(it.sampleMimeType, it.language) } }
            .ifEmpty { null }
    }

    /** Reads the file's soundtracks, in file order, without playing it. */
    private suspend fun probeAudioTracks(req: PlayRequest): List<AudioTrackInfo>? {
        val future = MetadataRetriever.retrieveMetadata(factory.probeSourceFactory(req), factory.mediaItem(req))
        return try {
            val groups = suspendCancellableCoroutine<TrackGroupArray> { cont ->
                future.addListener({
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }, Executor(Runnable::run))
                cont.invokeOnCancellation { future.cancel(true) }
            }
            (0 until groups.length)
                .map { groups[it].getFormat(0) }
                .filter { MimeTypes.isAudio(it.sampleMimeType) }
                .map { AudioTrackInfo(it.sampleMimeType, it.language) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PlayerActivity", "Couldn't read soundtracks before casting", e)
            null
        }
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
            val now = container.content.schedule(stream.playlistId, channelId, limit = 1).firstOrNull()
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
            if (isCasting()) {
                error = "Chromecast couldn't play this stream. The TV may not support its format, " +
                    "or the provider may need headers the TV can't send. Disconnect to watch on this device."
                return
            }
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
        if (req.catchup) return
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

    private fun pipSupported() = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && !isCasting()

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
        // Keep the TV playing when the app goes to the background while casting.
        if (!isInPictureInPictureMode && !isCasting()) player.pause()
    }

    override fun onDestroy() {
        saveProgress()
        progressJob?.cancel()
        exoPlayer.removeListener(listener)
        exoPlayer.release()
        castPlayer?.let {
            it.setSessionAvailabilityListener(null)
            it.removeListener(listener)
            it.release()
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
