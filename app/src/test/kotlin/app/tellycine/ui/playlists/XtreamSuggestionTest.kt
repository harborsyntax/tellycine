package app.tellycine.ui.playlists

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.screenshots.ScreenshotDriver
import app.tellycine.testing.FakeHttpServer
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Adding an M3U link whose server also has an Xtream Codes API. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class XtreamSuggestionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val panel = FakeHttpServer { path, query ->
        when (path) {
            "/player_api.php" -> if ("username=u&password=p" in query) """{"user_info":{"auth":1}}""" else """{"user_info":{"auth":0}}"""
            else -> "#EXTM3U"
        }
    }

    @After
    fun stop() = panel.close()

    @Test
    fun theEditorSuggestsTheXtreamLoginThatWorks() {
        val d = ScreenshotDriver(compose)
        d.launch()
        d.openTab("Playlists", "Add playlist")
        d.click("Add playlist")
        d.waitForText("Playlist URL")
        compose.onNode(hasText("Playlist URL") and hasSetTextAction()).performTextInput("${panel.url}/get.php?username=u&password=p&type=m3u_plus")
        // Once the server has answered.
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("this link's login works", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText("this link's login works", substring = true)[0].performClick()
        compose.waitForIdle()
        // Now an Xtream Codes login, filled in from the link.
        d.waitForText("Server")
        compose.onNode(hasText("Server") and hasText(panel.url, substring = true)).assertExists()
    }
}
