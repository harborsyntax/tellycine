package app.tellycine.tv.components

import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A remote key held down for [heldMs], as Android reports its repeats (about one every 50 ms). */
fun heldKey(code: Int, heldMs: Long): KeyEvent {
    val now = SystemClock.uptimeMillis()
    return KeyEvent(now - heldMs, now, KeyEvent.ACTION_DOWN, code, (heldMs / 50).toInt())
}

/** Sends [heldKey] to whatever has the focus. */
fun AndroidComposeTestRule<*, *>.hold(code: Int, heldMs: Long) {
    val roots = onAllNodes(isRoot())
    roots[roots.fetchSemanticsNodes().size - 1].performKeyPress(androidx.compose.ui.input.key.KeyEvent(heldKey(code, heldMs)))
    waitForIdle()
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class HeldKeysTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theLongerAKeyIsHeldTheFurtherEachRepeatMoves() {
        val down = KeyEvent.KEYCODE_DPAD_DOWN
        assertEquals(1, heldKeySteps(KeyEvent(KeyEvent.ACTION_DOWN, down)))
        assertEquals(1, heldKeySteps(heldKey(down, 800)))
        assertEquals(2, heldKeySteps(heldKey(down, 1_500)))
        assertEquals(4, heldKeySteps(heldKey(down, 3_000)))
        assertEquals(8, heldKeySteps(heldKey(down, 6_000)))
    }

    @Test
    fun aListMovesSeveralRowsAtATimeAndStopsAtItsEnds() {
        compose.setContent {
            Column {
                Box(Modifier.testTag("above").size(200.dp, 40.dp).focusable())
                val list = rememberLazyListState()
                LazyColumn(Modifier.height(300.dp).fasterWhenHeld(list), state = list) {
                    items(40) { i -> Box(Modifier.testTag("row$i").size(200.dp, 40.dp).focusable()) }
                }
            }
        }
        compose.onNodeWithTag("row0").requestFocus()
        val down = KeyEvent.KEYCODE_DPAD_DOWN
        val up = KeyEvent.KEYCODE_DPAD_UP

        compose.hold(down, 800)
        compose.onNodeWithTag("row1").assertIsFocused()
        compose.hold(down, 3_000)
        compose.onNodeWithTag("row5").assertIsFocused()
        compose.hold(down, 6_000)
        compose.onNodeWithTag("row13").assertIsFocused()

        // Held, it stops at the last row and at the first, rather than leaving the list.
        repeat(4) { compose.hold(down, 6_000) }
        compose.onNodeWithTag("row39").assertIsFocused()
        repeat(6) { compose.hold(up, 6_000) }
        compose.onNodeWithTag("row0").assertIsFocused()
        compose.hold(up, 6_000)
        compose.onNodeWithTag("row0").assertIsFocused()
        // A fresh press moves on, as it always has.
        compose.hold(up, 0)
        compose.onNodeWithTag("above").assertIsFocused()
    }

    @Test
    fun aGridMovesSeveralRowsAtATime() {
        compose.setContent {
            val grid = rememberLazyGridState()
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.height(300.dp).fasterWhenHeld(grid), state = grid) {
                items(60) { i -> Box(Modifier.testTag("item$i").size(100.dp, 60.dp).focusable()) }
            }
        }
        compose.onNodeWithTag("item1").requestFocus()
        // Eight rows down, in the same column.
        compose.hold(KeyEvent.KEYCODE_DPAD_DOWN, 6_000)
        compose.onNodeWithTag("item25").assertIsFocused()
    }
}
