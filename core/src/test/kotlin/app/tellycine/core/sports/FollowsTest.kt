package app.tellycine.core.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Finding the events of the teams and players someone follows. */
class FollowsTest {
    @Test
    fun aTeamByAnyOfItsNames() {
        assertEquals("Man Utd", Follows(listOf("Man Utd")).match(Sport.FOOTBALL, "Manchester United", "Spurs", ""))
        assertEquals("Arsenal", Follows(listOf("Arsenal")).match(Sport.FOOTBALL, "Arsenal Women", "Chelsea Women", ""))
        assertEquals("Tottenham Hotspur", Follows(listOf("Tottenham Hotspur")).match(Sport.FOOTBALL, "Brentford", "Spurs", ""))
    }

    @Test
    fun notAnotherTeamWithTheNameInIt() {
        assertNull(Follows(listOf("Rangers")).match(Sport.FOOTBALL, "Queens Park Rangers", "Millwall", ""))
        assertNull(Follows(listOf("Arsenal")).match(Sport.FOOTBALL, "Chelsea", "Liverpool", "Former Arsenal man returns"))
    }

    @Test
    fun teamsTheCatalogueDoesntKnow() {
        assertEquals("Tranmere", Follows(listOf("Tranmere")).match(Sport.FOOTBALL, "Tranmere Rovers", "Wrexham", ""))
    }

    @Test
    fun playersByNameInTheTitleOrDescription() {
        val follows = Follows(listOf("Ronnie O'Sullivan"))
        assertEquals("Ronnie O'Sullivan", follows.match(Sport.SNOOKER, null, null, "UK Championship. O'Sullivan faces Judd Trump in round two."))
        assertNull(follows.match(Sport.DARTS, null, null, "O'Sullivan"))
        assertEquals("Jannik Sinner", Follows(listOf("Jannik Sinner")).match(Sport.TENNIS, "Sinner", "Zverev", ""))
    }

    @Test
    fun aRacingTeamFollowsItsSportsRaces() {
        assertEquals("McLaren", Follows(listOf("McLaren")).match(Sport.F1, null, null, "Brazilian Grand Prix"))
        assertNull(Follows(listOf("McLaren")).match(Sport.MOTOGP, null, null, "Valencia Grand Prix"))
    }
}
