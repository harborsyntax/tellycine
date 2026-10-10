package app.tellycine.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Documentation screenshots of the Android TV interface, driven with remote-control keys. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvScreenshots {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun captureAll() {
        val d = ScreenshotDriver(compose)
        d.launchTv()

        // The guide, moved about with the remote.
        d.press(Key.DirectionDown, 2)
        d.press(Key.DirectionRight)
        d.shot("tv-guide")

        // Back opens the side menu.
        d.back()
        d.waitForText("Playlists")
        d.shot("tv-menu")
        d.press(Key.DirectionRight)

        // Holding OK (or Menu) on a channel: options and schedule.
        d.press(Key.Menu)
        d.waitForText("Watch live")
        d.shot("tv-channel-options")
        d.back()

        // Left from the channel column opens the categories column.
        d.pressUntil(Key.DirectionLeft, "Press OK to watch")
        d.press(Key.DirectionLeft)
        d.shot("tv-categories")
        d.back()

        d.tvClick("List")
        // The list's header names the playlist.
        d.waitForText("Demo IPTV")
        d.shot("tv-channel-list")
        d.tvClick("Guide")

        d.tvTab("Movies", "Midnight Harbor")
        d.shot("tv-movies")
        d.tvClick("The Last Signal")
        d.waitForText("Resume")
        d.shot("tv-movie-details")
        d.back()

        d.tvTab("Series", "The Long Night")
        d.tvClick("The Long Night")
        d.waitForText("Season 2")
        d.shot("tv-series-details")
        d.back()

        d.tvTab("Library", "Continue watching")
        d.shot("tv-library")

        d.tvTab("Search", "Search your playlist")
        d.press(Key.DirectionCenter)
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("night")
        d.tvClick("OK")
        d.waitForText("Tonight")
        d.shot("tv-search")

        d.tvTab("Playlists", "Demo IPTV")
        d.shot("tv-playlists")
        d.tvClick("Edit")
        d.waitForText("Maximum connections")
        d.shot("tv-edit-playlist")
        d.back()

        d.tvTab("Settings", "Default player")
        d.shot("tv-settings")
    }
}
