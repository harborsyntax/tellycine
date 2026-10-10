package app.tellycine.tv

import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.AppContainer
import app.tellycine.data.db.ProgrammeEntity
import app.tellycine.screenshots.ScreenshotDriver
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

private const val HOUR = 60 * 60_000L
private const val TITLE = "Election Night Special"

/** Gives the first demo channel one long programme, on from 3 hours ago until 6 hours from now. */
private fun longProgramme(container: AppContainer, playlistId: Long) = runBlocking(Dispatchers.IO) {
    val dao = container.database.content()
    val now = System.currentTimeMillis()
    dao.deleteProgrammes(playlistId)
    dao.insertProgrammes(
        listOf(ProgrammeEntity(playlistId = playlistId, channelId = "ch0", startMs = now - 3 * HOUR, stopMs = now + 6 * HOUR, title = TITLE, description = null, category = null))
    )
}

/**
 * A programme that started well before the part of the guide on screen, and ends after it, must
 * still fill its row. (Cells used to be no wider than the screen, so they stopped short.)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideLongProgrammeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun tvGuideFillsTheRowToTheEdge() {
        val d = ScreenshotDriver(compose)
        d.launchTv { longProgramme(d.app.container, it) }
        d.waitForText(TITLE)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        // The guide's rows (the categories column scrolls too, but is narrow).
        val lanes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .fetchSemanticsNodes().map { it.boundsInRoot }.maxBy { it.width }
        val channel = compose.onAllNodes(hasText("Metro News 24"), useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.boundsInRoot }.filter { it.top >= lanes.top }.minBy { it.top }
        val file = File("build/tmp/guide-long-programme.png").apply { parentFile?.mkdirs() }
        captureScreenRoboImage(file.path)
        val screen = BitmapFactory.decodeFile(file.path)
        val density = compose.onRoot().fetchSemanticsNode().layoutInfo.density.density
        val y = channel.center.y.toInt()
        // Just inside the right of the programme lanes, and the page beyond them.
        val cell = screen.getPixel((lanes.right - 30 * density).toInt(), y)
        val page = screen.getPixel((lanes.right + 20 * density).toInt(), y)
        assertTrue("The programme stops short of the edge: ${Integer.toHexString(cell)} is the page's colour", distance(cell, page) > 40)
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun phoneGuideFillsTheRowToTheEdge() {
        val d = ScreenshotDriver(compose)
        d.launch { longProgramme(d.app.container, it) }
        d.openTab("Live TV", "Metro News 24")
        compose.onNodeWithContentDescription("TV guide view").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("List view").fetchSemanticsNodes().isNotEmpty() }
        d.waitForText(TITLE)

        // The programme's cell, as far as it's on screen, reaches the right of the guide.
        val cell = compose.onNode(hasText(TITLE) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(root.right, cell.right, 2f)
    }

    private fun distance(a: Int, b: Int) =
        abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b))
}
