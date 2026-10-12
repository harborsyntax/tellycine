package app.tellycine.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseDatesTest {
    @Test
    fun keepsTheDayWhenKnownOtherwiseTheYear() {
        assertEquals("2023-05-01", ReleaseDates.normalise("2023-05-01"))
        assertEquals("2023-05-01", ReleaseDates.normalise("2023-05-01 20:00:00"))
        assertEquals("2023", ReleaseDates.normalise("2023"))
        assertEquals("2023", ReleaseDates.normalise("05/2023"))
        assertEquals("2023", ReleaseDates.normalise("2023-13-45"))
        assertNull(ReleaseDates.normalise(""))
        assertNull(ReleaseDates.normalise("n/a"))
        assertNull(ReleaseDates.normalise("0000-00-00"))
        assertEquals("2019-02-02", ReleaseDates.first(null, "", "2019-02-02", "2020"))
    }

    @Test
    fun findsBracketedYearsInTitles() {
        assertEquals("2023", ReleaseDates.yearInTitle("Film Name (2023)"))
        assertEquals("1999", ReleaseDates.yearInTitle("Film Name [1999] [4K]"))
        assertEquals("2010", ReleaseDates.yearInTitle("Remake (1980) (2010)"))
        // A bare number is often part of the name.
        assertNull(ReleaseDates.yearInTitle("Blade Runner 2049"))
        assertNull(ReleaseDates.yearInTitle("Show (12345)"))
    }
}
