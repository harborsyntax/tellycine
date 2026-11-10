package app.tellycine.ui.settings

import app.tellycine.core.sports.Sport
import app.tellycine.data.db.SportsStatus
import app.tellycine.data.settings.AppSettings
import app.tellycine.ui.components.formatRelative

/** What Settings says about the Sports Hub, on phones and on TV. */
object SportsText {
    const val HUB = "The Sports tab, and Sports in the TV menu: what's on now and coming up, from your guides"
    const val EVENT_CHANNELS = "Read events from channel names, such as “UCL 04: Arsenal v Inter 20:00”"
    const val ABOUT = "Sport is found in your playlists' TV guides and channel names, on this device. Nothing is sent anywhere, and no scores are shown."

    fun replays(on: Boolean) = if (on) "Shown as well as live coverage" else "Off: live coverage only"

    /** The sports and teams followed, in a line. */
    fun followed(s: AppSettings): String {
        val sports = if (s.sports.containsAll(Sport.FOLLOWABLE)) "All sports" else Sport.FOLLOWABLE.filter { it in s.sports }.joinToString(", ") { it.label }
        return if (s.sportsTeams.isEmpty()) sports else "$sports · ${s.sportsTeams.joinToString(", ")}"
    }

    /**
     * What was found in each playlist, a line each: "Demo IPTV: 312 sport programmes and 40 event
     * channels, looked 5 minutes ago", or that it hasn't looked yet, or couldn't.
     */
    fun found(status: List<SportsStatus>, working: Boolean): String = buildString {
        if (working) append("Looking through your guides now…")
        for (s in status) {
            if (isNotEmpty()) append('\n')
            append(s.name).append(": ")
            when {
                s.error != null -> append("couldn't look through its guide (").append(s.error.take(120)).append(")")
                s.indexedAt == null || s.indexedAt == 0L -> append("not looked through yet")
                else -> append(count(s.programmes, "sport programme")).append(" and ").append(count(s.eventChannels, "event channel"))
                    .append(", looked ").append(formatRelative(s.indexedAt))
            }
        }
    }

    private fun count(n: Int, what: String) = "%,d %s%s".format(n, what, if (n == 1) "" else "s")
}
