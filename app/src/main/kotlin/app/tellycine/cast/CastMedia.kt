package app.tellycine.cast

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import app.tellycine.player.PlayRequest

/** Builds media items the Cast Default Media Receiver can play. */
object CastMedia {
    /** Media metadata extras read by [CastMediaItemConverter]. */
    const val EXTRA_LIVE = "app.tellycine.cast.LIVE"
    const val EXTRA_TS_SEGMENTS = "app.tellycine.cast.TS_SEGMENTS"

    /**
     * Chromecast cannot play raw MPEG-TS, so Xtream live channels are switched to a format it can:
     * the panel's HLS output, or for Dispatcharr (0.25+) its fragmented MP4 output, optionally
     * through [outputProfileId] (an output profile re-encoding the audio to AAC).
     */
    fun castUrl(request: PlayRequest, playlist: PlaylistEntity?, outputProfileId: Long? = null): String {
        val url = request.url
        if (request.isLive && playlist?.type == PlaylistType.XTREAM) {
            val path = Uri.parse(url).path.orEmpty()
            if (path.contains("/live/") && path.endsWith(".ts")) {
                val ts = Regex("""\.ts(?=($|\?))""")
                if (!playlist.isDispatcharr) return url.replaceFirst(ts, ".m3u8")
                val mp4 = url.replaceFirst(ts, ".mp4")
                return if (outputProfileId == null) mp4
                else Uri.parse(mp4).buildUpon().appendQueryParameter("output_profile", outputProfileId.toString()).build().toString()
            }
        }
        return url
    }

    fun mediaItem(request: PlayRequest, playlist: PlaylistEntity?, outputProfileId: Long? = null): MediaItem {
        val url = castUrl(request, playlist, outputProfileId)
        val extras = Bundle().apply {
            putBoolean(EXTRA_LIVE, request.isLive)
            // Xtream's HLS output (switched to above) is a playlist of MPEG-TS segments.
            putBoolean(EXTRA_TS_SEGMENTS, url != request.url && Uri.parse(url).path.orEmpty().endsWith(".m3u8"))
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
