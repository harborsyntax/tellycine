package app.tellycine.player.multiview

import kotlin.math.min

/** How multi-view arranges its channels. */
enum class MultiViewLayout(val label: String) {
    /** All the same size: two side by side, three or four in a 2×2 grid. */
    GRID("Grid"),
    /** The first channel big, the others small in a column beside it. */
    BIG_SIDE("Side column"),
    /** The first channel big, the others small in a row below it. */
    BIG_BOTTOM("Bottom row"),
}

/** Where one channel (or the add tile) goes, in the same units as the space given. */
data class MultiViewCell(val x: Float, val y: Float, val width: Float, val height: Float)

/** The positions of 16:9 cells for [slots] channels in a [width] × [height] space. */
fun MultiViewLayout.cells(slots: Int, width: Float, height: Float, gap: Float): List<MultiViewCell> {
    if (slots <= 0) return emptyList()
    if (slots == 1) return listOf(fit(0f, 0f, width, height, gap))
    return when (this) {
        MultiViewLayout.GRID -> grid(slots, width, height, gap)
        MultiViewLayout.BIG_SIDE -> bigSide(slots - 1, width, height, gap)
        MultiViewLayout.BIG_BOTTOM -> bigBottom(slots - 1, width, height, gap)
    }
}

/** The small channels take at most this share of the screen's width (side) or of a row's (bottom). */
private const val SMALL_SHARE = 0.28f

/** A small channel is never wider than this share of the big one. */
private const val BIG_RATIO = 0.45f

private fun grid(slots: Int, width: Float, height: Float, gap: Float): List<MultiViewCell> {
    val columns = 2
    val rows = if (slots <= 2) 1 else 2
    val w = min((width - gap * (columns + 1)) / columns, (height - gap * (rows + 1)) / rows * 16f / 9f)
    val h = w * 9f / 16f
    val left = (width - (w * columns + gap * (columns - 1))) / 2f
    val top = (height - (h * rows + gap * (rows - 1))) / 2f
    return (0 until slots).map { i ->
        MultiViewCell(left + (i % columns) * (w + gap), top + (i / columns) * (h + gap), w, h)
    }
}

/** The big channel on the left, [small] channels stacked in a column on the right. */
private fun bigSide(small: Int, width: Float, height: Float, gap: Float): List<MultiViewCell> {
    val column = min(width * SMALL_SHARE, (height - gap * (small + 1)) / small * 16f / 9f)
    val bigW = min(width - column - gap * 3, (height - gap * 2) * 16f / 9f)
    val bigH = bigW * 9f / 16f
    // In a short space the big channel is held back by the height: the others shrink with it.
    val smallW = min(column, bigW * BIG_RATIO)
    val smallH = smallW * 9f / 16f
    val left = (width - (bigW + gap + smallW)) / 2f
    val columnTop = (height - (smallH * small + gap * (small - 1))) / 2f
    return listOf(MultiViewCell(left, (height - bigH) / 2f, bigW, bigH)) +
        (0 until small).map { i -> MultiViewCell(left + bigW + gap, columnTop + i * (smallH + gap), smallW, smallH) }
}

/** The big channel on top, [small] channels side by side in a row below it. */
private fun bigBottom(small: Int, width: Float, height: Float, gap: Float): List<MultiViewCell> {
    // The row takes at most a third of the height, so the big channel stays the big one.
    val smallW = minOf(width * SMALL_SHARE, (width - gap * (small + 1)) / small, (height - gap * 3) * 0.3f * 16f / 9f)
    val smallH = smallW * 9f / 16f
    val bigH = min(height - smallH - gap * 3, (width - gap * 2) * 9f / 16f)
    val bigW = bigH * 16f / 9f
    val top = (height - (bigH + gap + smallH)) / 2f
    val rowLeft = (width - (smallW * small + gap * (small - 1))) / 2f
    return listOf(MultiViewCell((width - bigW) / 2f, top, bigW, bigH)) +
        (0 until small).map { i -> MultiViewCell(rowLeft + i * (smallW + gap), top + bigH + gap, smallW, smallH) }
}

/** One cell as big as fits, centred. */
private fun fit(x: Float, y: Float, width: Float, height: Float, gap: Float): MultiViewCell {
    val w = min(width - gap * 2, (height - gap * 2) * 16f / 9f)
    val h = w * 9f / 16f
    return MultiViewCell(x + (width - w) / 2f, y + (height - h) / 2f, w, h)
}
