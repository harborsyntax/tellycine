package app.tellycine.tv.components

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.onPreviewKeyEvent

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
