package app.tellycine.ui.settings

import app.tellycine.data.tmdb.TmdbUpdater
import app.tellycine.ui.components.formatCount
import app.tellycine.ui.components.formatRelative

/** The words for details from TMDB, in Settings and on the Playlists page (phone and TV). */
object TmdbText {
    const val TITLE = "Details from TMDB"
    const val KEY = "TMDB API key"
    const val KEY_PAGE = "https://www.themoviedb.org/settings/api"
    const val KEY_REJECTED = "TMDB didn't accept this key. Copy the API Key, or the API Read Access Token, from your TMDB account's API settings."
    const val KEY_UNREACHABLE = "Couldn't reach TMDB to check the key. Check your connection and try again."

    /** The attribution TMDB's terms ask for, with its logo and a link to its site. */
    const val NOTICE = "This product uses the TMDB API but is not endorsed or certified by TMDB."
    const val SITE = "https://www.themoviedb.org"
    const val SITE_NAME = "www.themoviedb.org"

    /** What the setting does; [builtIn] when this build has Tellycine's own key, so none is needed. */
    fun setting(builtIn: Boolean): String =
        "Adds plots, cast, ratings, trailers, pictures and episode details to movies and series, " +
            "and sorting by rating or when series last aired. " +
            (if (builtIn) "Sends their names to TMDB. " else "Sends their names to TMDB, with your own API key. ") +
            "The first look-up takes a while."

    /** How to get a key of their own. */
    fun keyHelp(builtIn: Boolean): String {
        val why = if (builtIn) {
            "Tellycine has its own TMDB key, so you don't need one. To use yours instead (free for personal use), "
        } else {
            "This copy of Tellycine doesn't come with a TMDB key, so you need your own, which is free for personal use. To get one, "
        }
        return why + "sign in at themoviedb.org, open Settings → API and request a developer key, then copy the API Key " +
            "(or the API Read Access Token) here."
    }

    /** The user's [key] as Settings shows it: whether there is one, and its last few characters. */
    fun keySummary(key: String, builtIn: Boolean): String = when {
        key.isNotBlank() -> "Saved: ending ${key.takeLast(4)}"
        builtIn -> "Optional. Tellycine's own key is used unless you add yours."
        else -> "Not set. Needed for details from TMDB."
    }
}

/** What a check is doing, or what it found: "Looking up titles: 120 of 3,000". */
fun TmdbUpdater.Status.headline(): String = when {
    running && todo > 0 -> "Looking up titles: ${formatCount(done)} of ${formatCount(todo)}"
    running -> "Checking TMDB…"
    titles == 0 -> "No movies or series to look up yet"
    else -> "${formatCount(found)} of ${formatCount(titles)} movies and series found"
}

/** More about it: why it stopped, what's still to look up, when it last checked, and what wasn't found. */
fun TmdbUpdater.Status.details(): String = buildList {
    error?.let(::add)
    if (!running) {
        val waiting = titles - found - notFound
        if (waiting > 0) add("${formatCount(waiting)} still to look up")
        checkedAt?.let { add("Checked ${formatRelative(it)}") }
        if (notFound > 0) add("${formatCount(notFound)} not on TMDB")
    }
}.joinToString(" · ")

/** How far a check has got while it looks titles up, or null when it isn't (or can't tell). */
val TmdbUpdater.Status.fraction: Float? get() = if (running && todo > 0) done.toFloat() / todo else null
