package app.tellycine.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Documentation screenshots on a phone. Renders the real UI against a fictional demo playlist.
 * Run: ./gradlew :app:testDebugUnitTest --tests '*Screenshots*'
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class PhoneScreenshots {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun captureAll() {
        val d = ScreenshotDriver(compose)
        d.launch()

        d.openTab("Live TV", "Metro News 24")
        d.shot("live-tv")

        compose.onAllNodesWithText("City Sports 1", substring = true).onFirst().performTouchInput { longClick() }
        d.waitForText("Watch live")
        d.shot("guide-sheet")
        d.back()

        compose.onNodeWithContentDescription("Search").performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("night")
        // Guide programmes come before movies, so they fill the first screen of results.
        d.waitForText("Tonight")
        d.shot("search")
        d.back()

        d.openTab("Movies", "Midnight Harbor")
        d.shot("movies")
        d.click("The Last Signal")
        d.waitForText("Resume")
        d.shot("movie-detail")
        d.back()

        d.openTab("Series", "The Long Night")
        d.click("The Long Night")
        d.waitForText("Season 2")
        d.shot("series-detail")
        d.back()

        d.openTab("Library", "Continue watching")
        d.shot("library")
        d.setLibraryGrouping(true)
        // The first group header in "Continue watching" (lower sections are off-screen and not composed).
        d.waitForText("Sci-Fi")
        d.shot("library-grouped")
        d.setLibraryGrouping(false)

        d.click("Downloads")
        d.waitForText("used by downloads")
        d.shot("downloads")
        d.back()

        d.openTab("Playlists", "Demo IPTV")
        d.shot("playlists")

        d.click("Add playlist")
        d.waitForText("Playlist URL")
        d.shot("add-playlist")
        d.back()

        d.openTab("Playlists", "Add playlist")
        compose.onNodeWithContentDescription("Settings").performClick()
        d.waitForText("Default player")
        d.shot("settings")
        d.back()

        d.openTab("Playlists", "Add playlist")
        compose.onNodeWithContentDescription("More").performClick()
        d.click("Categories")
        d.waitForText("Documentaries")
        d.shot("category-filter")
    }

    /** The Library long-press menu, in its own run so the open menu can't affect the main sequence. */
    @Test
    fun libraryMenu() {
        val d = ScreenshotDriver(compose)
        d.launch()
        d.openTab("Library", "Favourite channels")
        // A favourite channel that isn't also in recently watched.
        compose.onAllNodesWithText("Channel Bright", useUnmergedTree = true).onFirst().performTouchInput { longClick() }
        d.waitForText("Remove from favourites")
        d.shot("library-menu")
    }

    /** The grid TV guide, which Live TV offers in landscape. */
    @Test
    @Config(qualifiers = "+land")
    fun tvGuideGrid() {
        val d = ScreenshotDriver(compose)
        d.launch()
        d.openTab("Live TV", "Metro News 24")
        compose.onNodeWithContentDescription("TV guide view").performClick()
        // The choice is saved first; the toggle flips once the grid is showing.
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("List view").fetchSemanticsNodes().isNotEmpty() }
        d.waitForText("World Tonight")
        d.shot("tv-guide-grid")
    }
}
