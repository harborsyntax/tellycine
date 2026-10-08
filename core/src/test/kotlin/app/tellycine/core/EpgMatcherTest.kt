package app.tellycine.core

import app.tellycine.core.xmltv.EpgMatcher
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class EpgMatcherTest {
    @Test
    fun onlyTheFirstChannelMatchedByNameFeedsAPlaylistChannel() {
        val m = EpgMatcher(setOf("bbc one"))
        m.onChannel("bbc1.london", listOf("BBC One"))
        m.onChannel("bbc1.scotland", listOf("BBC One"))
        m.onChannel("bbc1.hd", listOf("BBC One", "BBC One HD"))
        assertEquals(listOf("bbc one"), m.targetsFor("BBC1.London"))
        assertEquals(emptyList<String>(), m.targetsFor("bbc1.scotland"))
        assertEquals(emptyList<String>(), m.targetsFor("bbc1.hd"))
    }

    @Test
    fun anExactIdBeatsADisplayNameMatchWhicheverComesFirst() {
        val m = EpgMatcher(setOf("bbc1.uk"))
        m.onChannel("bbc1.alt", listOf("bbc1.uk"))
        m.onChannel("BBC1.UK", listOf("BBC One"))
        assertEquals(listOf("bbc1.uk"), m.targetsFor("bbc1.uk"))
        assertEquals(emptyList<String>(), m.targetsFor("bbc1.alt"))

        // A later name match can't take it back.
        m.onChannel("bbc1.other", listOf("bbc1.uk"))
        assertEquals(emptyList<String>(), m.targetsFor("bbc1.other"))
    }

    @Test
    fun oneXmltvChannelCanFeedSeveralPlaylistChannels() {
        val m = EpgMatcher(setOf("news.uk", "news 24"))
        m.onChannel("news.uk", listOf("News 24"))
        assertEquals(setOf("news.uk", "news 24"), m.targetsFor("news.uk").toSet())
    }

    @Test
    fun undeclaredChannelsStillMatchByExactId() {
        val m = EpgMatcher(setOf("sport.1"))
        assertEquals(listOf("sport.1"), m.targetsFor("Sport.1"))
        assertEquals(emptyList<String>(), m.targetsFor("unknown"))
    }

    @Test
    fun repeatedStartTimesAreDroppedPerChannel() {
        val m = EpgMatcher(setOf("a", "b"))
        assertTrue(m.isNew("a", 1_000))
        assertFalse(m.isNew("a", 1_000))
        assertTrue(m.isNew("a", 2_000))
        assertTrue(m.isNew("b", 1_000))
    }
}
