package app.tellycine.core.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Competitions as guides name them, for filtering a sport by country and league. */
class CompetitionsTest {
    private fun football(text: String) = Competitions.of(text, Sport.FOOTBALL)

    @Test
    fun ukGuidesNames() {
        assertEquals(Competition("Premier League", "England"), football("EPL"))
        assertEquals(Competition("FA Cup", "England"), football("The Emirates FA Cup"))
        assertEquals(Competition("EFL Trophy", "England"), football("EFL Trophy"))
        assertEquals(Competition("EFL", "England"), football("EFL"))
        assertEquals(Competition("Women's Super League", "England"), football("WSL"))
        assertEquals(Competition("Scottish Premiership", "Scotland"), football("William Hill Premiership"))
        assertEquals(Competition("Scottish League Cup", "Scotland"), football("Premier Sports Cup"))
        assertEquals(Competition("Champions League", "Europe"), football("UEFA Champions League"))
        assertEquals(Competition("LaLiga", "Spain"), football("LaLiga"))
    }

    @Test
    fun theLongestNameFoundIsTheOne() {
        assertEquals(Competition("Canadian Premier League", "Canada"), football("Canadian Premier League"))
        assertEquals(Competition("Brasileirão", "Brazil"), football("Brazilian Serie A"))
        assertEquals(Competition("AFC Champions League", "Asia"), football("AFC Champions League"))
        assertEquals(Competition("Scottish League Cup", "Scotland"), football("Scottish League Cup"))
    }

    @Test
    fun womensAndYouthCompetitionsHaveTheirOwnNames() {
        assertEquals(Competition("Women's Champions League", "Europe"), football("Futbol Femenino UEFA Champions League"))
        assertEquals(Competition("Women's Champions League", "Europe"), football("UEFA Women's Champions League"))
        assertEquals(Competition("Women's Serie A", "Italy"), football("Women's Italian Serie A"))
        assertEquals(Competition("Women's World Cup U-17", "International"), football("2026 FIFA U-17 Women's World Cup"))
        assertEquals(Competition("NWSL", "USA"), football("Futbol NWSL"))
    }

    @Test
    fun americanGuidesNames() {
        assertEquals(Competition("Ligue 1", "France"), football("Futbol Ligue 1"))
        assertEquals(Competition("Primeira Liga", "Portugal"), football("Futbol Portugues Primeira Liga"))
        assertEquals(Competition("Liga MX", "Mexico"), football("Liga MX en 60"))
        assertEquals(Competition("Süper Lig", "Turkey"), football("Turkish Super Lig"))
        assertEquals(Competition("International Friendlies", "International"), football("Women's International Soccer Friendlies").let { it?.copy(name = it.name.removePrefix("Women's ")) })
    }

    @Test
    fun unknownCompetitionsWhereThePlaceIsPlain() {
        assertEquals(Competition("Austrian Bundesliga", "Austria"), football("Austrian Bundesliga"))
        assertEquals(Competition("Ukrainian Premier League", "Ukraine"), football("Ukrainian Premier League"))
        assertNull(football("Poker After Dark"))
        assertNull(football("College"))
        assertNull(football("Sign Up"))
    }

    @Test
    fun otherSportsTours() {
        assertEquals(Competition("ATP Finals", null), Competitions.of("Nitto ATP Finals", Sport.TENNIS))
        assertEquals(Competition("ATP Tour", null), Competitions.of("ATP Tour ATP Tennis", Sport.TENNIS))
        assertEquals(Competition("Premier League", null), Competitions.of("Premier League Darts", Sport.DARTS))
        assertEquals(Competition("UK Championship", null), Competitions.of("UK Championship", Sport.SNOOKER))
        assertEquals(Competition("DP World Tour", null), Competitions.of("DP World Tour Championship", Sport.GOLF))
        assertEquals(Competition("NFL", "USA"), Competitions.find("NFL LP 03: Bills @ Dolphins", Sport.OTHER))
    }

    @Test
    fun eventChannelsNames() {
        assertEquals(Competition("Premier League", "England"), Competitions.find("EPL 03: Arsenal v Chelsea 15:00", Sport.FOOTBALL))
        assertEquals(Competition("Champions League", "Europe"), Competitions.find("UCL 04: Arsenal v Inter @ 20:00", Sport.FOOTBALL))
        assertNull(Competitions.find("Sky Sports + 05: Barnet v L Orient", Sport.FOOTBALL))
    }
}
