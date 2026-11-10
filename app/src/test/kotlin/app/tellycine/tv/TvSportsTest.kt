package app.tellycine.tv

import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.performSemanticsAction
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
import org.junit.Assert.assertTrue
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
    fun aChannelLeftWithBackPlaysOnAboveTheHubUntilItsLeft() {
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
        // It plays on in the mini player, as in the guide.
        assertNotNull(playback.mini)
        assertTrue(compose.onAllNodes(hasText("City Sports 1") and hasClickAction()).fetchSemanticsNodes().isNotEmpty())

        // Anywhere else, it stops.
        d.tvTab("Search", "Search your playlist")
        compose.waitUntil(5_000) { playback.mini == null }
    }

    /** The filter chip saying exactly [text]: the chips come before the events they filter. */
    private fun chip(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText(text) and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test
    fun aSportsCompetitionsAndLive() {
        val d = ScreenshotDriver(compose)
        d.launchTv { pid -> DemoData.seedSports(d.app.container, pid) }
        d.tvTab("Sports", "Live now")

        // Football: all of it, or one of its competitions.
        chip("Football")
        d.waitForText("All football")
        chip("Premier League")
        d.waitForText("Newcastle v Man Utd")
        // Arsenal v Inter, on now, is in the Champions League.
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Live now")).fetchSemanticsNodes().isEmpty() }
        chip("All football")
        d.waitForText("Live now")

        // Live: what the guides say is live, in every sport.
        chip("Live")
        d.waitForText("Sinner v Zverev")
        assertTrue(compose.onAllNodes(hasText("All football")).fetchSemanticsNodes().isEmpty())
    }

    /** Holds OK on whatever has the focus, in the newest window, and lets go. */
    private fun holdOk() {
        val roots = compose.onAllNodes(isRoot())
        val root = roots[roots.fetchSemanticsNodes().size - 1]
        val t = SystemClock.uptimeMillis()
        for (e in listOf(
            KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0),
            KeyEvent(t, t + 600, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 1),
            KeyEvent(t, t + 700, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0),
        )) root.performKeyPress(androidx.compose.ui.input.key.KeyEvent(e))
        compose.waitForIdle()
    }

    @Test
    fun holdingOkOnAnEventsChannelOpensTheGuidesOptionsForIt() {
        val d = ScreenshotDriver(compose)
        d.launchTv { pid -> DemoData.seedSports(d.app.container, pid) }
        d.tvTab("Sports", "Live now")
        beATelevision()

        // Held on the live match, its panel: Multi-view, as it's on now; no Record without a DVR.
        holdOk()
        d.waitForText("Watch on City Sports 1")
        d.waitForText("Multi-view")
        assertTrue(compose.onAllNodes(hasText("Record")).fetchSemanticsNodes().isEmpty())

        // Held on a channel there, the channel's options and schedule, as in the guide.
        holdOk()
        d.waitForText("Watch live")
        d.waitForText("Open with…")

        // Back returns to the event's panel.
        d.back()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Watch live")).fetchSemanticsNodes().isEmpty() }
        d.waitForText("Watch on City Sports 1")
    }
}
