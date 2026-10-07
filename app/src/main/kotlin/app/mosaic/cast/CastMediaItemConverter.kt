package app.mosaic.cast

import androidx.annotation.OptIn
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.HlsSegmentFormat
import com.google.android.gms.cast.HlsVideoSegmentFormat
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem

/**
 * Media3's default converter sends every item as on-demand ("buffered") media. A live HLS
 * channel sent that way makes the receiver play only the segments listed when it loaded (a
 * minute or two), then stall. This marks live channels as live, and tells the receiver when
 * HLS segments are MPEG-TS so it picks the right pipeline.
 */
@OptIn(UnstableApi::class)
class CastMediaItemConverter : MediaItemConverter {
    private val default = DefaultMediaItemConverter()

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val item = default.toMediaQueueItem(mediaItem)
        val extras = mediaItem.mediaMetadata.extras
        val live = extras?.getBoolean(CastMedia.EXTRA_LIVE) == true
        val tsSegments = extras?.getBoolean(CastMedia.EXTRA_TS_SEGMENTS) == true
        val info = item.media
        if ((!live && !tsSegments) || info == null) return item

        val builder = MediaInfo.Builder(info.contentId)
            .setStreamType(if (live) MediaInfo.STREAM_TYPE_LIVE else info.streamType)
        info.contentType?.let { builder.setContentType(it) }
        info.contentUrl?.let { builder.setContentUrl(it) }
        info.metadata?.let { builder.setMetadata(it) }
        // Kept so the item converts back to a MediaItem (Media3 stores it there).
        info.customData?.let { builder.setCustomData(it) }
        if (live) builder.setStreamDuration(MediaInfo.UNKNOWN_DURATION)
        if (tsSegments) {
            builder.setHlsSegmentFormat(HlsSegmentFormat.TS)
            builder.setHlsVideoSegmentFormat(HlsVideoSegmentFormat.MPEG2_TS)
        }
        return MediaQueueItem.Builder(builder.build()).build()
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem = default.toMediaItem(mediaQueueItem)
}
