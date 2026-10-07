package app.mosaic.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mosaic.data.settings.ThemeMode
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

        d.openTab("Live TV", "Mosaic News 24")
        d.shot("live-tv")

        compose.onAllNodesWithText("City Sports 1", substring = true).onFirst().performTouchInput { longClick() }
        d.waitForText("Watch live")
        d.shot("guide-sheet")
        d.back()

        d.applyTheme(ThemeMode.DARK)
        d.shot("live-tv-dark")
        d.applyTheme(ThemeMode.LIGHT)

        compose.onNodeWithContentDescription("Search").performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("night")
        d.waitForText("Midnight Harbor")
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
        d.waitForText("Documentaries")
        d.shot("library-grouped")
        d.setLibraryGrouping(false)

        d.openTab("Playlists", "Demo IPTV")
        d.shot("playlists")

        compose.onNodeWithContentDescription("More").performClick()
        d.click("Categories")
        d.waitForText("Documentaries")
        d.shot("category-filter")
        d.back()

        d.click("Add playlist")
        d.waitForText("Playlist URL")
        d.shot("add-playlist")
        d.back()

        compose.onNodeWithContentDescription("Settings").performClick()
        d.waitForText("Default player")
        d.shot("settings")
    }
}
