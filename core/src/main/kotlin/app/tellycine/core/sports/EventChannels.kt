package app.tellycine.core.sports

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** An event a channel's name announces: "EPL 03: Arsenal v Chelsea 15:00". */
data class EventChannel(
    val sport: Sport,
    val title: String,
    val home: String?,
    val away: String?,
    /** When it starts, if the name says; for a time without a day, the next time it comes round. */
    val startMs: Long?,
    /** The competition, when the name puts it before the sides: "EFL Trophy". */
    val competition: String? = null,
)

/**
 * Reads the events that providers put in their event channels' names, which change from day to
 * day: "EPL 03: Arsenal v Chelsea 15:00", "PPV 1 | Joshua vs Dubois | Sat 22:00 UK",
 * "UCL 04: Arsenal v Inter @ 20:00". Times without a time zone are taken to be UK time.
 */
object EventChannels {
    private val UK: ZoneId = ZoneId.of("Europe/London")

    /** What [name] announces, as of [now], or null if it isn't an event channel's name. */
    fun parse(name: String, now: Long, defaultZone: ZoneId = UK): EventChannel? {
        // Superscript and other look-alike letters ("ᴴᴰ") as plain ones.
        val plain = SportNames.plain(name)
        if (PLACEHOLDER.containsMatchIn(plain) || NOT_AN_EVENT.containsMatchIn(plain)) return null
        val zone = ZONE.find(plain)?.groupValues?.get(1)?.let { zoneOf(it) } ?: defaultZone
        val american = zone.id.startsWith("America/")
        val time = timeOf(plain)
        val date = dateOf(plain, now, zone, american)

        // The name without the provider's prefix, the time, the date and the time zone.
        var text = plain
        for (r in listOf(DATE_ISO, TIME_AM_PM, TIME_COLON, DATE_DOTTED, TIME_DOT, DATE_NUMERIC, DATE_WORDS, DATE_WORDS_US, WEEKDAY, ZONE)) text = r.replace(text, " ")
        text = text.replace(AT, " ").replace(BRACKETS_EMPTY, " ").replace(SPACES, " ").trim(' ', '-', '|', ':', '@', '–')

        val segments = text.split(SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }
        val sides = segments.firstNotNullOfOrNull(::sidesOf)
        val sport = sportOf(plain, sides, american)
        if (sides == null && (sport == null || time == null)) return null

        // Without sides, what follows the channel's own name: "F1 TV: Brazilian GP - Race" is "Brazilian GP · Race".
        val title = if (sides != null) "${sides.home} v ${sides.away}" else (if (segments.size > 1) segments.drop(1) else segments).joinToString(" · ")
        if (title.isBlank()) return null
        return EventChannel(
            sport = sport ?: Sport.OTHER,
            title = title,
            home = sides?.home,
            away = sides?.away,
            startMs = time?.let { startOf(it, date, now, zone) },
            competition = sides?.competition,
        )
    }

    private class Sides(val home: String, val away: String, val competition: String?)

    private fun sidesOf(part: String): Sides? {
        val m = SIDES.find(part) ?: return null
        var home = m.groupValues[1].replace(SLOT, "").trim(' ', '-', '|', ':')
        var away = m.groupValues[2]
        // "Hornets (Mobile View (In-Arena))": what's in brackets after a side isn't its name.
        while (true) {
            val stripped = away.replace(TRAILING_BRACKETS, "")
            if (stripped == away) break
            away = stripped
        }
        away = away.trim(' ', '-', '|', ':')
        // The competition before the first side: "EFL EFL Trophy Grimsby" is Grimsby, in the EFL Trophy.
        val words = home.split(' ').filter { it.isNotEmpty() }
        val leading = words.takeWhile { it in COMPETITION_WORDS || (it.length >= 2 && it.all { c -> c.isUpperCase() || c.isDigit() || c == '+' }) }
        val competition = if (leading.isNotEmpty() && leading.size < words.size && leading.any { it in COMPETITIONS }) {
            home = words.drop(leading.size).joinToString(" ")
            leading.distinct().joinToString(" ")
        } else {
            null
        }
        if (home.isEmpty() || away.isEmpty() || home.none { it.isLetter() } || away.none { it.isLetter() }) return null
        if (home.split(' ').size > 5 || away.split(' ').size > 5) return null
        return Sides(home, away, competition)
    }

