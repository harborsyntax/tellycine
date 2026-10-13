package app.tellycine.core

import app.tellycine.core.tvmaze.Tvmaze
import app.tellycine.core.tvmaze.TvmazeShow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvmazeTest {
    @Test
    fun readsAShowWithItsEpisodes() {
        val show = Tvmaze.parseShow(
            """{"id":169,"name":"Breaking Bad","premiered":"2008-01-20","status":"Ended","updated":1704794122,
               "_embedded":{"previousepisode":{"id":1,"airdate":"2013-09-29"}}}"""
        )
        assertEquals(TvmazeShow(169, "Breaking Bad", "2008-01-20", true, 1704794122, "2013-09-29", null), show)
        val running = Tvmaze.parseShow(
            """{"id":2,"name":"Running","premiered":"2020-01-01","status":"Running","updated":5,
               "_embedded":{"previousepisode":{"airdate":"2025-05-01"},"nextepisode":{"airdate":""}}}"""
        )!!
        assertFalse(running.ended)
        assertEquals("2025-05-01", running.lastAired)
        // An episode without a date yet.
        assertNull(running.nextAired)
        assertNull(Tvmaze.parseShow("not json"))
    }

    @Test
    fun readsSearchResultsAndUpdates() {
        val results = Tvmaze.parseSearch(
            """[{"score":0.9,"show":{"id":210,"name":"Doctor Who","premiered":"2005-03-26","status":"Ended"}},
               {"score":0.8,"show":{"id":766,"name":"Doctor Who","premiered":"1963-11-23","status":"Ended"}}]"""
        )
        assertEquals(listOf(210, 766), results.map { it.id })
        assertEquals(mapOf(1 to 1700000000L, 2 to 1700000500L), Tvmaze.parseUpdates("""{"1":1700000000,"2":1700000500}"""))
        assertEquals(emptyMap<Int, Long>(), Tvmaze.parseUpdates("[]"))
    }

    @Test
    fun matchesByNameAndYear() {
        val who2005 = TvmazeShow(210, "Doctor Who", "2005-03-26", true, null, null, null)
        assertTrue(Tvmaze.matches(who2005, "Doctor Who", 2005))
        assertTrue(Tvmaze.matches(who2005, "doctor who", null))
        // Providers' years can be a year out.
        assertTrue(Tvmaze.matches(who2005, "Doctor Who", 2006))
        assertFalse(Tvmaze.matches(who2005, "Doctor Who", 1963))
        assertFalse(Tvmaze.matches(who2005, "Doctor Who Confidential", 2005))
    }
}
