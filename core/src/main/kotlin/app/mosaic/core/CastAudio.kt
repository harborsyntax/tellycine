package app.mosaic.core

import java.util.Locale

/** An audio track in a media file, as the player reports it. */
data class AudioTrackInfo(val mimeType: String?, val language: String? = null)

/**
 * Explains, before casting, when a movie's sound probably won't play on a Chromecast.
 *
 * Chromecast's built-in player plays the file's first soundtrack and can't be told to use
 * another. It can't decode Dolby itself (it passes Dolby to the TV or soundbar, which may not
 * support it) and doesn't support DTS or TrueHD at all.
 */
object CastAudio {
    private val dolby = setOf("audio/ac3", "audio/eac3", "audio/eac3-joc", "audio/ac4")
    private val compatible = setOf(
        "audio/mp4a-latm", "audio/mpeg", "audio/mpeg-l1", "audio/mpeg-l2",
        "audio/opus", "audio/vorbis", "audio/flac", "audio/raw",
    )

    /** A warning to show while casting, or null when the first soundtrack should play. */
    fun notice(tracks: List<AudioTrackInfo>): String? {
        val first = tracks.firstOrNull()?.mimeType?.lowercase() ?: return null
        val unplayable = first.startsWith("audio/vnd.dts") || first == "audio/true-hd"
        if (first !in dolby && !unplayable) return null

        val alternative = tracks.drop(1).firstOrNull { it.mimeType?.lowercase() in compatible }
        val alternativeName = alternative?.let { describe(it) }
        return if (first in dolby) {
            "This movie's sound is ${name(first)}. Chromecast sends Dolby sound straight to your TV or soundbar, " +
                "so if they don't support it there'll be no sound." +
                (alternativeName?.let { " The file also has $it, but Chromecast can't be told to use it." } ?: "")
        } else {
            "This movie's sound is ${name(first)}, which Chromecast can't play." +
                (alternativeName?.let { " It may fall back to $it; if not, there'll be no sound." } ?: " There'll be no sound on the TV.")
        }
    }

    /** "an AAC soundtrack (English)" */
    private fun describe(track: AudioTrackInfo): String {
        val codec = name(track.mimeType?.lowercase())
        // By sound: "an AAC", "an MP3", "a FLAC".
        val article = if (codec in setOf("AAC", "MP3", "Opus")) "an" else "a"
        val language = track.language
            ?.takeIf { it.isNotBlank() && it != "und" }
            ?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH) }
            ?.takeIf { it.isNotBlank() }
        return "$article $codec soundtrack" + (language?.let { " ($it)" } ?: "")
    }

    fun name(mime: String?): String = when {
        mime == null -> "unknown"
        mime == "audio/ac3" -> "Dolby Digital"
        mime == "audio/eac3" -> "Dolby Digital Plus"
        mime == "audio/eac3-joc" -> "Dolby Atmos"
        mime == "audio/ac4" -> "Dolby AC-4"
        mime == "audio/true-hd" -> "Dolby TrueHD"
        mime.startsWith("audio/vnd.dts") -> "DTS"
        mime == "audio/mp4a-latm" -> "AAC"
        mime.startsWith("audio/mpeg") -> "MP3"
        mime == "audio/opus" -> "Opus"
        mime == "audio/vorbis" -> "Vorbis"
        mime == "audio/flac" -> "FLAC"
        mime == "audio/raw" -> "PCM"
        else -> mime.substringAfter('/').uppercase()
    }
}
