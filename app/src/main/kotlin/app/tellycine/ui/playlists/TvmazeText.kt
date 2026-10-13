package app.tellycine.ui.playlists

import app.tellycine.data.tvmaze.TvmazeUpdater
import app.tellycine.ui.components.formatCount
import app.tellycine.ui.components.formatRelative

/** The words for air dates from TVmaze, in Settings and on the Playlists page (phone and TV). */
object TvmazeText {
    const val TITLE = "Air dates from TVmaze"
    const val SETTING = "Look your series up on TVmaze.com, to sort them by when their latest episode aired. " +
        "Sends series names to TVmaze, nothing else. The first look-up takes a while."
    const val CREDIT = "Air dates from TVmaze.com (CC BY-SA)"
}

/** What a check is doing, or what it found: "Looking up series: 120 of 3,000". */
fun TvmazeUpdater.Status.headline(): String = when {
    running && todo > 0 -> "Looking up series: ${formatCount(done)} of ${formatCount(todo)}"
    running -> "Checking TVmaze for changes…"
    series == 0 -> "No series to look up yet"
    else -> "${formatCount(found)} of ${formatCount(series)} series found"
}

/** More about it: why it stopped, what's still to look up, when it last checked, and what wasn't found. */
fun TvmazeUpdater.Status.details(): String = buildList {
    error?.let(::add)
    if (!running) {
        val waiting = series - found - notFound
        if (waiting > 0) add("${formatCount(waiting)} still to look up")
        checkedAt?.let { add("Checked ${formatRelative(it)}") }
        if (notFound > 0) add("${formatCount(notFound)} not on TVmaze, so they sort last")
    }
}.joinToString(" · ")

/** How far a check has got while it looks series up, or null when it isn't (or can't tell). */
val TvmazeUpdater.Status.fraction: Float? get() = if (running && todo > 0) done.toFloat() / todo else null
