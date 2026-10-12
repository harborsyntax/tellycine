package app.tellycine.ui.playlists

import app.tellycine.data.db.PlaylistType
import app.tellycine.data.repo.XtreamOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamHintTest {
    private val link = "http://h:8080/get.php?username=u&password=p&type=m3u_plus"
    private val form = PlaylistForm(type = PlaylistType.M3U_URL, url = link)

    @Test
    fun beforeTheCheckTheLinksShapeSuggestsIt() {
        val hint = xtreamHint(form, null)!!
        assertEquals(XtreamOffer("http://h:8080", "u", "p", loginWorks = false), hint.offer)
        assertTrue(hint.message.startsWith("This looks like"))
    }

    @Test
    fun theCheckDecides() {
        val works = xtreamHint(form, XtreamCheck(link, XtreamOffer("http://h:8080", "u", "p", loginWorks = true)))!!
        assertTrue(works.message.contains("this link's login works"))
        // Checked, and no API there: nothing to suggest, whatever the link looks like.
        assertNull(xtreamHint(form, XtreamCheck(link, null)))
        // A check of another link doesn't count.
        assertTrue(xtreamHint(form, XtreamCheck("http://other/x.m3u", null))!!.message.startsWith("This looks like"))
    }

    @Test
    fun aPlainLinkWhoseServerHasAnApi() {
        val plain = PlaylistForm(type = PlaylistType.M3U_URL, url = "http://h/mine.m3u")
        assertNull(xtreamHint(plain, null))
        val hint = xtreamHint(plain, XtreamCheck("http://h/mine.m3u", XtreamOffer("http://h", "", "", loginWorks = false)))!!
        assertTrue(hint.message.contains("also offers Xtream Codes"))
    }
}
