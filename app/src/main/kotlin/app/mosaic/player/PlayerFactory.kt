package app.mosaic.player

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
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import app.mosaic.data.net.DEFAULT_USER_AGENT
import okhttp3.OkHttpClient

/** Builds an ExoPlayer tuned for IPTV (MPEG-TS quirks, generous buffering, per-stream headers). */
@OptIn(UnstableApi::class)
class PlayerFactory(private val context: Context, httpClient: OkHttpClient) {

    private val httpDataSourceFactory = OkHttpDataSource.Factory(httpClient).setUserAgent(DEFAULT_USER_AGENT)

    /** Streams over OkHttp; also reads local files, for downloads. */
    private val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

    private fun extractors() = DefaultExtractorsFactory()
        .setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
        )
        .setConstantBitrateSeekingEnabled(true)

    /** Reads a stream's tracks without playing it, e.g. to check soundtracks before casting. */
    fun probeSourceFactory(request: PlayRequest): MediaSource.Factory {
        applyHeaders(request)
        return DefaultMediaSourceFactory(dataSourceFactory, extractors())
    }

    fun create(): ExoPlayer {
        val extractors = extractors()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000,
            )
            .build()

        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)

        return ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory, extractors))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
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
