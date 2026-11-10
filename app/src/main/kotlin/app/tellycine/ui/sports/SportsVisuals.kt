package app.tellycine.ui.sports

import android.text.format.DateUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GolfCourse
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import app.tellycine.core.sports.Sport
import app.tellycine.core.sports.SportCatalogue
import app.tellycine.core.sports.SportNames
import app.tellycine.data.sports.SportEvent
import app.tellycine.data.sports.SportSide
import app.tellycine.ui.components.formatTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Two balls, one behind the other: snooker, which Material has no icon for. */
private val SnookerIcon: ImageVector = ImageVector.Builder("Snooker", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(9f, 9f)
        arcToRelative(5f, 5f, 0f, true, true, 0f, 10f)
        arcToRelative(5f, 5f, 0f, true, true, 0f, -10f)
        close()
        moveTo(17f, 3.5f)
        arcToRelative(3f, 3f, 0f, true, true, 0f, 6f)
        arcToRelative(3f, 3f, 0f, true, true, 0f, -6f)
        close()
    }
}.build()

/** Each sport's icon. */
val Sport.icon: ImageVector
    get() = when (this) {
        Sport.FOOTBALL -> Icons.Filled.SportsSoccer
        Sport.F1 -> Icons.Filled.SportsMotorsports
        Sport.MOTOGP -> Icons.Filled.TwoWheeler
        Sport.GOLF -> Icons.Filled.GolfCourse
        Sport.TENNIS -> Icons.Filled.SportsTennis
        Sport.SNOOKER -> SnookerIcon
        Sport.DARTS -> Icons.Filled.TrackChanges
        Sport.BOXING -> Icons.Filled.SportsMma
        Sport.OTHER -> Icons.Filled.EmojiEvents
    }

/** A side's colour, and the colour of its code on it: a neutral one for sides without colours of their own. */
fun SportSide.colours(neutral: Color, onNeutral: Color): Pair<Color, Color> {
    val c = colour ?: return neutral to onNeutral
    val background = Color(0xFF000000.toInt() or c)
    return background to if (background.luminance() > 0.45f) Color(0xFF1F1B16) else Color.White
}

/** "Today", "Tomorrow", or the day and date: "Sunday 15 November". */
fun dayLabel(dayStartMs: Long): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(dayStartMs).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> day.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
    }
}

/** When an event is, in a few words: "Live · 47 min", "Today 21:00", "Sat 15:00". */
fun whenLabel(event: SportEvent, now: Long): String {
    if (event.isLive(now)) return "Live · ${elapsed(event, now)}"
    return startLabel(event.startMs, now)
}

/** "Today 21:00", "Tomorrow 15:00", "Sat 15:00", or a date further off. */
fun startLabel(startMs: Long, now: Long): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val time = formatTime(startMs)
    return when {
        day == today -> "Today $time"
        day == today.plusDays(1) -> "Tomorrow $time"
        day.isBefore(today.plusDays(7)) -> "${day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} $time"
        else -> "${day.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))} $time"
    }
}

/** How long ago an event started: "47 min", "1 hr 20". */
fun elapsed(event: SportEvent, now: Long): String {
    val minutes = ((now - event.startMs) / 60_000).coerceAtLeast(0)
    return if (minutes < 60) "$minutes min" else "${minutes / 60} hr ${(minutes % 60).toString().padStart(2, '0')}"
}

/** How far through an event is, from 0 to 1. */
fun progressOf(event: SportEvent, now: Long): Float {
    val length = (event.stopMs - event.startMs).coerceAtLeast(1)
    return ((now - event.startMs).toFloat() / length).coerceIn(0f, 1f)
}

/** "Starts in 13 minutes", "Starts in 2 hours", or nothing once it has started. */
fun startsIn(event: SportEvent, now: Long): String? {
    if (event.startMs <= now) return null
    return "Starts " + DateUtils.getRelativeTimeSpanString(event.startMs, now, DateUtils.MINUTE_IN_MILLIS).toString().replaceFirstChar { it.lowercase() }
}

/** The name to follow [side] by: the one Tellycine knows it by, or the guide's. */
fun nameToFollow(side: SportSide, sport: Sport): String = SportCatalogue.find(side.name, sport)?.name ?: side.name

/** Which of the names in [teams] is [name], in any of its names, if one is. */
fun followedAs(name: String, teams: List<String>): String? {
    val figure = SportCatalogue.find(name)
    val key = SportNames.key(name)
    return teams.firstOrNull { t -> SportNames.key(t) == key || (figure != null && SportCatalogue.find(t) == figure) }
}

/** "v Chelsea" for a team's match, or the event's name. */
fun opponentOrTitle(card: TeamCard, event: SportEvent): String {
    val home = event.home
    val away = event.away
    if (home == null || away == null) return event.title
    val key = SportNames.key(card.name)
    val other = when {
        SportNames.key(home.name) == key || SportCatalogue.find(home.name)?.name == card.name -> away
        SportNames.key(away.name) == key || SportCatalogue.find(away.name)?.name == card.name -> home
        else -> return event.title
    }
    return "v ${other.name}"
}
