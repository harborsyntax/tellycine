package app.tellycine.ui.search

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.AppContainer
import app.tellycine.TellycineApp
import app.tellycine.core.ContentKind
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import app.tellycine.data.db.StreamEntity
import app.tellycine.player.PlayRequest
import app.tellycine.screenshots.ScreenshotDriver
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A second playlist beside the demo one, with a channel and a movie that "metro" finds. */
private fun seedSecondPlaylist(container: AppContainer): Long = runBlocking(Dispatchers.IO) {
    val db = container.database
    val pid = db.playlists().insert(
        PlaylistEntity(name = "Second IPTV", type = PlaylistType.M3U_URL, url = "https://example.com/second.m3u", lastSyncedAt = System.currentTimeMillis())
    )
    db.content().insertStreams(
        listOf(
            StreamEntity(pid, ContentKind.LIVE, "s1", "Metro Sport", 0, null, null, "https://example.com/second/1.m3u8"),
            StreamEntity(pid, ContentKind.MOVIE, "m1", "Metro Nights", 0, null, null, "https://example.com/second/m1.mp4", year = "2021"),
        )
    )
    pid
}

private fun playlistOfStartedPlayer(): Long {
    val started = shadowOf(ApplicationProvider.getApplicationContext<TellycineApp>()).nextStartedActivity
    return PlayRequest.fromIntent(started)!!.playlistId
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SearchAllPlaylistsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun searchFindsEveryPlaylistLabelsThemAndPlaysFromTheRightOne() {
        val d = ScreenshotDriver(compose)
        d.launch()
        val second = seedSecondPlaylist(d.app.container)

        compose.onNodeWithContentDescription("Search").performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("metro")
        // The demo playlist's channel and the second playlist's channel and movie.
        d.waitForText("Metro Sport")
        d.waitForText("Metro News 24")
        d.waitForText("Metro Nights")
        // Each labelled with its playlist.
        assertTrue(compose.onAllNodesWithText("Second IPTV", useUnmergedTree = true).fetchSemanticsNodes().size >= 2)
        assertTrue(compose.onAllNodesWithText("Demo IPTV", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())

        d.click("Metro Sport")
        assertEquals(second, playlistOfStartedPlayer())
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvSearchAllPlaylistsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tvSearchFindsEveryPlaylistLabelsThemAndPlaysFromTheRightOne() {
        val d = ScreenshotDriver(compose)
        d.launchTv()
        val second = seedSecondPlaylist(d.app.container)

        d.tvTab("Search", "Search your playlists")
        d.press(Key.DirectionCenter)
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("metro")
        d.tvClick("OK")
        d.waitForText("Metro Sport")
        d.waitForText("Second IPTV")

        d.tvClick("Metro Sport")
        assertEquals(second, playlistOfStartedPlayer())
    }
}
