package app.tellycine.core

import app.tellycine.core.xmltv.GuideUrls
import org.junit.Assert.assertEquals
import org.junit.Test

class GuideUrlsTest {
    @Test
    fun readsOnePerLineAndOlderCommaSeparatedSettings() {
        assertEquals(listOf("http://a/1.xml", "http://b/2.xml.gz"), GuideUrls.split("http://a/1.xml\nhttp://b/2.xml.gz\n"))
        assertEquals(listOf("http://a/1.xml", "http://b/2.xml"), GuideUrls.split(" http://a/1.xml, http://b/2.xml ,http://a/1.xml"))
        assertEquals(emptyList<String>(), GuideUrls.split("  \n , "))
    }

    @Test
    fun storesOnePerLineWithoutBlanksOrRepeats() {
        assertEquals("http://a/1.xml\nhttp://b/2.xml", GuideUrls.join(listOf(" http://a/1.xml ", "", "http://b/2.xml", "http://a/1.xml")))
        assertEquals("", GuideUrls.join(listOf("", "  ")))
    }

    @Test
    fun addedGuidesReplaceThePlaylistsOwnUnlessCombined() {
        val provider = "http://provider/xmltv.php?u=1"
        val mine = "http://a/1.xml\nhttp://b/2.xml"
        assertEquals(listOf(provider), GuideUrls.toLoad("", provider, combine = false))
        assertEquals(listOf(provider), GuideUrls.toLoad("", provider, combine = true))
        assertEquals(listOf("http://a/1.xml", "http://b/2.xml"), GuideUrls.toLoad(mine, provider, combine = false))
        // Combined, the provider's comes first, so its channels are matched before the added guides fill in.
        assertEquals(listOf(provider, "http://a/1.xml", "http://b/2.xml"), GuideUrls.toLoad(mine, provider, combine = true))
        assertEquals(listOf("http://a/1.xml", "http://b/2.xml"), GuideUrls.toLoad(mine, "", combine = true))
    }
}
