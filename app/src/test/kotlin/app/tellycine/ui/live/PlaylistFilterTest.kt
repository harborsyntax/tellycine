package app.tellycine.ui.live

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.data.repo.SecondPlaylist
import app.tellycine.screenshots.ScreenshotDriver
import app.tellycine.screenshots.beATelevision
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class PlaylistFilterTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun liveTvAndMoviesEachChooseTheirPlaylists() {
        val d = ScreenshotDriver(compose)
        var second = 0L
        d.launch { second = SecondPlaylist.seed(d.app.container) }
        d.openTab("Live TV", "Metro News 24")

        // Both playlists together, in a category both have: each channel says where it's from.
        compose.onNodeWithContentDescription("Playlists: Demo IPTV").performClick()
        d.click("All playlists")
        // From the full list of categories, as the row of them scrolls off the screen.
        compose.onNodeWithContentDescription("All categories").performClick()
        clickWhenShown("News")
        d.waitForText("Second News HD")
        assertTrue(compose.onAllNodesWithText(SecondPlaylist.NAME, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("Demo IPTV", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())

        // It plays from its own playlist, and changing channel stays in its own category there.
        d.click("Second News HD")
        val request = d.playing()
        assertEquals(second, request.playlistId)
        assertEquals("7", request.categoryId)

        // Movies has its own choice: still the active playlist until changed.
        d.openTab("Movies", "Midnight Harbor")
        compose.onNodeWithContentDescription("Playlists: Demo IPTV").performClick()
        d.click("All playlists")
        compose.onNodeWithContentDescription("All categories").performClick()
        clickWhenShown("Drama")
        d.waitForText("Quiet Tide")
        d.click("Quiet Tide")
        // The title's page says which playlist it's from.
        d.waitForText(SecondPlaylist.NAME)
    }

    /** Clicks the control showing exactly [text], once the change of playlists has brought it. */
    private fun clickWhenShown(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText(text, useUnmergedTree = true).onFirst().performClick()
        compose.waitForIdle()
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvPlaylistFilterTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val selected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)

    @Test
    fun theGuidesCategoriesColumnStartsWithThePlaylists() {
        val d = ScreenshotDriver(compose)
        var second = 0L
        d.launchTv { second = SecondPlaylist.seed(d.app.container) }

        // Left into the column, on All channels; the playlists are above the categories.
        d.pressUntil(Key.DirectionLeft, "Press OK to watch")
        d.press(Key.DirectionLeft)
        compose.onNode(hasText("All channels") and isFocused()).assertExists()
        compose.onNode(hasText("Demo IPTV") and selected).assertExists()
        // Past the Sort button at the top of the categories.
        d.press(Key.DirectionUp, 4)
        compose.onNode(hasText("All playlists") and isFocused()).assertExists()
        d.press(Key.DirectionCenter)

        // Both playlists' news channels, merged into one News category.
        d.pressUntil(Key.DirectionLeft, "Press OK to watch")
        d.press(Key.DirectionLeft)
        compose.onNode(hasText("All playlists") and selected).assertExists()
        d.press(Key.DirectionDown, 2)
        compose.onNode(hasText("News") and isFocused()).assertExists()
        d.press(Key.DirectionCenter)
        d.waitForText("Second News HD")

        // The details above the guide say which playlist the highlighted channel is from.
        d.press(Key.DirectionDown, 2)
        d.waitForText("Second Headlines")
        assertTrue(compose.onAllNodesWithText(SecondPlaylist.NAME, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        beATelevision()
        d.press(Key.DirectionCenter)
        val request = d.playing()
        assertEquals(second, request.playlistId)
        assertEquals("7", request.categoryId)
    }
}
