package app.tellycine.data.settings

import app.tellycine.core.ContentKind
import org.junit.Assert.assertEquals
import org.junit.Test

class VodOrderTest {
    @Test
    fun startsWithTheNewestAdditions() {
        assertEquals(VodOrder(VodSort.ADDED, descending = true), VodOrder.DEFAULT)
        assertEquals(VodOrder.DEFAULT, AppSettings().vodSort(ContentKind.MOVIE))
        assertEquals(VodOrder.DEFAULT, AppSettings().vodSort(ContentKind.SERIES))
    }

    @Test
    fun keepsTheLastChoiceForEachSection() {
        val title = VodOrder(VodSort.NAME, descending = true)
        val settings = AppSettings(vodSorts = mapOf(ContentKind.MOVIE to title))
        assertEquals(title, settings.vodSort(ContentKind.MOVIE))
        assertEquals(VodOrder.DEFAULT, settings.vodSort(ContentKind.SERIES))
        // Latest episode is for series only.
        assertEquals(VodOrder.DEFAULT, AppSettings(vodSorts = mapOf(ContentKind.MOVIE to VodOrder(VodSort.UPDATED))).vodSort(ContentKind.MOVIE))
    }

    @Test
    fun pressingTheCurrentOrderTurnsItRound() {
        val oldestFirst = VodOrder.DEFAULT.press(VodSort.ADDED)
        assertEquals(VodOrder(VodSort.ADDED, descending = false), oldestFirst)
        assertEquals(VodOrder.DEFAULT, oldestFirst.press(VodSort.ADDED))
        // Another starts in its usual direction: titles A–Z, dates newest first.
        assertEquals(VodOrder(VodSort.NAME, descending = false), oldestFirst.press(VodSort.NAME))
        assertEquals(VodOrder(VodSort.RELEASED, descending = true), oldestFirst.press(VodSort.RELEASED))
    }

    @Test
    fun directionsInWords() {
        assertEquals("newest first", VodOrder.DEFAULT.direction)
        assertEquals("oldest first", VodOrder(VodSort.RELEASED, descending = false).direction)
        assertEquals("A to Z", VodOrder(VodSort.NAME).direction)
        assertEquals("Z to A", VodOrder(VodSort.NAME, descending = true).direction)
        assertEquals("reversed", VodOrder(VodSort.PROVIDER, descending = true).direction)
    }
}
