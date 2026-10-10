package app.tellycine.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkTintTest {
    private val sources = (0 until 360 step 15).flatMap { h ->
        listOf(0.2f, 0.6f, 1f).flatMap { s -> listOf(0.2f, 0.5f, 0.8f).map { l -> Color.hsl(h.toFloat(), s, l) } }
    }

    @Test
    fun darkTintsKeepTextAndButtonsReadable() {
        val text = Color(0xFFEBE1D9) // the dark themes' text
        val quiet = Color(0xFFB8C2D2) // their quietest text
        for (c in sources) {
            val t = artworkTint(c, dark = true)
            assertTrue("$c accent", contrast(t.accent, t.onAccent) >= 4.5f)
            assertTrue("$c accent on background", contrast(t.accent, t.background) >= 4.5f)
            assertTrue("$c text", contrast(text, t.background) >= 7f)
            assertTrue("$c quiet text", contrast(quiet, t.containerHigh) >= 4.5f)
        }
    }

    @Test
    fun lightTintsKeepTextAndButtonsReadable() {
        val text = Color(0xFF1F1B16) // the light theme's text
        val quiet = Color(0xFF4F4539)
        for (c in sources) {
            val t = artworkTint(c, dark = false)
            assertTrue("$c accent", contrast(t.accent, t.onAccent) >= 4.5f)
            assertTrue("$c text", contrast(text, t.background) >= 7f)
            assertTrue("$c quiet text", contrast(quiet, t.containerHigh) >= 4.5f)
        }
    }

    @Test
    fun blurSpreadsAPointAndKeepsFlatAreasFlat() {
        val w = 9
        val flat = IntArray(w * w) { 0xFF336699.toInt() }
        boxBlur(flat, w, w, 2)
        assertTrue(flat.all { it == 0xFF336699.toInt() })

        val dot = IntArray(w * w) { 0xFF000000.toInt() }
        dot[4 * w + 4] = 0xFFFFFFFF.toInt()
        boxBlur(dot, w, w, 2)
        // The white is shared among the 5x5 pixels around it: 255 / 25 each.
        assertEquals(10, dot[4 * w + 4] and 0xFF)
        assertEquals(10, dot[2 * w + 2] and 0xFF)
        assertEquals(0, dot[1 * w + 1] and 0xFF)
    }
}
