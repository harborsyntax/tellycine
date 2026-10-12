package app.tellycine.data.repo

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.TellycineApp
import app.tellycine.testing.FakeHttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Checking an M3U link's server for an Xtream Codes API. */
@RunWith(AndroidJUnit4::class)
class XtreamFinderTest {
    private val finder = ApplicationProvider.getApplicationContext<TellycineApp>().container.xtreamFinder

    /** An Xtream Codes panel: user "u" with password "p", and its M3U links. */
    private val panel = FakeHttpServer { path, query ->
        when (path) {
            "/player_api.php" -> if ("username=u&password=p" in query) """{"user_info":{"auth":1}}""" else """{"user_info":{"auth":0}}"""
            "/get.php", "/lists/mine.m3u" -> "#EXTM3U"
            else -> null
        }
    }

    /** An ordinary web server with only a playlist. */
    private val plain = FakeHttpServer { path, _ -> if (path == "/mine.m3u") "#EXTM3U" else null }

    @After
    fun stop() {
        panel.close()
        plain.close()
    }

    private fun check(url: String) = runBlocking { finder.check(url, null) }

    @Test
    fun aLinkWhoseLoginWorksWithTheApi() {
        assertEquals(
            XtreamOffer(panel.url, "u", "p", loginWorks = true),
            check("${panel.url}/get.php?username=u&password=p&type=m3u_plus&output=ts"),
        )
    }

    @Test
    fun aServerWithAnApiButNoLoginInTheLink() {
        assertEquals(XtreamOffer(panel.url, "", "", loginWorks = false), check("${panel.url}/lists/mine.m3u"))
        // A login the API doesn't accept is still offered, to correct.
        assertEquals(XtreamOffer(panel.url, "u", "wrong", loginWorks = false), check("${panel.url}/get.php?username=u&password=wrong"))
    }

    @Test
    fun aServerWithoutAnApi() {
        assertNull(check("${plain.url}/mine.m3u"))
    }
}
