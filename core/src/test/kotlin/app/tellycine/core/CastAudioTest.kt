package app.tellycine.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastAudioTest {
    @Test
    fun compatibleFirstSoundtrackNeedsNoWarning() {
        assertNull(CastAudio.notice(listOf(AudioTrackInfo("audio/mp4a-latm"), AudioTrackInfo("audio/ac3"))))
        assertNull(CastAudio.notice(emptyList()))
    }

    @Test
    fun dolbyFirstWarnsAndMentionsACompatibleAlternative() {
        val notice = CastAudio.notice(listOf(AudioTrackInfo("audio/eac3", "en"), AudioTrackInfo("audio/mp4a-latm", "en")))!!
        assertTrue(notice.startsWith("This movie's sound is Dolby Digital Plus."))
        assertTrue(notice.endsWith("The file also has an AAC soundtrack (English), but Chromecast can't be told to use it."))
    }

    @Test
    fun dtsFirstWithoutAlternativeHasNoSound() {
        assertEquals(
            "This movie's sound is DTS, which Chromecast can't play. There'll be no sound on the TV.",
            CastAudio.notice(listOf(AudioTrackInfo("audio/vnd.dts.hd"), AudioTrackInfo("audio/ac3"))),
        )
    }

    @Test
    fun dtsFirstMayFallBack() {
        assertEquals(
            "This movie's sound is DTS, which Chromecast can't play. It may fall back to an MP3 soundtrack; if not, there'll be no sound.",
            CastAudio.notice(listOf(AudioTrackInfo("audio/vnd.dts"), AudioTrackInfo("audio/mpeg", "und"))),
        )
    }
}
