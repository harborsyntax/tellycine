package app.tellycine.player

import android.view.Display
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/** Counts the bytes the player downloads, for the stream's download rate. */
@OptIn(UnstableApi::class)
class TrafficMeter : TransferListener {
    val bytes = AtomicLong()
    override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
    override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
        if (isNetwork) bytes.addAndGet(bytesTransferred.toLong())
    }
}

/**
 * "Stats for nerds": what the player knows about the stream playing, read once a second while the
 * panel is open. Add it to the player with [ExoPlayer.addAnalyticsListener] for the decoder names.
 */
@OptIn(UnstableApi::class)
class PlaybackStats(private val traffic: TrafficMeter) : AnalyticsListener {
    private var videoDecoder: String? = null
    private var audioDecoder: String? = null
    private var lastBytes = traffic.bytes.get()
    private var lastAt = System.nanoTime()
    private var rate: Long? = null

    override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
        videoDecoder = decoderName
    }

    override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
        audioDecoder = decoderName
    }

    /** Label and value rows describing [player] now; [display] adds the screen's mode. */
    fun rows(player: ExoPlayer, display: Display?): List<Pair<String, String>> = buildList {
        val video = player.videoFormat
        val audio = player.audioFormat

        val type = when (player.currentMediaItem?.localConfiguration?.mimeType) {
            MimeTypes.APPLICATION_M3U8 -> "HLS"
            MimeTypes.APPLICATION_MPD -> "DASH"
            else -> (video ?: audio)?.containerMimeType?.let(::containerName) ?: "Progressive"
        }
        add("Stream" to type)

        if (video != null) {
            add("Video" to listOfNotNull(
                codecName(video),
                if (video.width > 0 && video.height > 0) "${video.width}×${video.height}" else null,
                video.frameRate.takeIf { it > 0 }?.let { "${decimal(it)} fps" },
                bitrateOf(video)?.let(::formatBitrate),
            ).joinToString("  "))
            add("Colour" to colourOf(video))
            videoDecoder?.let { add("Video decoder" to it) }
        }
        if (audio != null) {
            add("Audio" to listOfNotNull(
                codecName(audio),
                audio.channelCount.takeIf { it > 0 }?.let(::channelName),
                audio.sampleRate.takeIf { it > 0 }?.let { "${it / 1000.0} kHz" },
                bitrateOf(audio)?.let(::formatBitrate),
                audio.language?.takeIf { it != C.LANGUAGE_UNDETERMINED },
            ).joinToString("  "))
            audioDecoder?.let { add("Audio decoder" to it) }
        }

        // Download rate over the last refresh: once the buffer is full, about the stream's bitrate.
        val now = System.nanoTime()
        val bytes = traffic.bytes.get()
        val seconds = (now - lastAt) / 1e9
        if (seconds >= 0.5) {
            rate = ((bytes - lastBytes) * 8 / seconds).toLong()
            lastBytes = bytes
            lastAt = now
        }
        rate?.let { add("Download" to formatBitrate(it)) }

        add("Buffer" to "%.1f s".format(Locale.US, player.totalBufferedDuration / 1000.0))
        player.currentLiveOffset.takeIf { it != C.TIME_UNSET && player.isCurrentMediaItemLive }?.let {
            add("Behind live" to "%.1f s".format(Locale.US, it / 1000.0))
        }
        player.videoDecoderCounters?.let { c ->
            c.ensureUpdated()
            add("Frames" to "${c.renderedOutputBufferCount} shown, ${c.droppedBufferCount} dropped, ${c.skippedOutputBufferCount} skipped")
        }
        display?.mode?.let { mode ->
            add("Display" to "${mode.physicalWidth}×${mode.physicalHeight} @ ${decimal(mode.refreshRate)} Hz")
        }
    }

    private fun bitrateOf(format: Format): Int? =
        listOf(format.averageBitrate, format.bitrate, format.peakBitrate).firstOrNull { it != Format.NO_VALUE && it > 0 }

    private fun colourOf(format: Format): String {
        val info = format.colorInfo
        val dolbyVision = format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION
        val range = when (info?.colorTransfer) {
            C.COLOR_TRANSFER_ST2084 -> if (format.colorInfo?.hdrStaticInfo != null) "HDR10 (PQ, with metadata)" else "HDR10 (PQ)"
            C.COLOR_TRANSFER_HLG -> "HLG"
            C.COLOR_TRANSFER_SDR, C.COLOR_TRANSFER_GAMMA_2_2 -> "SDR"
            else -> null
        }
        val space = when (info?.colorSpace) {
            C.COLOR_SPACE_BT2020 -> "BT.2020"
            C.COLOR_SPACE_BT709 -> "BT.709"
            C.COLOR_SPACE_BT601 -> "BT.601"
            else -> null
        }
        val levels = when (info?.colorRange) {
            C.COLOR_RANGE_LIMITED -> "limited range"
            C.COLOR_RANGE_FULL -> "full range"
            else -> null
        }
        val depth = info?.lumaBitdepth?.takeIf { it != Format.NO_VALUE }?.let { "$it-bit" }
        return listOfNotNull(if (dolbyVision) "Dolby Vision" else range, space, levels, depth)
            .ifEmpty { listOf("Not signalled (usually SDR)") }
            .joinToString(", ")
    }

    private fun codecName(format: Format): String {
        val name = when (format.sampleMimeType) {
            MimeTypes.VIDEO_H264 -> "H.264"
            MimeTypes.VIDEO_H265 -> "HEVC"
            MimeTypes.VIDEO_AV1 -> "AV1"
            MimeTypes.VIDEO_VP9 -> "VP9"
            MimeTypes.VIDEO_MPEG2 -> "MPEG-2"
            MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision"
            MimeTypes.AUDIO_AAC -> "AAC"
            MimeTypes.AUDIO_AC3 -> "Dolby Digital (AC-3)"
            MimeTypes.AUDIO_E_AC3 -> "Dolby Digital Plus (E-AC-3)"
            MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Atmos (E-AC-3 JOC)"
            MimeTypes.AUDIO_AC4 -> "Dolby AC-4"
            MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD -> "DTS"
            MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_MPEG_L2 -> "MPEG audio"
            MimeTypes.AUDIO_OPUS -> "Opus"
            MimeTypes.AUDIO_TRUEHD -> "Dolby TrueHD"
            else -> format.sampleMimeType?.substringAfter('/') ?: "Unknown"
        }
        return format.codecs?.let { "$name ($it)" } ?: name
    }

    private fun containerName(mime: String) = when (mime) {
        MimeTypes.VIDEO_MP2T -> "MPEG-TS"
        MimeTypes.VIDEO_MP4, MimeTypes.AUDIO_MP4 -> "MP4"
        MimeTypes.VIDEO_MATROSKA, MimeTypes.VIDEO_WEBM -> "Matroska"
        else -> mime.substringAfter('/')
    }

    private fun channelName(count: Int) = when (count) {
        1 -> "mono"
        2 -> "stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> "$count channels"
    }

    /** 25 or 29.97, as a frame or refresh rate is usually written. */
    private fun decimal(value: Float) =
        if (kotlin.math.abs(value - value.roundToInt()) < 0.01f) "${value.roundToInt()}" else "%.2f".format(Locale.US, value)

    private fun formatBitrate(bps: Int) = formatBitrate(bps.toLong())

    private fun formatBitrate(bps: Long) =
        if (bps >= 1_000_000) "%.1f Mbps".format(Locale.US, bps / 1e6) else "${(bps / 1e3).roundToInt()} kbps"
}
