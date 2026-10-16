package app.tellycine.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.screenshots.ScreenshotDriver
import app.tellycine.testing.FakeHttpServer
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Turning details from TMDB on: it needs a key of the user's own, which TMDB is asked about before
 * it's kept; then the Playlists page shows how the lookups are going, and there are more sorts.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class TmdbSettingsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var d: ScreenshotDriver

    // A pretend TMDB that accepts one key and knows one of the demo's series.
    private val tmdb = FakeHttpServer { path, query ->
        when {
            "api_key=$KEY" !in query -> throw FakeHttpServer.Status(401)
            path == "/authentication" -> """{"success":true}"""
            path == "/search/tv" && "query=Office" in query ->
                """{"results":[{"id":1,"name":"Office Hours","first_air_date":"2023-01-01","vote_average":7.2,"vote_count":50}]}"""
            path == "/tv/1" -> """{"id":1,"name":"Office Hours","first_air_date":"2023-01-01","status":"Returning Series","vote_average":7.2,
                "vote_count":50,"last_episode_to_air":{"season_number":2,"episode_number":3,"air_date":"2025-06-01"}}"""
            path.startsWith("/search/") -> """{"results":[]}"""
            else -> null
        }
    }

    @After
    fun tearDown() {
        // Settings outlive a test.
        runBlocking { d.app.container.settings.setTmdbApiKey("") }
        tmdb.close()
    }

    private fun settings() = runBlocking(Dispatchers.IO) { d.app.container.settings.current() }

    @Test
    fun aKeyIsCheckedBeforeItTurnsDetailsOn() {
        d = ScreenshotDriver(compose)
        d.launch()
        d.app.container.tmdb.client.baseUrl = tmdb.url
        d.click("Playlists")
        d.waitForText("Demo IPTV")
        compose.onNodeWithContentDescription("Settings").performClick()
        // TMDB's credit, as its terms ask.
        d.waitForText(TmdbText.NOTICE)
        d.waitForText(TmdbText.SITE_NAME)
        // And FFmpeg's, as its licence asks.
        d.waitForText(FfmpegText.NOTICE)
        d.waitForText(TmdbText.keySummary("", builtIn = false))

        // Without a key (tests build without Tellycine's own), turning it on asks for one first.
        compose.onNodeWithText(TmdbText.TITLE).performScrollTo().performClick()
        d.waitForText(TmdbText.keyHelp(builtIn = false))
        compose.onNode(hasSetTextAction()).performTextInput("not-my-key")
        d.click("Save")
        d.waitForText(TmdbText.KEY_REJECTED)
        assertEquals(false, settings().tmdb)

        compose.onNode(hasSetTextAction()).performTextReplacement("  $KEY ")
        d.click("Save")
        // The write finishes on the main thread, so let that run between looks.
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            settings().tmdb
        }
        assertEquals(KEY, settings().tmdbApiKey)
        d.waitForText(TmdbText.keySummary(KEY, builtIn = false))
        d.back()

        // Below the playlists: one of the demo's 18 movies and series is on (the pretend) TMDB.
        d.waitForText(TmdbText.TITLE)
        d.waitForText("1 of 18 movies and series found")
        d.waitForText("17 not on TMDB")

        d.openTab("Series", "The Long Night")
        compose.onNodeWithContentDescription("Sort: Provider order, as listed").performClick()
        d.waitForText("Last aired")
        d.waitForText("Rating")
    }

    private companion object {
        const val KEY = "0123456789abcdef0123456789abcdef"
    }
}
