package app.tellycine.ui.search

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.data.db.ProgrammeEntity
import app.tellycine.screenshots.ScreenshotDriver
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Search finds what can still be watched again from catch-up, apart from what's to come. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SearchCatchupTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val hour = 60 * 60 * 1000L

    @Test
    fun catchUpIsWhatsBeenOnWhereTheArchiveStillHasIt() {
        val d = ScreenshotDriver(compose)
        d.launch()
        val c = d.app.container
        val playlist = runBlocking(Dispatchers.IO) { c.database.playlists().getAll().first() }
        val now = System.currentTimeMillis()

        // Metro News 24 keeps a week: what's been on is there, most recent first.
        val catchup = runBlocking { c.content.searchCatchup(playlist, "World Tonight", 40, now) }
        assertTrue(catchup.isNotEmpty())
        assertTrue(catchup.all { it.programme.stopMs <= now && it.channel.name == "Metro News 24" })
        assertEquals(catchup.sortedByDescending { it.programme.startMs }, catchup)
        // Channel Bright keeps nothing, so what's been on there can't be watched again.
        assertEquals(emptyList<Any>(), runBlocking { c.content.searchCatchup(playlist, "Sunday Bakes", 40, now) })
        // The guide's results are what's on now and still to come.
        assertTrue(runBlocking { c.content.searchProgrammes(playlist.id, "World Tonight", 40, now, fromMs = now) }.all { it.programme.stopMs > now })
    }

    @Test
    fun whatsStillToComeCantCrowdItOut() {
        val d = ScreenshotDriver(compose)
        d.launch()
        val c = d.app.container
        val pid = runBlocking(Dispatchers.IO) { c.database.playlists().getAll().first().id }
        // More "World Tonight" still to come than a search shows.
        val later = System.currentTimeMillis() + 13 * hour
        runBlocking(Dispatchers.IO) {
            c.database.content().insertProgrammes((0 until 60).map { i ->
                ProgrammeEntity(playlistId = pid, channelId = "ch0", startMs = later + i * hour, stopMs = later + (i + 1) * hour, title = "World Tonight", description = null, category = null)
            })
        }

        compose.onNodeWithContentDescription("Search").performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("World Tonight")
        // Both sections (headers, not the filters of the same names).
        fun section(title: String) = compose.onAllNodes(hasText(title) and hasNoClickAction()).fetchSemanticsNodes().isNotEmpty()
        compose.waitUntil(10_000) { section("TV guide") }
        // Below a full section of what's to come.
        compose.waitUntil(10_000) {
            // The results, not the row of filters inside them.
            runCatching { compose.onAllNodes(hasScrollToKeyAction()).onFirst().performScrollToKey("h-Catch-up") }.isSuccess && section("Catch-up")
        }

        // Catch-up on its own: only what can be watched again (the filters are back at the top).
        compose.onAllNodes(hasScrollToKeyAction()).onFirst().performScrollToKey("filters")
        compose.onAllNodes(hasText("Catch-up") and hasClickAction()).onFirst().performClick()
        compose.waitUntil(10_000) { !section("TV guide") && section("Catch-up") }
        assertTrue(compose.onAllNodes(hasContentDescription("Watch again"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasContentDescription("Watch live"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }
}
