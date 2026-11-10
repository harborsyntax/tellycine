package app.tellycine.ui.settings

import app.tellycine.core.sports.Sport
import app.tellycine.data.settings.AppSettings

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
}
