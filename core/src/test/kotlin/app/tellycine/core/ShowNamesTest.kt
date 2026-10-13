package app.tellycine.core

import app.tellycine.core.ShowNames.Cleaned
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShowNamesTest {
    private fun clean(name: String) = ShowNames.clean(name)

    @Test
    fun takesOffProvidersCodesTagsAndYears() {
        assertEquals(Cleaned("Breaking Bad", null), clean("EN - Breaking Bad"))
        assertEquals(Cleaned("Breaking Bad", null), clean("|EN| Breaking Bad"))
        assertEquals(Cleaned("Breaking Bad", 2008), clean("[EN] Breaking Bad (2008)"))
        assertEquals(Cleaned("Doctor Who", 2005), clean("UK | Doctor Who (2005) [4K]"))
        assertEquals(Cleaned("Stranger Things", null), clean("4K-NF - Stranger Things"))
        assertEquals(Cleaned("The Office", null), clean("The Office (US) FHD"))
        assertEquals(Cleaned("Shōgun", 2024), clean("Shōgun (2024) MULTI-SUB"))
    }

    @Test
    fun leavesTitlesAlone() {
        // Colons and numbers belong to titles.
        assertEquals(Cleaned("CSI: Vegas", null), clean("CSI: Vegas"))
        assertEquals(Cleaned("24 - Live Another Day", null), clean("24 - Live Another Day"))
        assertEquals(Cleaned("9-1-1: Lone Star", null), clean("9-1-1: Lone Star"))
        assertEquals(Cleaned("ER", null), clean("ER"))
        assertEquals(Cleaned("Blade Runner 2049", null), clean("Blade Runner 2049"))
        // Nothing left but tags: keep the name.
        assertEquals("[4K]", clean("[4K]").title)
    }

    @Test
    fun keysMatchTheSameSeries() {
        assertEquals(ShowNames.key("Shogun", 2024), ShowNames.key("Shōgun", 2024))
        assertEquals(ShowNames.key("The Office", null), ShowNames.key("office", null))
        assertEquals(ShowNames.key("Law & Order", 1990), ShowNames.key("Law and Order", 1990))
        assertEquals(ShowNames.key("Grey's Anatomy", null), ShowNames.key("Greys Anatomy", null))
        assert(ShowNames.key("Doctor Who", 2005) != ShowNames.key("Doctor Who", 1963))
    }

    @Test
    fun yearsFromProvidersFields() {
        assertEquals(2019, ShowNames.yearOf("2019-05-03"))
        assertEquals(2019, ShowNames.yearOf(" 2019 "))
        assertNull(ShowNames.yearOf("N/A"))
        assertNull(ShowNames.yearOf("0000"))
        assertNull(ShowNames.yearOf(null))
    }
}
