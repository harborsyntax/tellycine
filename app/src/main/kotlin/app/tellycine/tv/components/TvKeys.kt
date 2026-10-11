package app.tellycine.tv.components

import android.view.KeyEvent
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager

/** The remote's OK button and its equivalents. */
val OkKeyCodes = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A)

/**
 * Click and long-press on OK, both on release and only for presses that started here.
 *
 * TV Material acts on the release of OK, and its long press fires while OK is still held. If that
 * long press opens a menu, the release lands on the menu's first button and presses it. Use this
 * instead of a component's own `onLongClick`.
 */
fun Modifier.okClicks(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    onPreviewKeyEvent { event ->
        val native = event.nativeKeyEvent
        if (native.keyCode !in OkKeyCodes) return@onPreviewKeyEvent false
        when {
            native.action == KeyEvent.ACTION_DOWN && native.repeatCount == 0 -> {
                pressed = true
                held = false
            }
            native.action == KeyEvent.ACTION_DOWN -> if (pressed) held = true
            native.action == KeyEvent.ACTION_UP -> {
                if (pressed) {
                    if (held && onLongClick != null) onLongClick() else onClick()
                }
                pressed = false
                held = false
            }
        }
        true
    }
}

/**
 * Ignores the release of an OK press that began before this window or panel appeared, so it
 * can't press the first button of a menu that the press itself opened.
 */
fun Modifier.ignoreStrayOkRelease(): Modifier = composed {
    var sawPress by remember { mutableStateOf(false) }
    onPreviewKeyEvent { event ->
        val native = event.nativeKeyEvent
        if (native.keyCode !in OkKeyCodes) return@onPreviewKeyEvent false
        if (native.action == KeyEvent.ACTION_DOWN) sawPress = true
        native.action == KeyEvent.ACTION_UP && !sawPress
    }
}

/**
 * How many rows a press of Up or Down ([event]) moves: one, until the key has been held for a
 * moment, then more and more the longer it's held, so long lists go by faster.
 */
fun heldKeySteps(event: KeyEvent): Int {
    if (event.repeatCount == 0) return 1
    val held = event.eventTime - event.downTime
    return when {
        held < 1_200 -> 1
        held < 2_500 -> 2
        held < 4_000 -> 4
        else -> 8
    }
}

/** [fasterWhenHeld] for a list. */
fun Modifier.fasterWhenHeld(list: LazyListState): Modifier = fasterWhenHeld(list) {
    val info = list.layoutInfo
    info.visibleItemsInfo.map { it.size }.average().toFloat() + info.mainAxisItemSpacing
}

/** [fasterWhenHeld] for a grid of rows. */
fun Modifier.fasterWhenHeld(grid: LazyGridState): Modifier = fasterWhenHeld(grid) {
    val info = grid.layoutInfo
    info.visibleItemsInfo.map { it.size.height }.average().toFloat() + info.mainAxisItemSpacing
}

/**
 * Up and Down move through this list or grid faster the longer they're held ([heldKeySteps]).
 * While moving several rows at once, focus stops at the first or last row rather than leaving
 * the list; a fresh press moves on from there.
 *
 * A list only lays out a row or two beyond its edge, and scrolls to the focused row after the
 * move, so between moves it's scrolled along by about a row ([rowSize]) straight away.
 */
private fun Modifier.fasterWhenHeld(state: ScrollableState, rowSize: () -> Float): Modifier = composed {
    val focusManager = LocalFocusManager.current
    val skipping = remember { booleanArrayOf(false) }
    onPreviewKeyEvent { event ->
        val native = event.nativeKeyEvent
        val direction = when (native.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> FocusDirection.Up
            KeyEvent.KEYCODE_DPAD_DOWN -> FocusDirection.Down
            else -> return@onPreviewKeyEvent false
        }
        if (native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
        val steps = heldKeySteps(native)
        if (steps == 1) return@onPreviewKeyEvent false
        val sign = if (direction == FocusDirection.Down) 1 else -1
        skipping[0] = true
        try {
            for (i in 0 until steps) {
                if (i > 0) state.dispatchRawDelta(sign * rowSize().takeIf { it.isFinite() }.orZero())
                if (!focusManager.moveFocus(direction)) break
            }
        } finally {
            skipping[0] = false
        }
        true
    }
        .focusProperties { onExit = { if (skipping[0]) cancelFocusChange() } }
        .focusGroup()
}

private fun Float?.orZero() = this ?: 0f
