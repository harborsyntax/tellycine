package app.mosaic.screenshots

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import app.mosaic.MosaicApp
import app.mosaic.data.settings.ThemeMode
import app.mosaic.ui.MosaicNavHost
import app.mosaic.ui.theme.MosaicTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream

typealias ComposeRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

/**
 * Drives the real app UI through screens and saves PNGs to docs/images/screens/, in the dark
 * theme with rounded corners like a device screen.
 */
@OptIn(ExperimentalRoborazziApi::class)
class ScreenshotDriver(private val compose: ComposeRule) {
    private var theme by mutableStateOf(ThemeMode.DARK)
    val app: MosaicApp get() = ApplicationProvider.getApplicationContext()

    fun launch() {
        DemoData.seed(app.container)
        compose.setContent {
            MosaicTheme(themeMode = theme, dynamicColor = false) { MosaicNavHost() }
        }
        waitForText("Live TV")
    }

    fun applyTheme(mode: ThemeMode) {
        theme = mode
        compose.waitForIdle()
    }

    fun setLibraryGrouping(enabled: Boolean) {
        runBlocking(Dispatchers.IO) { app.container.settings.setLibraryGroupByCategory(enabled) }
        compose.waitForIdle()
    }

    fun waitForText(text: String, timeoutMs: Long = 15_000) {
        try {
            compose.waitUntil(timeoutMs) {
                // Unmerged: some components (e.g. the extended FAB) don't expose their label when merged.
                compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            // Include what is on screen, so CI logs show why the text never appeared.
            val tree = runCatching { compose.onRoot().printToString(maxDepth = 30) }.getOrElse { "<no tree: $it>" }
            throw AssertionError("Timed out waiting for \"$text\". Screen:\n$tree", e)
        }
        compose.waitForIdle()
    }

    fun click(text: String) {
        waitForText(text)
        compose.onAllNodesWithText(text, useUnmergedTree = true).onFirst().performClick()
        compose.waitForIdle()
    }

    fun openTab(label: String, expect: String) {
        click(label)
        waitForText(expect)
    }

    fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    fun shot(name: String) {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val file = File("../docs/images/screens/$name.png")
        captureScreenRoboImage(file.path)
        roundCorners(file)
    }

    /** Makes the corners transparent, with a radius proportional to the shorter side. */
    private fun roundCorners(file: File) {
        val source = BitmapFactory.decodeFile(file.path)
        val w = source.width.toFloat()
        val h = source.height.toFloat()
        val radius = minOf(w, h) * CORNER_FRACTION
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawRoundRect(RectF(0f, 0f, w, h), radius, radius, paint)
        // Keep the screenshot only where the rounded shape was drawn (anti-aliased edges included).
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(source, 0f, 0f, paint)
        FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val CORNER_FRACTION = 0.06f
    }
}
