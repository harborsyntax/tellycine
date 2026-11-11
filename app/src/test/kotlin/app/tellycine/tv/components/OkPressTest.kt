package app.tellycine.tv.components

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** OK pressed, held and let go, as the remote sends it. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class OkPressTest {
    private val ok = KeyEvent.KEYCODE_DPAD_CENTER
    private fun down(repeat: Int = 0) = KeyEvent(0, repeat * 50L, KeyEvent.ACTION_DOWN, ok, repeat)
    private fun up(flags: Int = 0) = KeyEvent(0, 1_000, KeyEvent.ACTION_UP, ok, 0, 0, 0, 0, flags)

    private val log = ArrayList<String>()
    private val press = OkPress()
    private fun send(e: KeyEvent) = press.on(e, onClick = { log += "click" }, onLongClick = { log += "long" })

    @Test
    fun aPressClicksAsItsLetGo() {
        send(down())
        assertEquals(emptyList<String>(), log)
        send(up())
        assertEquals(listOf("click"), log)
    }

    @Test
    fun heldItActsStraightAwayAndOnlyOnce() {
        send(down())
        send(down(1))
        // Before OK is let go, as holding Back does.
        assertEquals(listOf("long"), log)
        send(down(2))
        send(down(3))
        send(up())
        assertEquals(listOf("long"), log)
    }

    @Test
    fun aReleaseAnotherWindowCancelledIsntAClick() {
        send(down())
        send(up(KeyEvent.FLAG_CANCELED))
        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun withoutALongPressHoldingStillClicks() {
        send(down())
        press.on(down(1), onClick = { log += "click" }, onLongClick = null)
        press.on(up(), onClick = { log += "click" }, onLongClick = null)
        assertEquals(listOf("click"), log)
    }

    @Test
    fun aReleaseWithoutAPressHereDoesNothing() {
        send(up())
        assertEquals(emptyList<String>(), log)
    }
}
