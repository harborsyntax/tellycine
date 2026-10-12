package app.tellycine.ui.vod

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.core.ContentKind
import app.tellycine.data.settings.VodOrder
import app.tellycine.data.settings.VodSort
import app.tellycine.screenshots.ScreenshotDriver
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Whether the poster titled [first] comes before [then] in the grid: higher up, or to its left. */
private fun AndroidComposeTestRule<*, *>.comesBefore(first: String, then: String): Boolean {
    fun place(title: String) = onAllNodesWithText(title, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
    val a = place(first) ?: return false
    val b = place(then) ?: return true
    return a.top < b.top - 1 || (a.top <= b.top + 1 && a.left < b.left)
}

/** Whether the title is in view, not just composed out of it (where its bounds, clipped to the grid's, are empty). */
private fun AndroidComposeTestRule<*, *>.onScreen(title: String): Boolean =
    onAllNodesWithText(title, useUnmergedTree = true).fetchSemanticsNodes().any { it.boundsInRoot.height > 0f }

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class VodSortMenuTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun ScreenshotDriver.movieOrder() = runBlocking { app.container.settings.settings.first().vodSort(ContentKind.MOVIE) }

    /** The order is saved in the background, then the arrow turns. */
    private fun waitForDescription(description: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun theSortMenuTurnsTheOrderRound() {
        val d = ScreenshotDriver(compose)
        // As on a first run: newest additions first.
        d.launch { runBlocking(Dispatchers.IO) { d.app.container.settings.setVodSort(ContentKind.MOVIE, VodOrder.DEFAULT) } }
        d.openTab("Movies", "Midnight Harbor")
        // Velocity was added today.
        compose.waitUntil(10_000) { compose.comesBefore("Velocity", "Midnight Harbor") }
        compose.onNodeWithContentDescription("Sort: Date added, newest first").performClick()
        compose.onNodeWithContentDescription("newest first").assertExists()

        // Pressing it again turns it round, and the menu stays open with the arrow turned.
        d.click("Date added")
        waitForDescription("oldest first")
        // From the top, oldest first (not following Velocity, which was first, down the grid).
        compose.waitUntil(10_000) { compose.onScreen("Northern Lights") && compose.comesBefore("Northern Lights", "Midnight Harbor") }
        compose.waitForIdle()
        assertTrue(compose.onScreen("Northern Lights"))
        assertEquals(VodOrder(VodSort.ADDED, descending = false), d.movieOrder())

        // Another order starts in its usual direction, then turns round too.
        d.click("Title")
        compose.waitUntil(10_000) { compose.comesBefore("Double Shift", "Velocity") }
        compose.onNodeWithContentDescription("A to Z").assertExists()
        d.click("Title")
        compose.waitUntil(10_000) { compose.comesBefore("Velocity", "Double Shift") }
        compose.onNodeWithContentDescription("Sort: Title, Z to A").assertExists()
        assertEquals(VodOrder(VodSort.NAME, descending = true), d.movieOrder())
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvVodSortTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theSortButtonOffersSeriesTheirLatestEpisodeBothWaysRound() {
        val d = ScreenshotDriver(compose)
        d.launchTv()
        d.tvTab("Series", "The Long Night")
        d.tvClick("Provider order")
        d.waitForText("Latest episode")
        d.tvClick("Latest episode")
        // Office Hours had the latest episode.
        compose.waitUntil(10_000) { compose.comesBefore("Office Hours", "The Long Night") }
        compose.onNodeWithContentDescription("newest first").assertExists()
        // Pressed again in the panel, which stays open (by its arrow: the header button reads the same now).
        d.tvClick("newest first")
        compose.waitUntil(10_000) { compose.comesBefore("The Long Night", "Office Hours") }
        compose.onNodeWithContentDescription("oldest first").assertExists()
        val settings = runBlocking { d.app.container.settings.settings.first() }
        assertEquals(VodOrder(VodSort.UPDATED, descending = false), settings.vodSort(ContentKind.SERIES))
        // Movies keep their own order, and don't offer it.
        assertEquals(VodOrder(VodSort.PROVIDER), settings.vodSort(ContentKind.MOVIE))
    }
}
