package app.tellycine.player.multiview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MultiViewLimitsTest {
    private val limits = mapOf(1L to 2, 2L to null, 3L to 1)

    @Test
    fun upToFourChannels() {
        assertNull(MultiViewLimits.refusal(listOf(2, 2, 2), 2, null, limits::get))
        assertNotNull(MultiViewLimits.refusal(listOf(2, 2, 2, 2), 2, null, limits::get))
        assertEquals(0, MultiViewLimits.spare(listOf(2, 2, 2, 2), 2, null))
        assertEquals(3, MultiViewLimits.spare(listOf(2), 2, null))
    }

    @Test
    fun noMoreFromAPlaylistThanItsConnections() {
        assertNull(MultiViewLimits.refusal(listOf(1), 1, null, limits::get))
        assertNotNull(MultiViewLimits.refusal(listOf(1, 1), 1, null, limits::get))
        // Another playlist's connections are its own.
        assertNull(MultiViewLimits.refusal(listOf(1, 1), 2, null, limits::get))
        assertEquals(0, MultiViewLimits.spare(listOf(1, 1), 1, 2))
        assertEquals(2, MultiViewLimits.spare(listOf(1, 1), 2, null))
    }

    @Test
    fun changingAChannelFreesItsConnection() {
        // One connection, in use: a new channel can only take that channel's place.
        assertNotNull(MultiViewLimits.refusal(listOf(3), 3, null, limits::get))
        assertNull(MultiViewLimits.refusal(listOf(3), 3, 0, limits::get))
        // Four channels: replacing one is still allowed.
        assertNull(MultiViewLimits.refusal(listOf(2, 2, 2, 2), 2, 3, limits::get))
    }
}
