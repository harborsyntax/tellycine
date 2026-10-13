package app.tellycine.player

import android.content.Context
import android.util.Log
import android.view.Display
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.exoplayer.source.TrackGroupArray
import app.tellycine.cast.CastMedia
import app.tellycine.cast.CastMediaItemConverter
import app.tellycine.container
import app.tellycine.core.AudioTrackInfo
import app.tellycine.core.CastAudio
import app.tellycine.core.ContentKind
import app.tellycine.data.db.HistoryEntity
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.repo.Categories
import app.tellycine.data.repo.ContentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Plays [PlayRequest]s: on this device's [exoPlayer], or on a Chromecast while one is connected,
 * with live channels to switch between, retries when a live stream drops, and resume points and
 * watch history. The screen showing it ([PlayerActivity] on a TV, the phone's [PhonePlayback])
 * adds what's its own, through [onStarted], [onVideoSizeChanged] and [onIsPlayingChanged].
 */
@OptIn(UnstableApi::class)
class PlaybackEngine(
    context: Context,
    val factory: PlayerFactory,
    val exoPlayer: ExoPlayer,
    private val scope: CoroutineScope,
    /** After a request starts playing; [continuing] when it was already playing (a TV's mini player). */
    private val onStarted: (request: PlayRequest, continuing: Boolean) -> Unit = { _, _ -> },
    private val onVideoSizeChanged: () -> Unit = {},
    private val onIsPlayingChanged: (Boolean) -> Unit = {},
    /** The channel playing when it isn't in [channels], for [previousChannel]. */
    private val currentChannel: () -> StreamEntity? = { null },
) {
    private val context = context.applicationContext
    private val container = context.container

    /** Media3 player that drives a Chromecast while a Cast session is connected. */
    private var castPlayer: CastPlayer? = null
    var activePlayer by mutableStateOf<Player?>(exoPlayer)
        private set
    /** Whichever player currently has the stream: local ExoPlayer or the Cast device. */
    val player: Player get() = activePlayer ?: exoPlayer
    var castingTo by mutableStateOf<String?>(null)
        private set
    /** Why a movie's sound may not play on the Chromecast, shown while casting. */
    var castAudioNotice by mutableStateOf<String?>(null)
        private set

    var request by mutableStateOf<PlayRequest?>(null)
        private set
    /** The live channels to switch between: the playing channel's category, or favourites. */
    var channels by mutableStateOf<List<StreamEntity>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** The channel watched before this one. */
    var previousChannel: StreamEntity? = null
        private set

    private var playlist: PlaylistEntity? = null
    private var castJob: Job? = null
    private var progressJob: Job? = null
    private var liveRetries = 0
    /** "Stats for nerds", read while its panel is open. */
    private val stats = PlaybackStats(factory.traffic)

    /** Plays [req]: from its start or resume point, or [continuing] what the player already has. */
    fun open(req: PlayRequest, continuing: Boolean = false) {
        saveProgress()
        channels = emptyList()
        scope.launch {
            // The playlist decides how a stream is adapted for Chromecast.
            if (playlist?.id != req.playlistId) playlist = container.playlists.get(req.playlistId)
            if (continuing) resume(req) else start(req)
            if (req.isLive) loadChannels(req)
        }
    }

    /** Shows [req], which the player is already playing, without restarting it. */
    private fun resume(req: PlayRequest) {
        request = req
        error = null
        liveRetries = 0
        exoPlayer.playWhenReady = true
        recordHistory(req, 0, 0)
        startProgressUpdates()
        onStarted(req, true)
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
            castJob = scope.launch {
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
        onStarted(req, false)
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
            Toast.makeText(context, "Recordings can only play on this device", Toast.LENGTH_SHORT).show()
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

    fun isCasting() = castPlayer != null && activePlayer === castPlayer

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
            Log.w(TAG, "Couldn't read soundtracks before casting", e)
            null
        }
    }

    // ---- Live channels ----

    private fun loadChannels(req: PlayRequest) {
        scope.launch {
            val content = container.content
            channels = if (req.categoryId == Categories.FAVORITES) {
                content.favoriteItems(req.playlistId, ContentKind.LIVE, 2000).first()
            } else {
                content.channelList(req.playlistId, req.categoryId)
            }
        }
    }

    /** Switch to the previous (-1) or next (+1) channel in the current list. */
    fun zap(direction: Int) {
        val list = channels
        val current = request ?: return
        if (list.isEmpty()) return
        val index = list.indexOfFirst { it.streamId == current.streamId }
        val next = list[((if (index < 0) 0 else index + direction) % list.size + list.size) % list.size]
        playChannel(next)
    }

    fun playChannel(stream: StreamEntity) {
        val current = request ?: return
        if (stream.streamId != current.streamId) {
            previousChannel = channels.firstOrNull { it.streamId == current.streamId } ?: currentChannel()
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
        scope.launch {
            val channelId = stream.epgChannelId ?: return@launch
            val now = container.content.schedule(stream.playlistId, channelId, limit = 1).firstOrNull()
            if (now != null && now.startMs <= System.currentTimeMillis() && request?.streamId == stream.streamId) {
                request = request?.copy(subtitle = now.title)
            }
        }
    }

    /**
     * A channel or past programme picked somewhere else, such as the guide over the picture. A
     * channel from another category brings that category's channels, so switching moves through them.
     */
    fun playPicked(req: PlayRequest) {
        val current = request
        if (req.isLive && current != null && req.streamId != current.streamId) {
            previousChannel = channels.firstOrNull { it.streamId == current.streamId } ?: currentChannel()
        }
        saveProgress()
        start(req)
        if (req.isLive && req.categoryId != current?.categoryId) loadChannels(req)
    }

    fun retry() {
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
                scope.launch {
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

        override fun onVideoSizeChanged(videoSize: VideoSize) = this@PlaybackEngine.onVideoSizeChanged()

        override fun onIsPlayingChanged(isPlaying: Boolean) = this@PlaybackEngine.onIsPlayingChanged(isPlaying)
    }

    // After the listener it adds.
    init {
        exoPlayer.addListener(listener)
        exoPlayer.addAnalyticsListener(stats)
        setUpCast()
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
        progressJob = scope.launch {
            while (isActive) {
                delay(15_000)
                if (player.isPlaying) saveProgress()
            }
        }
    }

    /** Keeps the resume point of what's playing (not live channels). */
    fun saveProgress() {
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

    /** The stats panel's rows: the stream as the local player sees it. */
    fun statsRows(display: Display?): List<Pair<String, String>> {
        if (isCasting()) return listOf("Casting" to "Stats come from this device's player, which isn't playing")
        return stats.rows(exoPlayer, display)
    }

    /**
     * Lets go of the players' listeners and the Chromecast player, and stops the jobs it started.
     * The [exoPlayer] is its owner's to release or keep (a TV hands it to the mini player).
     */
    fun detach() {
        progressJob?.cancel()
        castJob?.cancel()
        exoPlayer.removeListener(listener)
        exoPlayer.removeAnalyticsListener(stats)
        castPlayer?.let {
            it.setSessionAvailabilityListener(null)
            it.removeListener(listener)
            it.release()
        }
        castPlayer = null
    }

    private companion object {
        const val TAG = "PlaybackEngine"
    }
}
