package app.tellycine.ui.vod

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.core.ContentKind
import app.tellycine.data.settings.VodSort
import app.tellycine.screenshots.ScreenshotDriver
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class VodSortMenuTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theSortMenuReordersMovies() {
        val d = ScreenshotDriver(compose)
        d.launch()
        d.openTab("Movies", "Midnight Harbor")
        compose.onNodeWithContentDescription("Sort: Provider order").performClick()
        d.click("Date added")
        // The newest first: Velocity, added today.
        compose.waitUntil(10_000) { compose.comesBefore("Velocity", "Midnight Harbor") }
        compose.onNodeWithContentDescription("Sort: Date added").assertExists()
        assertEquals(VodSort.ADDED, runBlocking { d.app.container.settings.settings.first().vodSort(ContentKind.MOVIE) })
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvVodSortTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theSortButtonOffersSeriesTheirLatestEpisode() {
        val d = ScreenshotDriver(compose)
        d.launchTv()
        d.tvTab("Series", "The Long Night")
        d.tvClick("Provider order")
        d.waitForText("Latest episode")
        d.tvClick("Latest episode")
        // Office Hours had the latest episode.
        compose.waitUntil(10_000) { compose.comesBefore("Office Hours", "The Long Night") }
        assertEquals(VodSort.UPDATED, runBlocking { d.app.container.settings.settings.first().vodSort(ContentKind.SERIES) })
        // Movies keep their own order, and don't offer it.
        assertEquals(VodSort.PROVIDER, runBlocking { d.app.container.settings.settings.first().vodSort(ContentKind.MOVIE) })
    }
}
