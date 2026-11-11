package app.tellycine.ui.sports

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.AppContainer
import app.tellycine.core.ContentKind
import app.tellycine.core.sports.AiringKind
import app.tellycine.core.sports.Sport
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import app.tellycine.data.db.ProgrammeEntity
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.sports.SportEvent
import app.tellycine.screenshots.DemoData
import app.tellycine.screenshots.ScreenshotDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers

/** The Sports Hub on a phone: finding sport in the guides, gathering it into events, and the tab. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SportsHubTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun events(container: AppContainer, replays: Boolean = false, teams: List<String> = listOf("Arsenal")): List<SportEvent> = runBlocking(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        container.sports.events(now - 8 * HOUR, now + 4 * DAY, replays, eventChannels = true, teams = teams).first()
    }

    @Test
    fun sportInTheGuideAndChannelNamesBecomesEvents() {
        val d = ScreenshotDriver(compose)
        d.launch { pid -> DemoData.seedSports(d.app.container, pid) }
        val container = d.app.container
        val all = events(container)

        // The match on now, on its channel and on an event channel.
        val match = all.single { it.title == "Arsenal v Inter" }
        assertEquals(Sport.FOOTBALL, match.sport)
        assertEquals("Champions League", match.competition)
        assertTrue(match.isLive(System.currentTimeMillis()))
        assertEquals(listOf("City Sports 1", "UCL 04: Arsenal v Inter"), match.sources.map { it.stream.name })
        assertEquals(listOf("Arsenal"), match.followed)

        // Magazines never; highlights only when asked for.
        assertTrue(all.none { it.title.contains("Football Focus") || it.title.contains("Goals") })
        val withReplays = events(container, replays = true)
        assertEquals(AiringKind.HIGHLIGHTS, withReplays.single { it.title.contains("Goals") }.kind)

        // Every sport the demo has.
        assertEquals(
            setOf(Sport.FOOTBALL, Sport.DARTS, Sport.TENNIS, Sport.F1, Sport.BOXING, Sport.SNOOKER, Sport.GOLF),
            all.map { it.sport }.toSet(),
        )
        assertEquals("Qualifying", all.first { it.sport == Sport.F1 }.session)
    }

    @Test
    fun theSameMatchInTwoPlaylistsIsOneEvent() {
        val d = ScreenshotDriver(compose)
        d.launch { pid -> DemoData.seedSports(d.app.container, pid) }
        val container = d.app.container
        val now = System.currentTimeMillis() / (15 * MINUTE) * (15 * MINUTE)
        val second = runBlocking(Dispatchers.IO) {
            val db = container.database
            val id = db.playlists().insert(PlaylistEntity(name = "Second IPTV", type = PlaylistType.M3U_URL, url = "https://example.com/2.m3u", lastSyncedAt = now))
            db.content().insertStreams(listOf(StreamEntity(id, ContentKind.LIVE, "s1", "UK: Sports Central", 0, null, null, "https://example.com/2/s1.ts", epgChannelId = "central")))
            // Its build-up starts a quarter of an hour before the other's coverage.
            db.content().insertProgrammes(listOf(ProgrammeEntity(playlistId = id, channelId = "central", startMs = now - 62 * MINUTE, stopMs = now + 75 * MINUTE, title = "Champions League Football: Arsenal v Inter", description = null, category = "Sports")))
            container.sportsIndexer.index(id)
            id
        }
        val match = events(container).single { it.home?.name == "Arsenal" && it.sport == Sport.FOOTBALL && it.isLive(System.currentTimeMillis()) }
        assertEquals(setOf("City Sports 1", "UCL 04: Arsenal v Inter", "UK: Sports Central"), match.sources.map { it.stream.name }.toSet())
        assertTrue(match.sources.any { it.playlist.id == second })
    }

    @Test
    fun theTabPlaysAndFollows() {
        val d = ScreenshotDriver(compose)
        d.launch { pid -> DemoData.seedSports(d.app.container, pid, follow = emptyList()) }
        d.openTab("Sports", "Live now")

        // A live match opens its sheet; a channel there plays it.
        compose.onAllNodesWithText("Inter", substring = true).onFirst().performClick()
        d.waitForText("Watch on")
        // Low in the sheet, below the channels: on screen first, or the tap lands elsewhere.
        compose.onAllNodesWithText("Follow Inter", useUnmergedTree = true).onFirst().performScrollTo().performClick()
        compose.waitForIdle()
        // DataStore makes the change on the thread that asked for it, the main thread, which is this
        // test's: it's let run while waiting (as the other tests' waits do), or it waits forever.
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            runBlocking { d.app.container.settings.current().sportsTeams } == listOf("Inter")
        }
        d.click("City Sports 1")
        assertEquals("https://example.com/live/2.m3u8", d.playing().url)
    }

    @Test
    fun liveAndASportsCompetitions() {
        val d = ScreenshotDriver(compose)
        d.launch { pid -> DemoData.seedSports(d.app.container, pid, follow = emptyList()) }
        d.openTab("Sports", "Live now")
        fun chip(text: String) {
            compose.waitUntil(5_000) { compose.onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(hasText(text) and hasClickAction()).onFirst().performClick()
            compose.waitForIdle()
        }

        // Football: all of it, or one of its competitions.
        chip("Football")
        d.waitForText("All football")
        chip("Premier League")
        d.waitForText("Newcastle v Man Utd")
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Live now")).fetchSemanticsNodes().isEmpty() }
        chip("Champions League")
        d.waitForText("Live now")

        // Live: what the guides say is live, in every sport; no competitions to choose.
        chip("Live")
        d.waitForText("Sinner v Zverev")
        assertTrue(compose.onAllNodes(hasText("All football")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun turningTheHubOffHidesItsTabAndForgetsTheSport() {
        val d = ScreenshotDriver(compose)
        d.launch { pid -> DemoData.seedSports(d.app.container, pid) }
        val container = d.app.container
        val dao = container.database.sports()
        fun count() = runBlocking(Dispatchers.IO) { dao.observeCount().first() }
        d.waitForText("Sports")
        assertTrue(count() > 0)
        try {
            runBlocking { container.settings.setSportsHub(false) }
            compose.waitUntil(5_000) { compose.onAllNodes(hasText("Sports")).fetchSemanticsNodes().isEmpty() }
            compose.waitUntil(5_000) { count() == 0 }
        } finally {
            runBlocking { container.settings.setSportsHub(true) }
        }
        // On again, it finds the sport again.
        compose.waitUntil(10_000) { count() > 0 }
        d.waitForText("Sports")
    }

    @Test
    fun aDeletedPlaylistTakesItsSportWithIt() {
        val d = ScreenshotDriver(compose)
        var playlist = 0L
        d.launch { pid -> playlist = pid; DemoData.seedSports(d.app.container, pid) }
        val container = d.app.container
        runBlocking { container.playlists.delete(playlist) }
        assertEquals(0, runBlocking(Dispatchers.IO) { container.database.sports().observeCount().first() })
        assertNull(runBlocking(Dispatchers.IO) { container.database.playlists().get(playlist) })
        assertFalse(events(container).isNotEmpty())
        assertNotNull(container.sports)
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
