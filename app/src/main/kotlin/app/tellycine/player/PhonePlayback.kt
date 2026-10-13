package app.tellycine.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient

/**
 * The phone's player, kept by the app rather than a screen: it plays at the top of the app in
 * portrait while you browse, full screen in landscape, and in picture in picture, without starting
 * again in between. One thing plays at a time; [play] replaces it and [close] stops it.
 */
@OptIn(UnstableApi::class)
class PhonePlayback(context: Context, private val httpClient: OkHttpClient) {
    private val context = context.applicationContext

    /** What's playing, if anything. */
    var engine by mutableStateOf<PlaybackEngine?>(null)
        private set
    private var scope: CoroutineScope? = null

    var isPlaying by mutableStateOf(false)
        private set
    var videoSize by mutableStateOf(VideoSize.UNKNOWN)
        private set
    /** Fit, zoom or stretch ([RESIZE_MODES]). */
    var resizeMode by mutableIntStateOf(0)

    fun play(request: PlayRequest) {
        val playing = engine ?: start()
        playing.open(request)
    }

    private fun start(): PlaybackEngine {
        val factory = PlayerFactory(context, httpClient)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }
        lateinit var created: PlaybackEngine
        created = PlaybackEngine(
            context,
            factory,
            factory.create(),
            scope,
            onVideoSizeChanged = { videoSize = created.player.videoSize },
            onIsPlayingChanged = { isPlaying = it },
        )
        engine = created
        return created
    }

    /** Pauses what's playing on this device (a Chromecast carries on). */
    fun pause() {
        engine?.takeUnless { it.isCasting() }?.player?.pause()
    }

    /** Stops playing, keeping the resume point. */
    fun close() {
        val playing = engine ?: return
        playing.saveProgress()
        playing.detach()
        playing.exoPlayer.release()
        scope?.cancel()
        scope = null
        engine = null
        isPlaying = false
        videoSize = VideoSize.UNKNOWN
    }
}
