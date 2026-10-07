package app.mosaic.screenshots

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

typealias ComposeRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

/** Drives the real app UI through screens and saves PNGs to docs/images/screens/. */
@OptIn(ExperimentalRoborazziApi::class)
class ScreenshotDriver(private val compose: ComposeRule) {
    private var theme by mutableStateOf(ThemeMode.LIGHT)
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
        captureScreenRoboImage("../docs/images/screens/$name.png")
    }
}
