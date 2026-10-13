package app.tellycine.ui.playlists

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.screenshots.ScreenshotDriver
import app.tellycine.testing.FakeHttpServer
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Turning air dates from TVmaze on: the Playlists page shows how it's going, and series can sort by them. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class TvmazeUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // A pretend TVmaze that knows one show.
    private val tvmaze = FakeHttpServer { path, query ->
        when {
            path == "/singlesearch/shows" && "Office" in query ->
                """{"id":1,"name":"Office Hours","premiered":"2023-01-01","status":"Running","updated":1,
                   "_embedded":{"previousepisode":{"airdate":"2025-06-01"}}}"""
            else -> null
        }
    }

    @After
    fun tearDown() = tvmaze.close()

    @Test
    fun theSettingShowsProgressOnPlaylistsAndASortForSeries() {
        val d = ScreenshotDriver(compose)
        d.launch()
        d.app.container.tvmaze.client.baseUrl = tvmaze.url
        d.click("Playlists")
        d.waitForText("Demo IPTV")
        compose.onNodeWithContentDescription("Settings").performClick()
        d.click(TvmazeText.TITLE)
        // The write finishes on the main thread, so let that run between looks.
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            runBlocking(Dispatchers.IO) { d.app.container.settings.settings.first().tvmazeAirDates }
        }
        d.back()

        // Below the playlists: one of the demo's six series is on (the pretend) TVmaze.
        d.waitForText(TvmazeText.TITLE)
        d.waitForText("1 of 6 series found")
        d.waitForText("5 not on TVmaze")

        d.openTab("Series", "The Long Night")
        compose.onNodeWithContentDescription("Sort: Provider order, as listed").performClick()
        d.waitForText("Last aired")
        assertTrue(compose.onAllNodesWithText("Last aired").fetchSemanticsNodes().isNotEmpty())
    }
}
