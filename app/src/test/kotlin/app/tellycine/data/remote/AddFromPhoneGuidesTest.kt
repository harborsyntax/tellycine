package app.tellycine.data.remote

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.TellycineApp
import app.tellycine.testing.FakeHttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The guide URLs and the tick box from the add-from-phone page. */
@RunWith(AndroidJUnit4::class)
class AddFromPhoneGuidesTest {
    private val provider = FakeHttpServer { _, _ -> "#EXTM3U" }
    private val app = ApplicationProvider.getApplicationContext<TellycineApp>()

    @After
    fun stop() = provider.close()

    private fun add(vararg fields: Pair<String, String>) = runBlocking {
        addPlaylistFromPhone(app, app.container, PhoneSubmission(mapOf("type" to "m3u", "url" to "${provider.url}/list.m3u", *fields), null)).getOrThrow()
        runBlocking(Dispatchers.IO) { app.container.database.playlists().getAll().single() }
    }

    @Test
    fun keepsEveryGuideFieldInTheOrderAddedAndTheTickBox() {
        val saved = add("epg" to "http://a/1.xml", "epg3" to "http://c/3.xml", "epg2" to " http://b/2.xml ", "epgCombine" to "on")
        assertEquals("http://a/1.xml\nhttp://b/2.xml\nhttp://c/3.xml", saved.epgUrl)
        assertTrue(saved.epgCombine)
    }

    @Test
    fun untickedTheyReplaceThePlaylistsOwnGuide() {
        val saved = add("epg" to "http://a/1.xml", "epg2" to "")
        assertEquals("http://a/1.xml", saved.epgUrl)
        assertFalse(saved.epgCombine)
    }
}
