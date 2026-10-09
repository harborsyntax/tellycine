package app.tellycine.player.multiview

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.TellycineApp
import app.tellycine.core.ContentKind
import app.tellycine.player.PlayRequest
import app.tellycine.screenshots.DemoData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Multi-view driven with the remote: opened with one channel, a second picked in the guide. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class MultiViewFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @OptIn(ExperimentalTestApi::class)
    private fun press(key: Key, times: Int = 1) = repeat(times) {
        val roots = compose.onAllNodes(isRoot())
        roots[roots.fetchSemanticsNodes().size - 1].performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun waitForText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun opensWithAChannelAndAddsAnotherFromTheGuide() {
        val app = ApplicationProvider.getApplicationContext<TellycineApp>()
        val pid = DemoData.seed(app.container)
        val first = runBlocking(Dispatchers.IO) { app.container.content.channelList(pid, null) }[0]
        val request = PlayRequest(pid, ContentKind.LIVE, first.streamId, "LIVE:${first.streamId}", first.url, first.name)
        ActivityScenario.launch<MultiViewActivity>(MultiViewActivity.intent(app, request)).use {
            // It opens with the guide, on the channel, to pick the next one. The demo playlist
            // allows two connections, so there's room for one more.
            waitForText("OK: add to multi-view")
            waitForText("Room for 1 more")
            // Down to the next channel, OK adds it and closes the guide.
            press(Key.DirectionDown)
            press(Key.DirectionCenter)
            compose.waitUntil(10_000) { compose.onAllNodesWithText("OK: add to multi-view").fetchSemanticsNodes().isEmpty() }
            assertEquals(1, compose.onAllNodesWithText("Global Report", useUnmergedTree = true).fetchSemanticsNodes().size)
            // Both connections are in use: no room to add another.
            assertEquals(0, compose.onAllNodesWithText("Add a channel", useUnmergedTree = true).fetchSemanticsNodes().size)
        }
    }
}