    private fun sportOf(name: String, sides: Sides?, american: Boolean): Sport? {
        val w = " " + SportNames.key(name) + " "
        fun has(vararg words: String) = words.any { w.contains(" $it ") }
        // American times or services: their football isn't football (but their soccer is).
        val us = american || has("ncaa", "espn", "espnu", "espn unltd", "sec", "accn", "accnx", "big10", "big ten", "fox one", "tsn", "college")
        return when {
            has("soccer") -> Sport.FOOTBALL
            us && has("football") -> Sport.OTHER
            has(
                "nfl", "nba", "nhl", "mlb", "ufc", "mma", "rugby", "cricket", "wwe", "aew", "nascar", "indycar", "basketball", "baseball",
                "hockey", "volleyball", "lacrosse", "softball", "ncaa",
            ) -> Sport.OTHER
            has("f1", "formula 1", "formula one") -> Sport.F1
            has("motogp", "moto gp") -> Sport.MOTOGP
            has("snooker") -> Sport.SNOOKER
            has("darts", "pdc") -> Sport.DARTS
            has("golf", "pga") -> Sport.GOLF
            has("tennis", "atp", "wta") -> Sport.TENNIS
            has("boxing") -> Sport.BOXING
            has("epl", "efl", "ucl", "uel", "uecl", "spfl", "football", "soccer", "premier league", "laliga", "la liga", "bundesliga", "serie a", "ligue 1", "mls", "fa cup") -> Sport.FOOTBALL
            sides != null -> {
                val a = SportCatalogue.find(sides.home)
                val b = SportCatalogue.find(sides.away)
                if (a != null && b != null && a.sport == b.sport && !a.followsWholeSport) a.sport else null
            }
            else -> null
        }
    }

    private fun timeOf(name: String): LocalTime? {
        TIME_AM_PM.find(name)?.let { m ->
            val hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
            if (hour in 1..12 && minute in 0..59) {
                val pm = m.groupValues[3].equals("pm", ignoreCase = true)
                return LocalTime.of(hour % 12 + if (pm) 12 else 0, minute)
            }
        }
        TIME_COLON.find(name)?.let { m -> clock(m.groupValues[1], m.groupValues[2])?.let { return it } }
        TIME_DOT.find(name)?.let { m -> clock(m.groupValues[1], m.groupValues[2])?.takeIf { it.minute % 5 == 0 }?.let { return it } }
        return null
    }

    private fun clock(h: String, m: String): LocalTime? {
        val hour = h.toInt()
        val minute = m.toInt()
        return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
    }

