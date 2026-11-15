package app.tellycine.ui.sports

import app.tellycine.core.sports.AiringKind
import app.tellycine.core.sports.Sport
import app.tellycine.core.sports.SportAliases
import app.tellycine.data.sports.SportEvent
import app.tellycine.data.sports.SportSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What "Same as…" remembers: sides' names where it can, so their next matches are one too. */
class SameAsTest {
    private fun event(home: String?, away: String?, sport: Sport = Sport.OTHER, kind: AiringKind = AiringKind.LIVE, title: String = "$home v $away") = SportEvent(
        key = "$sport|coverage|$title|0", sport = sport, kind = kind, title = title, competition = null, session = null,
        home = home?.let { SportSide(it, "", null) }, away = away?.let { SportSide(it, "", null) }, startMs = 0, stopMs = 1, sources = emptyList(), followed = emptyList(),
    )

    @Test
    fun eachSideAsTheOneMostLikeIt() {
        // Either way round.
        assertEquals(
            listOf(SportAliases.sideLine(Sport.OTHER, "les bills", "bills"), SportAliases.sideLine(Sport.OTHER, "les vikes", "vikings")),
            SportsViewModel.sameAsLines(event("Les Vikes", "Les Bills"), event("Bills", "Vikings")).sorted(),
        )
    }

    @Test
    fun otherwiseTheEventItself() {
        val race = event(null, null, Sport.F1, title = "GP do Brasil")
        val other = event(null, null, Sport.F1, title = "Brazilian Grand Prix")
        assertEquals(listOf(SportAliases.eventLine(race.key.substringBeforeLast('|'), other.key.substringBeforeLast('|'))), SportsViewModel.sameAsLines(race, other))
        // Live and a replay of it, or two sports, aren't one by their sides.
        assertTrue(SportsViewModel.sameAsLines(event("A", "B"), event("C", "D", kind = AiringKind.REPLAY)).single().startsWith("event\t"))
        assertTrue(SportsViewModel.sameAsLines(event("A", "B"), event("C", "D", sport = Sport.FOOTBALL)).single().startsWith("event\t"))
    }
}
