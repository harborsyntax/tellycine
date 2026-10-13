package app.tellycine.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.screenshots.ScreenshotDriver
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Small windows, such as a small floating window or the narrowest split-screen pane, leave the
 * app's lists little room; every screen still has to lay out. Material's ListItem threw in them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NarrowWindowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Sideways, so the tabs are a rail down the side, leaving the lists about 160 dp. */
    @Test
    @Config(qualifiers = "w240dp-h135dp-land")
    fun aSmallLandscapeWindow() = visitEveryScreen()

    /** The smallest a split-screen pane can be. */
    @Test
    @Config(qualifiers = "w220dp-h220dp")
    fun theNarrowestSplitScreenPane() = visitEveryScreen()

    private fun visitEveryScreen() {
        val d = ScreenshotDriver(compose)
        d.launch(ready = null)
        d.waitForText("Metro News 24")
        for (tab in listOf("Movies", "Series", "Library", "Playlists")) {
            press(hasText(tab))
        }
        press(hasContentDescription("Settings"))
        d.waitForText("Theme")
        d.back()
        press(hasText("Live TV"))
        press(hasContentDescription("Search"))
    }

    /** Clicks the first control matching [matcher], wherever it is in the window, and lets the screen settle. */
    private fun press(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        compose.waitUntil(10_000) { compose.onAllNodes(matcher and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(matcher and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        repeat(3) {
            Thread.sleep(100)
            compose.waitForIdle()
        }
    }
}
