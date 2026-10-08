package app.tellycine.ui.components

import app.tellycine.data.db.HistoryEntity

/** How far through the item is, or null when its length isn't known. */
fun HistoryEntity.progress(): Float? =
    if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else null

/** Resume position, or 0 when the item was (almost) finished. */
fun HistoryEntity.resumePosition(): Long =
    if (durationMs > 0 && positionMs > durationMs * 0.95) 0L else positionMs
