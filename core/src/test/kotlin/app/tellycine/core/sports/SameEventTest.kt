package app.tellycine.core.sports

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same team or event, by the names different guides give it. */
class SameEventTest {
    private fun same(a: String, b: String, sport: Sport = Sport.FOOTBALL) = SameEvent.sameSide(a, b, sport)

    @Test
    fun theSameSideByAnotherName() {
        // From guides in the US, Canada and the UK.
        assertTrue(same("Bills", "Buffalo Bills", Sport.OTHER))
        assertTrue(same("Bills de Buffalo", "Buffalo Bills", Sport.OTHER))
        assertTrue(same("Vikings du Minnesota", "Minnesota Vikings", Sport.OTHER))
        assertTrue(same("L Orient", "Leyton Orient"))
        assertTrue(same("Bayern München", "Bayern Munich"))
        assertTrue(same("Man Utd", "Manchester United"))
        assertTrue(same("Sheff Wed", "Sheffield Wednesday"))
        assertTrue(same("Atlético Madrid", "Atletico de Madrid"))
        assertTrue(same("Philadelphia 76ers", "76ers", Sport.OTHER))
    }

    @Test
    fun notAnotherSide() {
        assertFalse(same("Manchester United", "Manchester City"))
        assertFalse(same("Man Utd", "Man City"))
        assertFalse(same("Real Madrid", "Real Sociedad"))
        assertFalse(same("Arsenal", "Arsenal Women"))
        assertFalse(same("England U21", "England"))
        assertFalse(same("Tanner Boser", "Tai Tuivasa", Sport.OTHER))
        assertFalse(same("Austria", "Australia"))
        assertFalse(same("L Orient", "Luton"))
    }

    @Test
    fun theSameMatchEitherWayRound() {
        assertTrue(SameEvent.sameSides("Buffalo Bills" to "Minnesota Vikings", "Vikings du Minnesota" to "Bills de Buffalo", Sport.OTHER))
        // A fighter in two fights isn't one fight.
        assertFalse(SameEvent.sameSides("Ciryl Gane" to "Tanner Boser", "Ciryl Gane" to "Tai Tuivasa", Sport.OTHER))
    }

    @Test
    fun eventsWithoutSides() {
        assertTrue(SameEvent.sameTitle("UK Championship", "Snooker UK Championship"))
        assertTrue(SameEvent.sameTitle("Grand Slam of Darts", "Grand Slam Darts"))
        assertTrue(SameEvent.sameTitle("DP World Tour Championship", "DP World Tour Championship Dubai"))
        assertFalse(SameEvent.sameTitle("Golf", "DP World Tour Championship"))
        assertFalse(SameEvent.sameTitle("UK Championship", "Champion of Champions"))
    }
}
