package app.tube.core

import app.tube.core.xtream.XtreamParser
import app.tube.core.xtream.XtreamUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class CatchupTest {
    // 2024-01-31 20:00:00 UTC to 21:30 UTC
    private val start = 1706731200000L
    private val stop = start + 90 * 60_000L
    private val now = start + 3 * 60 * 60_000L

    @Test
    fun xtreamUsesServerTimezone() {
        val urls = XtreamUrls("http://h:8080", "u", "p")
        assertEquals(
            "http://h:8080/timeshift/u/p/90/2024-01-31:20-00/42.ts",
            Catchup.xtream(urls, "42", start, stop, ZoneOffset.UTC),
        )
        assertEquals(
            "http://h:8080/timeshift/u/p/90/2024-01-31:21-00/42.ts",
            Catchup.xtream(urls, "42", start, stop, Catchup.zoneOrUtc("Europe/Paris")),
        )
        assertEquals(ZoneOffset.UTC, Catchup.zoneOrUtc("Not/AZone"))
    }

    @Test
    fun m3uTemplates() {
        assertEquals(
            "http://a/archive?start=1706731200&dur=5400",
            Catchup.m3u("http://a/live", "default", "http://a/archive?start={utc}&dur={duration}", start, stop, now),
        )
        assertEquals(
            "http://a/live.m3u8?utc=1706731200&lutc=1706742000",
            Catchup.m3u("http://a/live.m3u8", "shift", null, start, stop, now),
        )
        assertEquals(
            "http://a/live?archive=2024-01-31T20:00",
            Catchup.m3u("http://a/live", "append", "?archive=\${Y}-\${m}-\${d}T{H}:{M}", start, stop, now),
        )
        assertEquals(
            "http://f/ch1/index-1706731200-5400.m3u8?token=x",
            Catchup.m3u("http://f/ch1/index.m3u8?token=x", "flussonic", null, start, stop, now),
        )
        assertEquals("http://f/ch1/timeshift_abs-1706731200.ts", Catchup.m3u("http://f/ch1/mpegts", "flussonic", null, start, stop, now))
        assertNull(Catchup.m3u("http://a/live", null, null, start, stop, now))
    }

    @Test
    fun detectsDispatcharr() {
        val parser = XtreamParser()
        val dispatcharr = parser.parseAccount(
            """{"user_info":{"auth":1,"message":"Dispatcharr XC API","allowed_output_formats":["ts","mp4"]},
               "server_info":{"timezone":"UTC"}}""".reader()
        )
        assertTrue(dispatcharr.isDispatcharr)
        assertEquals("UTC", dispatcharr.serverTimezone)
        val other = parser.parseAccount("""{"user_info":{"auth":1,"message":"Welcome"}}""".reader())
        assertFalse(other.isDispatcharr)
    }
}
