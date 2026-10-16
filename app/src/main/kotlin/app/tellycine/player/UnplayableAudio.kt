package app.tellycine.player

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks

/**
 * The format of the sound in [tracks] when there is sound and none of it can be played on this
 * device ("Dolby AC-4", or several joined with "or"); null when some of it can, or there's none.
 * The player then plays the picture alone, so this says why there's no sound.
 */
fun unplayableAudio(tracks: Tracks): String? {
    val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    if (audio.isEmpty() || audio.any { it.isSupported(/* allowExceedsCapabilities = */ true) }) return null
    return audio.flatMap { group -> (0 until group.length).map { audioFormatName(group.getTrackFormat(it).sampleMimeType) } }
        .distinct()
        .joinToString(" or ")
}

/** What a sound format is called, for people: "Dolby Digital (AC-3)". */
fun audioFormatName(mimeType: String?): String = when (mimeType) {
    MimeTypes.AUDIO_AC3 -> "Dolby Digital (AC-3)"
    MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Digital Plus (E-AC-3)"
    MimeTypes.AUDIO_AC4 -> "Dolby AC-4"
    MimeTypes.AUDIO_TRUEHD -> "Dolby TrueHD"
    MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS, MimeTypes.AUDIO_DTS_X -> "DTS"
    MimeTypes.AUDIO_MPEG_L1 -> "MPEG audio (MP1)"
    MimeTypes.AUDIO_MPEG_L2 -> "MPEG audio (MP2)"
    MimeTypes.AUDIO_MPEGH_MHA1, MimeTypes.AUDIO_MPEGH_MHM1 -> "MPEG-H"
    null -> "an unknown format"
    else -> mimeType.substringAfter('/').uppercase()
}
