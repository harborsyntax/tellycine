package app.tellycine.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.TellycineApp
import app.tellycine.player.PlayerActivity
import app.tellycine.screenshots.ScreenshotDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The TV interface driven with remote-control keys only. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvNavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun remoteMovesAroundTheGuideAndPlays() {
        val d = ScreenshotDriver(compose)
        d.launchTv()

        // Down moves to the next channel; the top of the screen names it as well as its row.
        fun shown(name: String) = compose.onAllNodesWithText(name, useUnmergedTree = true).fetchSemanticsNodes().size
        d.waitForText("Global Report")
        assertEquals(1, shown("Global Report"))
        d.press(Key.DirectionDown)
        compose.waitUntil(15_000) { shown("Global Report") == 2 }

        // Left steps back through the guide to the channel itself; OK there plays it.
        d.pressUntil(Key.DirectionLeft, "Press OK to watch")
        d.press(Key.DirectionCenter)
        val started = shadowOf(ApplicationProvider.getApplicationContext<TellycineApp>()).nextStartedActivity
        assertNotNull(started)
        assertEquals(PlayerActivity::class.java.name, started.component?.className)
        assertEquals("https://example.com/live/1.m3u8", started.getStringExtra("url"))
    }

    @Test
    fun leftFromTheChannelsOpensTheCategoriesColumnAndOkPicksOne() {
        val d = ScreenshotDriver(compose)
        d.launchTv()

        // Left steps back to the channel, then once more into the categories column, on the current one.
        d.pressUntil(Key.DirectionLeft, "Press OK to watch")
        d.press(Key.DirectionLeft)
        compose.onNode(hasText("All channels") and hasText("12") and isFocused()).assertExists()

        // OK on Sport shows its channels and returns to them: OK then plays the first.
        d.press(Key.DirectionDown, 3)
        compose.onNode(hasText("Sport") and isFocused() and !isButton).assertExists()
        d.press(Key.DirectionCenter)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Metro News 24").fetchSemanticsNodes().isEmpty() }
        d.press(Key.DirectionCenter)
        val started = shadowOf(ApplicationProvider.getApplicationContext<TellycineApp>()).nextStartedActivity
        assertNotNull(started)
        assertEquals("https://example.com/live/2.m3u8", started.getStringExtra("url"))
    }

    @Test
    fun upFromTheFirstChannelReachesTheHeaderAndBackOpensTheMenu() {
        val d = ScreenshotDriver(compose)
        d.launchTv()

        d.press(Key.DirectionUp)
        compose.onNode(hasText("All channels") and hasClickAction() and isButton).assertIsFocused()

        d.back()
        d.waitForText("Playlists")
        compose.onNode(hasText("Live TV") and hasClickAction()).assertIsFocused()
    }

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
}
