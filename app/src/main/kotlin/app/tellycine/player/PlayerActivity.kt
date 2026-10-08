package app.tellycine.player

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
import app.tellycine.core.CastAudio
import app.tellycine.core.AudioTrackInfo
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.cast.CastMedia
import app.tellycine.cast.CastMediaItemConverter
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
import app.tellycine.container
import app.tellycine.core.ContentKind
import app.tellycine.data.db.HistoryEntity
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.repo.Categories
import app.tellycine.data.repo.ContentRepository
import app.tellycine.data.settings.ThemeMode
import app.tellycine.ui.theme.TellycineTheme
import app.tellycine.tv.TellycineTvTheme
import app.tellycine.tv.isTelevision
import androidx.media3.session.MediaSession
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

    // ---- TV ----
    /** On a TV the player is driven by the remote, with its own controls. */
    private val tv by lazy { isTelevision() }
    /** The live channel playing, what's on and the channel watched before it (TV banner). */
    private var liveInfo by mutableStateOf(LiveInfo())
    private var previousChannel: StreamEntity? = null
    private var liveInfoJob: Job? = null
    /** Lets the remote's play/pause and voice commands reach the player. */
    private var mediaSession: MediaSession? = null
    /** "Match the TV's refresh rate" setting. */
    private var matchFrameRate = false

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

        if (tv) {
            mediaSession = runCatching { MediaSession.Builder(this, exoPlayer).build() }.getOrNull()
            lifecycleScope.launch {
                container.settings.settings.collect { matchFrameRate = it.matchFrameRate }
            }
        }

        setContent {
            if (tv) {
                TellycineTvTheme {
                    TvPlayerScreen(
                        player = activePlayer ?: exoPlayer,
                        request = request,
                        channels = channels,
                        live = liveInfo,
                        error = error,
                        inPip = inPip,
                        resizeMode = resizeMode,
                        pipSupported = pipSupported(),
                        onBack = { finish() },
                        onRetry = { retry() },
                        onZap = { zap(it) },
                        onSelectChannel = { playChannel(it) },
                        onChannelNumber = { tuneToNumber(it) },
                        onPreviousChannel = { previousChannel?.let { playChannel(it) } },
                        onToggleFavorite = { toggleFavorite() },
                        onOpenWith = { request?.let { openExternally(it, chooser = true) } },
                        onCycleResize = { resizeMode = (resizeMode + 1) % RESIZE_MODES.size },
                        onEnterPip = { enterPip() },
                    )
                }
                return@setContent
            }
            TellycineTheme(themeMode = ThemeMode.DARK, dynamicColor = true) {
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
                val p = playlist
                if (req.kind != ContentKind.LIVE) {
                    // Checked before the TV starts, so a provider allowing one connection isn't asked for two.
                    val audio = knownAudio ?: withTimeoutOrNull(8_000) { probeAudioTracks(req) }
                    castAudioNotice = audio?.let(CastAudio::notice)
                }
                // Dispatcharr can convert a channel's audio to AAC, which Chromecast always plays.
                val outputProfile = if (req.isLive && p?.isDispatcharr == true) {
                    withTimeoutOrNull(5_000) { container.dvr.aacOutputProfile(p) }
                } else {
                    null
                }
                cast.setMediaItem(CastMedia.mediaItem(req, p, outputProfile), position)
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
        if (tv) loadLiveInfo(req)
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
        if (stream.streamId != current.streamId) {
            previousChannel = channels.firstOrNull { it.streamId == current.streamId } ?: liveInfo.channel
        }
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

    // ---- TV: channel banner, number entry, favourites ----

    /** Loads the channel, what's on now and next, and whether it's a favourite, for the TV banner. */
    private fun loadLiveInfo(req: PlayRequest) {
        liveInfoJob?.cancel()
        if (!req.isLive) {
            liveInfo = LiveInfo()
            return
        }
        liveInfo = LiveInfo(channel = channels.firstOrNull { it.streamId == req.streamId }, previous = previousChannel)
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
        val req = request ?: return
        lifecycleScope.launch {
            val stream = channels.firstOrNull { it.number == number }
                ?: container.content.channelList(req.playlistId, null).firstOrNull { it.number == number }
            if (stream != null) {
                playChannel(stream)
            } else {
                Toast.makeText(this@PlayerActivity, "No channel $number", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleFavorite() {
        val req = request ?: return
        if (!req.isLive) return
        lifecycleScope.launch { container.content.toggleFavorite(req.playlistId, ContentKind.LIVE, req.streamId) }
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
            matchDisplayToVideo()
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

    // ---- TV: refresh rate ----

    /**
     * Switches the TV to a refresh rate that is a whole multiple of the video's frame rate
     * (50 Hz for 25 or 50 fps, 23.976 Hz for film...), at the current resolution, so motion is
     * smooth. Only when the setting is on and the stream says its frame rate.
     */
    private fun matchDisplayToVideo() {
        if (!tv || !matchFrameRate || isCasting()) return
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
            builder.setAutoEnterEnabled(player.isPlaying && !tv)
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
        if (!tv && player.isPlaying && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) enterPip()
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
        mediaSession?.release()
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
