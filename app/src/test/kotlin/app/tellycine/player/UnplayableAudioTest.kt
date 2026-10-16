package app.tellycine.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Saying why a stream plays without sound: its sound is in a format this device can't play. */
@RunWith(AndroidJUnit4::class)
class UnplayableAudioTest {
    private fun track(mimeType: String, supported: Boolean) = Tracks.Group(
        TrackGroup(Format.Builder().setSampleMimeType(mimeType).build()),
        /* adaptiveSupported = */ false,
        intArrayOf(if (supported) C.FORMAT_HANDLED else C.FORMAT_UNSUPPORTED_SUBTYPE),
        booleanArrayOf(supported),
    )

    private fun tracks(vararg groups: Tracks.Group) = Tracks(groups.toList())

    @Test
    fun namesTheSoundNoneOfWhichCanBePlayed() {
        assertEquals("Dolby AC-4", unplayableAudio(tracks(track(MimeTypes.VIDEO_H264, true), track(MimeTypes.AUDIO_AC4, false))))
        assertEquals(
            "Dolby Digital (AC-3) or DTS",
            unplayableAudio(tracks(track(MimeTypes.AUDIO_AC3, false), track(MimeTypes.AUDIO_DTS, false), track(MimeTypes.AUDIO_DTS_HD, false))),
        )
    }

    @Test
    fun saysNothingWhenSomeSoundCanBePlayedOrThereIsNone() {
        // Another language's track it can play is enough.
        assertNull(unplayableAudio(tracks(track(MimeTypes.AUDIO_AC3, false), track(MimeTypes.AUDIO_AAC, true))))
        assertNull(unplayableAudio(tracks(track(MimeTypes.VIDEO_H264, true))))
        // Before the stream's tracks are known.
        assertNull(unplayableAudio(Tracks.EMPTY))
    }

    @Test
    fun namesFormatsForPeople() {
        assertEquals("Dolby Digital Plus (E-AC-3)", audioFormatName(MimeTypes.AUDIO_E_AC3_JOC))
        assertEquals("MPEG audio (MP2)", audioFormatName(MimeTypes.AUDIO_MPEG_L2))
        assertEquals("MPEG-H", audioFormatName(MimeTypes.AUDIO_MPEGH_MHM1))
        assertEquals("X-SOMETHING", audioFormatName("audio/x-something"))
        assertEquals("an unknown format", audioFormatName(null))
    }
}
