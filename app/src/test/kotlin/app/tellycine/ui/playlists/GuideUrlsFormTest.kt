package app.tellycine.ui.playlists

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import app.tellycine.screenshots.ScreenshotDriver
import app.tellycine.testing.FakeHttpServer
import app.tellycine.ui.theme.TellycineTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Several XMLTV guide URLs for one playlist, added with +, and whether they replace its own guide. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class GuideUrlsFormTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val provider = FakeHttpServer { _, _ -> "#EXTM3U" }

    @After
    fun stop() = provider.close()

    private fun field(label: String) = compose.onNode(hasText(label) and hasSetTextAction())

    private fun press(text: String) {
        compose.onNodeWithText(text).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun playlistAt(url: String, d: ScreenshotDriver) =
        runBlocking(Dispatchers.IO) { d.app.container.database.playlists().getAll().single { it.url == url } }

    @Test
    fun plusAddsAnotherGuideAndTheTickBoxCombinesThem() {
        val d = ScreenshotDriver(compose)
        d.launch()
        d.openTab("Playlists", "Add playlist")
        d.click("Add playlist")
        d.waitForText("Playlist URL")
        val url = "${provider.url}/list.m3u"
        field("Playlist URL").performTextInput(url)
        press("Advanced options")

        // Nothing to combine until a guide is entered; then the tick box, unticked: they replace it.
        assertTrue(compose.onAllNodesWithText("Also use the guide linked in the playlist").fetchSemanticsNodes().isEmpty())
        field("XMLTV guide URL").performScrollTo().performTextInput("http://a.example/guide.xml")
        compose.onNodeWithText("Also use the guide linked in the playlist").performScrollTo().assertIsOff()
        press("Add another guide URL")
        field("XMLTV guide URL 2").performScrollTo().performTextInput("http://b.example/guide.xml.gz")
        press("Also use the guide linked in the playlist")
        d.waitForText("Ticked: it's loaded first")
        press("Add and load")

        // Saving finishes on the main thread, so let that run between looks.
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            runBlocking(Dispatchers.IO) { d.app.container.database.playlists().getAll().any { it.url == url } }
        }
        val saved = playlistAt(url, d)
        assertEquals("http://a.example/guide.xml\nhttp://b.example/guide.xml.gz", saved.epgUrl)
        assertTrue(saved.epgCombine)
    }

    @Test
    fun anEarlierCommaSeparatedSettingOpensAsSeveralFieldsAndOneCanBeRemoved() {
        val url = "${provider.url}/old.m3u"
        val d = ScreenshotDriver(compose)
        val id = runBlocking(Dispatchers.IO) {
            d.app.container.database.playlists().insert(
                PlaylistEntity(name = "Old", type = PlaylistType.M3U_URL, url = url, epgUrl = "http://a.example/1.xml, http://b.example/2.xml", epgCombine = true)
            )
        }
        compose.setContent { TellycineTheme(dynamicColor = false) { EditPlaylistScreen(editId = id, onDone = {}, onManageCategories = {}) } }

        // Advanced options open by themselves, with a field for each guide.
        d.waitForText("http://b.example/2.xml")
        field("XMLTV guide URL").assert(hasText("http://a.example/1.xml"))
        field("XMLTV guide URL 2").assert(hasText("http://b.example/2.xml"))
        compose.onNodeWithText("Also use the guide linked in the playlist").performScrollTo().assertIsOn()

        compose.onNodeWithContentDescription("Remove XMLTV guide URL").performScrollTo().performClick()
        compose.waitForIdle()
        field("XMLTV guide URL").assert(hasText("http://b.example/2.xml"))
        press("Also use the guide linked in the playlist")
        press("Save and reload")

        compose.waitUntil(5_000) {
            compose.waitForIdle()
            !playlistAt(url, d).epgCombine
        }
        val saved = playlistAt(url, d)
        assertEquals("http://b.example/2.xml", saved.epgUrl)
        assertFalse(saved.epgCombine)
    }
}
