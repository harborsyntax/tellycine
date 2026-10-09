package app.tellycine.player.multiview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiViewLayoutTest {
    private val gap = 8f

    // A TV in dp, and the space above the guide while picking a channel.
    private val spaces = listOf(960f to 540f, 960f to 205f)

    @Test
    fun everyCellFitsTheSpaceIs16by9AndNoneOverlap() {
        for (layout in MultiViewLayout.entries) for ((w, h) in spaces) for (slots in 1..4) {
            val cells = layout.cells(slots, w, h, gap)
            val what = "$layout, $slots in ${w}×$h"
            assertEquals(what, slots, cells.size)
            cells.forEach { c ->
                assertTrue("$what: $c is outside", c.x >= 0 && c.y >= 0 && c.x + c.width <= w + 0.01f && c.y + c.height <= h + 0.01f)
                assertEquals("$what: $c isn't 16:9", 16f / 9f, c.width / c.height, 0.01f)
            }
            for (i in cells.indices) for (j in i + 1 until cells.size) {
                val a = cells[i]
                val b = cells[j]
                val apart = a.x + a.width <= b.x || b.x + b.width <= a.x || a.y + a.height <= b.y || b.y + b.height <= a.y
                assertTrue("$what: $a overlaps $b", apart)
            }
        }
    }

    @Test
    fun theBigLayoutsMakeTheFirstChannelBigger() {
        for (layout in listOf(MultiViewLayout.BIG_SIDE, MultiViewLayout.BIG_BOTTOM)) for ((w, h) in spaces) for (slots in 2..4) {
            val cells = layout.cells(slots, w, h, gap)
            cells.drop(1).forEach { assertTrue("$layout, $slots in ${w}×$h: the first isn't the biggest", cells[0].width > it.width * 2) }
        }
    }

    @Test
    fun theOthersGoBesideOrBelowTheBigChannel() {
        val side = MultiViewLayout.BIG_SIDE.cells(4, 960f, 540f, gap)
        side.drop(1).forEach { assertTrue(it.x >= side[0].x + side[0].width) }
        val bottom = MultiViewLayout.BIG_BOTTOM.cells(4, 960f, 540f, gap)
        bottom.drop(1).forEach { assertTrue(it.y >= bottom[0].y + bottom[0].height) }
    }
}
