package app.tellycine.screenshots

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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import app.tellycine.TellycineApp
import app.tellycine.data.settings.ThemeMode
import app.tellycine.tv.TellycineTvTheme
import app.tellycine.tv.TvApp
import app.tellycine.ui.TellycineNavHost
import app.tellycine.ui.theme.TellycineTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.Dispatchers
import org.robolectric.shadows.ShadowDialog
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
    val app: TellycineApp get() = ApplicationProvider.getApplicationContext()

    fun launch() {
        DemoData.seed(app.container)
        compose.setContent {
            TellycineTheme(themeMode = theme, dynamicColor = false) { TellycineNavHost() }
        }
        waitForText("Live TV")
    }

    /** Starts the TV interface instead of the phone one. */
    fun launchTv() {
        DemoData.seed(app.container)
        compose.setContent {
            TellycineTvTheme { TvApp() }
        }
        waitForText("Metro News 24")
    }

    /** Presses a remote-control key [times] times on whatever has focus. */
    @OptIn(ExperimentalTestApi::class)
    fun press(key: Key, times: Int = 1) {
        repeat(times) {
            // The newest window (an open dialog or panel) has the focus.
            val roots = compose.onAllNodes(isRoot())
            roots[roots.fetchSemanticsNodes().size - 1].performKeyInput { pressKey(key) }
            compose.waitForIdle()
        }
    }

    /** Presses [key] until [text] shows up, at most [limit] times. */
    fun pressUntil(key: Key, text: String, limit: Int = 20) {
        repeat(limit) {
            if (compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) return
            press(key)
        }
        waitForText(text)
    }

    /** Opens a screen from the TV side menu: Back opens the menu, then its item is chosen. */
    fun tvTab(label: String, expect: String) {
        back()
        tvClick(label)
        waitForText(expect)
    }

    /** Activates a TV control by its text. TV controls answer the remote, not touch, so this uses the click action. */
    fun tvClick(text: String) {
        waitForText(text)
        compose.onAllNodes((hasText(text, substring = true) or hasContentDescription(text, substring = true)) and hasClickAction())
            .onFirst().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
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

    /** Presses Back: on the topmost dialog or panel if one is open, otherwise on the screen. */
    fun back() {
        compose.runOnUiThread {
            val dialog = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }
            if (dialog != null) dialog.onBackPressed() else compose.activity.onBackPressedDispatcher.onBackPressed()
        }
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
