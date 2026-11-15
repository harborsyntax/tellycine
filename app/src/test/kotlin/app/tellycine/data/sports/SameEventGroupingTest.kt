package app.tellycine.data.sports

import app.tellycine.core.ContentKind
import app.tellycine.core.sports.AiringKind
import app.tellycine.core.sports.Follows
import app.tellycine.core.sports.Sport
import app.tellycine.core.sports.SportAliases
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import app.tellycine.data.db.SportAiringEntity
import app.tellycine.data.db.StreamEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/** The same event, named differently by different channels' guides, is one event. */
class SameEventGroupingTest {
    private val playlist = PlaylistEntity(id = 1, name = "P", type = PlaylistType.M3U_URL, url = "")
    private val minute = 60_000L
    private val kickOff = 1_800_000_000_000L

    private fun source(
        channel: String,
        title: String,
        home: String?,
        away: String?,
        start: Long = kickOff,
        minutes: Int = 180,
        sport: Sport = Sport.OTHER,
        kind: AiringKind = AiringKind.LIVE,
        session: String? = null,
        categoryId: String? = null,
    ): SportSource {
        val stream = StreamEntity(1, ContentKind.LIVE, channel, channel, 0, categoryId, null, "https://e/$channel.ts", epgChannelId = channel)
        val airing = SportAiringEntity(1, channel, start, start + minutes * minute, sport, kind, title, null, session, home, away, title, null, null)
        return SportSource(playlist, stream, airing)
    }

    private fun group(vararg sources: SportSource, aliases: SportAliases = SportAliases.NONE) =
        SportsRepository.group(sources.toList(), Follows(emptyList()), aliases)

    @Test
    fun oneMatchAsGuidesInTheUsCanadaAndLatinAmericaNameIt() {
        val events = group(
            source("LAT: ESPN DEPORTES", "Buffalo Bills v Minnesota Vikings", "Buffalo Bills", "Minnesota Vikings"),
            source("AL | Birmingham | ABC 33/40", "Buffalo Bills v Minnesota Vikings", "Buffalo Bills", "Minnesota Vikings", start = kickOff + 15 * minute),
            source("RDS", "Bills de Buffalo v Vikings du Minnesota", "Bills de Buffalo", "Vikings du Minnesota", start = kickOff - 30 * minute),
            source("NFL Channel", "Bills v Vikings", "Bills", "Vikings", kind = AiringKind.EVENT),
        )
        assertEquals(1, events.size)
        assertEquals(4, events.single().sources.size)
        // Called by the name most of its channels give it.
        assertEquals("Buffalo Bills v Minnesota Vikings", events.single().title)
    }

    @Test
    fun aFighterInTwoFightsAndAMatchShownAgainLaterAreNot() {
        val events = group(
            source("UFC Channel", "Ciryl Gane v Tanner Boser", "Ciryl Gane", "Tanner Boser", minutes = 30),
            source("UFC Channel", "Ciryl Gane v Tai Tuivasa", "Ciryl Gane", "Tai Tuivasa", start = kickOff + 30 * minute, minutes = 30),
            // The same match, later the same day, isn't on at the same time.
            source("NFL Channel", "Bills v Vikings", "Bills", "Vikings", minutes = 180),
            source("NFL Network", "Buffalo Bills v Minnesota Vikings", "Buffalo Bills", "Minnesota Vikings", start = kickOff + 6 * 60 * minute),
            // And live coverage isn't a replay of it.
            source("ESPN", "Buffalo Bills v Minnesota Vikings", "Buffalo Bills", "Minnesota Vikings", kind = AiringKind.REPLAY),
        )
        assertEquals(5, events.size)
    }

    @Test
    fun oneRaceWhateverItsCalled() {
        val events = group(
            source("Sky Sports F1", "Brazilian Grand Prix", null, null, sport = Sport.F1, minutes = 150, session = "Race"),
            source("SporTV", "GP do Brasil", null, null, start = kickOff + 5 * minute, sport = Sport.F1, minutes = 120),
            // Qualifying is another session.
            source("F1 TV", "Brazilian Grand Prix", null, null, start = kickOff + 10 * minute, sport = Sport.F1, minutes = 60, session = "Qualifying"),
        )
        assertEquals(listOf(2, 1), events.map { it.sources.size }.sortedDescending())
    }

    @Test
    fun namesSomeoneSaidAreTheSame() {
        val sources = arrayOf(
            source("TSN", "Bills v Vikings", "Bills", "Vikings"),
            // Nothing alike, and not on at quite the same time: only someone can say.
            source("TVA Sports", "Les Bills v Les Vikes", "Les Bills", "Les Vikes", start = kickOff + 2 * 60 * minute),
        )
        assertEquals(2, group(*sources).size)
        val aliases = SportAliases.NONE.plus(
            listOf(
                SportAliases.sideLine(Sport.OTHER, SportsRepository.sideKey("Les Bills", Sport.OTHER), SportsRepository.sideKey("Bills", Sport.OTHER)),
                SportAliases.sideLine(Sport.OTHER, SportsRepository.sideKey("Les Vikes", Sport.OTHER), SportsRepository.sideKey("Vikings", Sport.OTHER)),
            ),
        )
        assertEquals(1, group(*sources, aliases = aliases).size)
    }
}
