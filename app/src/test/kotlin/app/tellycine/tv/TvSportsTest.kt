package app.tellycine.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.TellycineApp
import app.tellycine.core.ContentKind
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import app.tellycine.data.db.StreamEntity
import app.tellycine.screenshots.DemoData
import app.tellycine.screenshots.ScreenshotDriver
import app.tellycine.screenshots.beATelevision
import app.tellycine.ui.live.liveRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The Sports Hub on TV, with the remote. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvSportsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun okOnALiveMatchPlaysItAndBackGoesToTheFiltersThenTheMenu() {
        val d = ScreenshotDriver(compose)
        d.launchTv { pid -> DemoData.seedSports(d.app.container, pid) }
        d.tvTab("Sports", "Live now")

        // The first live match has the focus: OK plays its channel.
        beATelevision()
        d.press(Key.DirectionCenter)
        val started = shadowOf(ApplicationProvider.getApplicationContext<TellycineApp>()).nextStartedActivity
        assertNotNull(started)
        assertEquals("https://example.com/live/2.m3u8", started.getStringExtra("url"))

        // Back goes up to the filters, on the one chosen; again, to the menu, on Sports.
        d.back()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("For you") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        d.back()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Sports") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aChannelLeftWithBackStopsUnlessTheGuideIsShowing() {
        val d = ScreenshotDriver(compose)
        d.launchTv { pid -> DemoData.seedSports(d.app.container, pid) }
        d.tvTab("Sports", "Live now")
        val playback = d.app.container.tvPlayback
        val stream = StreamEntity(1, ContentKind.LIVE, "ch2", "City Sports 1", 0, null, null, "https://example.com/live/2.m3u8")
        val playlist = PlaylistEntity(id = 1, name = "Demo IPTV", type = PlaylistType.M3U_URL, url = "")
        // As the player does when Back leaves a live channel.
        compose.runOnUiThread {
            val owner = Any()
            playback.claim(owner)
            playback.toMini(owner, liveRequest(playlist, stream))
        }
        compose.waitForIdle()
        compose.waitUntil(5_000) { playback.mini == null }
    }
}
