package app.mosaic.cast

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import app.mosaic.data.db.PlaylistEntity
import app.mosaic.data.db.PlaylistType
import app.mosaic.player.PlayRequest

/** Builds media items the Cast Default Media Receiver can play. */
object CastMedia {
    /** Media metadata extras read by [CastMediaItemConverter]. */
    const val EXTRA_LIVE = "app.mosaic.cast.LIVE"
    const val EXTRA_TS_SEGMENTS = "app.mosaic.cast.TS_SEGMENTS"

    /**
     * Chromecast cannot play raw MPEG-TS, so standard Xtream live channels are switched to
     * the panel's HLS output. Dispatcharr only serves TS, so its URLs are left unchanged.
     */
    fun castUrl(request: PlayRequest, playlist: PlaylistEntity?): String {
        val url = request.url
        if (request.isLive && playlist?.type == PlaylistType.XTREAM && !playlist.isDispatcharr) {
            val path = Uri.parse(url).path.orEmpty()
            if (path.contains("/live/") && path.endsWith(".ts")) {
                return url.replaceFirst(Regex("""\.ts(?=($|\?))"""), ".m3u8")
            }
        }
        return url
    }

    fun mediaItem(request: PlayRequest, playlist: PlaylistEntity?): MediaItem {
        val url = castUrl(request, playlist)
        val extras = Bundle().apply {
            putBoolean(EXTRA_LIVE, request.isLive)
            // Xtream's HLS output (switched to above) is a playlist of MPEG-TS segments.
            putBoolean(EXTRA_TS_SEGMENTS, url != request.url)
        }
        return MediaItem.Builder()
            .setUri(url)
            // CastPlayer needs an explicit MIME type for the receiver.
            .setMimeType(mimeType(url, request.isLive))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(request.title)
                    .setArtist(request.subtitle)
                    .setArtworkUri(request.artwork?.let(Uri::parse))
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    fun mimeType(url: String, live: Boolean): String {
        val path = Uri.parse(url).path?.lowercase().orEmpty()
        return when {
            path.endsWith(".m3u8") || path.endsWith(".m3u") -> MimeTypes.APPLICATION_M3U8
            path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
            path.endsWith(".mp4") || path.endsWith(".m4v") -> MimeTypes.VIDEO_MP4
            path.endsWith(".webm") -> MimeTypes.VIDEO_WEBM
            path.endsWith(".mkv") -> MimeTypes.VIDEO_MATROSKA
            path.endsWith(".ts") -> MimeTypes.VIDEO_MP2T
            // Extension-less live streams are most often HLS.
            live -> MimeTypes.APPLICATION_M3U8
            else -> MimeTypes.VIDEO_MP4
        }
    }
}
