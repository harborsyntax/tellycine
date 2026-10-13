package app.tellycine.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.screenshots.ScreenshotDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** On a TV: a playlist's guide URLs, another added from its own row, and the tick box to combine them. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvGuideUrlsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Brings the row showing [text] into the list (on a TV, moving the focus down does this). */
    private fun reach(text: String) {
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text, substring = true))
        compose.waitForIdle()
    }

    private fun type(d: ScreenshotDriver, text: String) {
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput(text)
        d.tvClick("OK")
    }

    @Test
    fun guidesAreAddedOneRowAtATime() {
        val d = ScreenshotDriver(compose)
        d.launchTv()
        d.tvTab("Playlists", "Demo IPTV")
        d.tvClick("Edit")
        d.waitForText("Maximum connections")

        // Until there's a guide, the playlist's own is used, and there's nothing more to offer.
        reach("XMLTV guide URL")
        d.waitForText("The guide linked in the playlist")
        assertTrue(compose.onAllNodesWithText("Add another guide URL").fetchSemanticsNodes().isEmpty())

        d.tvClick("XMLTV guide URL")
        type(d, "http://a.example/guide.xml")
        reach("Add another guide URL")
        d.tvClick("Add another guide URL")
        type(d, "http://b.example/guide.xml")
        d.waitForText("XMLTV guide URL 2")
        reach("Also use the guide linked in the playlist")
        d.tvClick("Also use the guide linked in the playlist")
        d.waitForText("Ticked: it's loaded first")
        reach("Save and reload")
        d.tvClick("Save and reload")

        // Saving finishes on the main thread, so let that run between looks.
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            runBlocking(Dispatchers.IO) { d.app.container.database.playlists().getAll().any { it.epgCombine } }
        }
        val saved = runBlocking(Dispatchers.IO) { d.app.container.database.playlists().getAll().single { it.name == "Demo IPTV" } }
        assertEquals("http://a.example/guide.xml\nhttp://b.example/guide.xml", saved.epgUrl)
        assertTrue(saved.epgCombine)
    }
}
