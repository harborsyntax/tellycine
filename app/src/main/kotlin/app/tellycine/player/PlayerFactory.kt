package app.tellycine.player

import androidx.media3.exoplayer.source.MediaSource
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import app.tellycine.data.net.DEFAULT_USER_AGENT
import okhttp3.OkHttpClient

/** Builds an ExoPlayer tuned for IPTV (MPEG-TS quirks, generous buffering, per-stream headers). */
@OptIn(UnstableApi::class)
class PlayerFactory(private val context: Context, httpClient: OkHttpClient) {

    /** Bytes downloaded, for the stream's download rate in the stats panel. */
    val traffic = TrafficMeter()

    private val httpDataSourceFactory = OkHttpDataSource.Factory(httpClient)
        .setUserAgent(DEFAULT_USER_AGENT)
        .setTransferListener(traffic)

    /** Streams over OkHttp; also reads local files, for downloads. */
    private val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

    private fun extractors() = DefaultExtractorsFactory()
        // MPEG-TS is read the way Media3 reads HLS, with no extra flags. Splitting H.264 into
        // frames by slice headers (FLAG_DETECT_ACCESS_UNITS) can cut an interlaced frame into its
        // two fields, and starting on any I-frame rather than an IDR frame
        // (FLAG_ALLOW_NON_IDR_KEYFRAMES) can stall the decoder: on TVs and phones alike, the
        // picture froze on live channels while the sound carried on.
        .setTsExtractorFlags(0)
        .setConstantBitrateSeekingEnabled(true)

    /** Reads a stream's tracks without playing it, e.g. to check soundtracks before casting. */
    fun probeSourceFactory(request: PlayRequest): MediaSource.Factory {
        applyHeaders(request)
        return DefaultMediaSourceFactory(dataSourceFactory, extractors())
    }

    /**
     * A player for one stream, or with [multiView] for one of several playing at once: their
     * sound is decoded here so it can be mixed (a TV or receiver takes only one Dolby stream at a
     * time), none takes the audio focus from the others, and smaller buffers keep four of them
     * within a TV's memory.
     */
    fun create(multiView: Boolean = false): ExoPlayer {
        val extractors = extractors()

        val loadControl = DefaultLoadControl.Builder()
            .apply {
                if (multiView) {
                    setBufferDurationsMs(8_000, 20_000, 1_500, 3_000)
                    setTargetBufferBytes(16 * 1024 * 1024)
                } else {
                    setBufferDurationsMs(
                        /* minBufferMs = */ 15_000,
                        /* maxBufferMs = */ 50_000,
                        /* bufferForPlaybackMs = */ 1_500,
                        /* bufferForPlaybackAfterRebufferMs = */ 3_000,
                    )
                }
            }
            .build()

        val renderers = (if (multiView) MixableAudioRenderersFactory(context) else DefaultRenderersFactory(context))
            .setEnableDecoderFallback(true)

        return ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory, extractors))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ !multiView,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
            .apply {
                // A quarter or half of the screen needs no more than 720p, where a stream offers a choice.
                if (multiView) trackSelectionParameters = trackSelectionParameters.buildUpon().setMaxVideoSize(1280, 720).build()
            }
    }

    /** Applies request headers; must be called before preparing each new item. */
    fun applyHeaders(request: PlayRequest) {
        httpDataSourceFactory.setUserAgent(request.userAgent?.ifBlank { null } ?: DEFAULT_USER_AGENT)
        httpDataSourceFactory.setDefaultRequestProperties(
            buildMap {
                request.referrer?.ifBlank { null }?.let { put("Referer", it) }
                request.apiKey?.ifBlank { null }?.let { put("X-API-Key", it) }
            }
        )
    }

    fun mediaItem(request: PlayRequest): MediaItem {
        val path = Uri.parse(request.url).path?.lowercase().orEmpty()
        val builder = MediaItem.Builder()
            .setUri(request.localUri ?: request.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(request.title)
                    .setArtist(request.subtitle)
                    .setArtworkUri(request.artwork?.let(Uri::parse))
                    .build()
            )
        when {
            path.endsWith(".m3u8") || path.endsWith(".m3u") -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            path.endsWith(".mpd") -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
        }
        return builder.build()
    }
}

/** Decodes all sound to PCM rather than passing Dolby through, so several players' sound can mix. */
@OptIn(UnstableApi::class)
private class MixableAudioRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    @Suppress("DEPRECATION")
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
        DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
            .build()
}