    private fun dateOf(name: String, now: Long, zone: ZoneId, american: Boolean): LocalDate? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        fun near(month: Int, day: Int): LocalDate? = runCatching {
            // The nearest such day, this year or the next: "02/01" seen in December is in January.
            val date = LocalDate.of(today.year, month, day)
            if (date.isBefore(today.minusMonths(6))) date.plusYears(1) else date
        }.getOrNull()
        DATE_ISO.find(name)?.let { m ->
            runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()?.let { return it }
        }
        // "11.10 12:00 PM ET" is the 10th of November; "10.11 15:00" in the UK is too.
        DATE_DOTTED.find(name)?.let { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            (if (american) near(a, b) else near(b, a))?.let { return it }
        }
        DATE_NUMERIC.find(name)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            near(month, day)?.let { return it }
        }
        DATE_WORDS.find(name)?.let { m -> monthOf(m.groupValues[2])?.let { near(it, m.groupValues[1].toInt()) }?.let { return it } }
        DATE_WORDS_US.find(name)?.let { m -> monthOf(m.groupValues[1])?.let { near(it, m.groupValues[2].toInt()) }?.let { return it } }
        WEEKDAY.find(name)?.let { m ->
            val day = DAYS[m.groupValues[1].take(3).lowercase(Locale.ROOT)] ?: return@let
            return today.with(TemporalAdjusters.nextOrSame(day))
        }
        return null
    }

    private fun startOf(time: LocalTime, date: LocalDate?, now: Long, zone: ZoneId): Long {
        if (date != null) return ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val start = ZonedDateTime.of(today, time, zone).toInstant().toEpochMilli()
        // A time that has been and gone (more than a few hours ago) is tomorrow's.
        return if (start < now - LATE_START_MS) ZonedDateTime.of(today.plusDays(1), time, zone).toInstant().toEpochMilli() else start
    }

    private fun monthOf(text: String): Int? = MONTHS.indexOfFirst { text.lowercase(Locale.ROOT).startsWith(it) }.takeIf { it >= 0 }?.plus(1)

    private fun zoneOf(text: String): ZoneId? = when (text.lowercase(Locale.ROOT)) {
        "uk", "gmt", "bst", "london" -> UK
        "et", "est", "edt" -> ZoneId.of("America/New_York")
        "ct", "cst", "cdt" -> ZoneId.of("America/Chicago")
        "pt", "pst", "pdt" -> ZoneId.of("America/Los_Angeles")
        "cet", "cest" -> ZoneId.of("Europe/Paris")
        else -> null
    }

    /** How long after its time an event still counts as today's. */
    private const val LATE_START_MS = 4 * 60 * 60 * 1000L

    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val DAYS = mapOf(
        "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY, "thu" to DayOfWeek.THURSDAY,
        "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY,
    )

    private val PLACEHOLDER = Regex("\\b(?:no event|no events|no event scheduled|no match|no stream|offline|coming soon|off air|event not started|tba|tbc)\\b|^[\\s\\-_|:#*]*$", RegexOption.IGNORE_CASE)

    /** Channels named after something that isn't an event. */
    private val NOT_AN_EVENT = Regex("\\bpress conference\\b|\\bstudio show\\b", RegexOption.IGNORE_CASE)
    private val TIME_COLON = Regex("(?<![\\d/])([01]?\\d|2[0-3]):([0-5]\\d)(?::[0-5]\\d)?(?!\\d)")
    private val DATE_ISO = Regex("(?<!\\d)(20\\d\\d)-(\\d{1,2})-(\\d{1,2})(?!\\d)")
    private val DATE_DOTTED = Regex("(?<![\\d.])(\\d{1,2})\\.(\\d{1,2})(?=\\s+\\d{1,2}[:.]\\d{2})")
    private val TIME_AM_PM = Regex("(?<![\\d/:.])(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm)\\b", RegexOption.IGNORE_CASE)
    private val TIME_DOT = Regex("(?<![\\d./])([01]?\\d|2[0-3])\\.([0-5]\\d)(?![\\d.])")
    private val DATE_NUMERIC = Regex("(?<!\\d)(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?(?!\\d)")
    private val DATE_WORDS = Regex("(?<!\\d)(\\d{1,2})(?:st|nd|rd|th)?\\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\b", RegexOption.IGNORE_CASE)
    private val DATE_WORDS_US = Regex("\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\s+(\\d{1,2})(?:st|nd|rd|th)?(?!\\d)", RegexOption.IGNORE_CASE)
    private val WEEKDAY = Regex("\\b(mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)(?:day|sday|nesday|rsday|urday)?\\b\\.?", RegexOption.IGNORE_CASE)
    private val ZONE = Regex("\\b(UK|GMT|BST|ET|EST|EDT|CT|CST|CDT|PT|PST|PDT|CET|CEST|London)\\b")
    private val TRAILING_BRACKETS = Regex("\\s*\\((?:[^()]|\\([^()]*\\))*\\)\\s*$|\\s*\\[[^\\]]*]\\s*$")

    /** Competitions providers put before the sides, and the words around them. */
    private val COMPETITIONS = setOf("EFL", "EPL", "ATP", "WTA", "NBA", "NFL", "NHL", "MLB", "UFC", "PDC", "UCL", "UEL", "SPFL", "NCAA", "MLS", "FA", "WSL")
    private val COMPETITION_WORDS = COMPETITIONS + setOf("Tour", "Tennis", "Trophy", "Cup", "League", "Football", "Soccer", "Darts", "Golf")
    /** "@" before a time ("@ 10 Nov 06:45 PM"), or left on its own once the time's gone; not "Cavaliers @ Hornets". */
    private val AT = Regex("\\s@\\s*(?=$|\\d|[|:–-])|^@\\s*")
    private val BRACKETS_EMPTY = Regex("[(\\[]\\s*[)\\]]")
    private val SPACES = Regex("\\s+")
    private val SEPARATORS = Regex("\\s*:\\s+|\\s+[-–—]\\s+|\\s*\\|\\s*")
    private val SIDES = Regex("^(.+?)\\s+(?:v|vs\\.?|versus|@)\\s+(.+)$", RegexOption.IGNORE_CASE)

    /** A provider's slot before the name: "EPL 03 ", "PPV 1 ", "Event 5 ". */
    private val SLOT = Regex("^(?:[A-Za-z+]{2,10}\\s*\\d{1,3}\\s+)+")
}
