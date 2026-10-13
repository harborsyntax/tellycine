package app.tellycine.player

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.screenshots.ScreenshotDriver
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** On a phone, what plays sits at the top of the app in portrait, so browsing carries on below. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class PhonePlayerTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun shown(description: String) = compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()

    /** Whether [text] is on screen with some size (the app behind a full-screen player has none). */
    private fun visible(text: String) =
        compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().any { it.boundsInRoot.height > 0f }

    @Test
    fun aChannelPlaysAtTheTopWithTheAppBelow() {
        val asked = mutableListOf<Boolean>()
        val d = ScreenshotDriver(compose)
        d.launch(onFullScreen = { asked += it })
        d.waitForText("Metro News 24")
        d.click("Metro News 24")

        compose.waitUntil(10_000) { shown("Stop playing") }
        val playback = d.app.container.phonePlayback
        assertEquals("Metro News 24", playback.engine?.request?.title)
        // Docked: the channel list and the tabs are still there to use.
        assertTrue(visible("Live TV"))
        assertTrue(visible("City Sports 1"))
        // Docked, it keeps the screen on only while it's playing.
        compose.runOnUiThread { assertEquals(playback.isPlaying, compose.activity.playerView()?.keepScreenOn) }

        // Another channel, chosen from the list below, plays in its place.
        d.click("City Sports 1")
        compose.waitUntil(10_000) { playback.engine?.request?.title == "City Sports 1" }

        // The full-screen button (in the player's own controls, a view) asks for full screen.
        compose.runOnUiThread {
            compose.activity.window.decorView.findViewById<View>(androidx.media3.ui.R.id.exo_fullscreen).performClick()
        }
        assertEquals(listOf(true), asked)

        compose.onNodeWithContentDescription("Stop playing").performClick()
        compose.waitUntil(5_000) { !shown("Stop playing") }
        assertNull(playback.engine)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w914dp-h411dp-normal-long-notround-any-land-420dpi-keyshidden-nonav")
class PhonePlayerLandscapeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sidewaysItFillsTheScreenAndBackLeavesFullScreen() {
        val asked = mutableListOf<Boolean>()
        val d = ScreenshotDriver(compose)
        d.launch(onFullScreen = { asked += it })
        d.waitForText("Metro News 24")
        d.click("Metro News 24")

        // Full screen: the player's own Back arrow, and none of the app showing.
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodesWithText("Live TV", useUnmergedTree = true).fetchSemanticsNodes().all { it.boundsInRoot.height == 0f })
        compose.runOnUiThread { assertEquals(true, compose.activity.playerView()?.keepScreenOn) }
        compose.onNodeWithContentDescription("Back").performClick()
        assertTrue(asked.isNotEmpty() && asked.none { it })
        d.app.container.phonePlayback.close()
    }
}

/** The player's own view, inside the Compose layout. */
private fun ComponentActivity.playerView(): PlayerView? = window.decorView.findPlayerView()

private fun View.findPlayerView(): PlayerView? =
    this as? PlayerView ?: (this as? ViewGroup)?.let { group -> (0 until group.childCount).firstNotNullOfOrNull { group.getChildAt(it).findPlayerView() } }
