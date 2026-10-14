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
import app.tellycine.core.ContentKind
import app.tellycine.screenshots.ComposeRule
import app.tellycine.screenshots.ScreenshotDriver
import app.tellycine.ui.live.liveRequest
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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

    private fun visible(text: String) = compose.visible(text)

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
        // Docking by itself asks nothing of the screen.
        assertEquals(emptyList<Boolean>(), asked)
        // Docked, it keeps the screen on only while it's playing.
        compose.runOnUiThread { assertEquals(playback.isPlaying, compose.activity.playerView()?.keepScreenOn) }

        // Another channel, chosen from the list below, plays in its place.
        d.click("City Sports 1")
        compose.waitUntil(10_000) { playback.engine?.request?.title == "City Sports 1" }

        // The full-screen button (in the player's own controls, a view) asks for full screen. Here
        // the screen doesn't turn, so it's still docked and the button asks again.
        repeat(2) {
            compose.runOnUiThread { compose.activity.playerView()!!.findViewById<View>(androidx.media3.ui.R.id.exo_fullscreen).performClick() }
            compose.waitForIdle()
        }
        assertEquals(listOf(true, true), asked)

        // Into picture in picture: the player fills the window, and nothing more is asked.
        d.inPip = true
        compose.waitForIdle()
        assertTrue(!visible("City Sports 1"))
        d.inPip = false
        compose.waitForIdle()
        assertTrue(visible("City Sports 1"))
        assertEquals(listOf(true, true), asked)

        // The controls hide after a few seconds; a tap on the player brings them back.
        compose.runOnUiThread { compose.activity.playerView()!!.showController() }
        compose.waitUntil(5_000) { shown("Stop playing") }
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
        assertTrue(!compose.visible("Live TV"))
        compose.runOnUiThread { assertEquals(true, compose.activity.playerView()?.keepScreenOn) }
        // Turning sideways is what made it full screen, so it asks nothing more of the screen.
        assertEquals(emptyList<Boolean>(), asked)
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf(false), asked)
        d.app.container.phonePlayback.close()
    }

    @Test
    fun theAppBehindTheFullScreenPlayerCanBeOnAnyTab() {
        val d = ScreenshotDriver(compose)
        d.launch()
        val metro = d.liveRequest("Metro News 24")
        for (tab in listOf("Movies", "Series", "Library", "Live TV")) {
            d.click(tab)
            compose.waitForIdle()
            d.app.container.phonePlayback.play(metro)
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty() }
            d.app.container.phonePlayback.close()
            compose.waitForIdle()
        }
    }
}

/** In a picture-in-picture window's size: the player fills it, with the app (out of sight) behind. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w240dp-h135dp-land")
class PhonePlayerPipWindowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun playsInATinyWindowAndAsksNothing() {
        val asked = mutableListOf<Boolean>()
        val d = ScreenshotDriver(compose)
        val playback = d.app.container.phonePlayback
        // Already playing in the window when the app is drawn: the app behind isn't laid out.
        d.inPip = true
        d.launch(onFullScreen = { asked += it }, ready = null) { playback.play(d.liveRequest("Metro News 24")) }
        compose.waitUntil(10_000) { playback.engine?.request != null }
        compose.waitForIdle()
        compose.runOnUiThread { assertEquals(false, compose.activity.playerView()?.useController) }
        assertEquals(emptyList<Boolean>(), asked)
        playback.close()
    }
}

/** The player's own view, inside the Compose layout. */
private fun ComponentActivity.playerView(): PlayerView? = window.decorView.findPlayerView()

private fun View.findPlayerView(): PlayerView? =
    this as? PlayerView ?: (this as? ViewGroup)?.let { group -> (0 until group.childCount).firstNotNullOfOrNull { group.getChildAt(it).findPlayerView() } }

/** A request for the demo channel called [name], as the Live TV list makes one. */
private fun ScreenshotDriver.liveRequest(name: String): PlayRequest = runBlocking(Dispatchers.IO) {
    val db = app.container.database
    val stream = db.playlists().getAll().firstNotNullOf { playlist ->
        db.content().search(playlist.id, ContentKind.LIVE, name, 5).firstOrNull { it.name == name }
    }
    liveRequest(null, stream, categoryId = stream.categoryId)
}

/** Whether [text] is on screen: laid out, with some size (the app behind a full-screen player isn't laid out). */
private fun ComposeRule.visible(text: String) =
    onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().any { it.layoutInfo.isPlaced && it.boundsInRoot.height > 0f }
