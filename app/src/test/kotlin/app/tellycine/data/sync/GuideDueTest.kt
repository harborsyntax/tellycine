package app.tellycine.data.sync

import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a TV guide is refreshed as the app starts: when it's running out, or every two days. */
class GuideDueTest {
    private val now = 1_000 * DAY

    /** A guide refreshed [ago], with programmes for most channels for [left] more. */
    private fun guide(ago: Long?, left: Long?) = PlaylistEntity(
        name = "Guide", type = PlaylistType.M3U_URL, url = "",
        epgCheckedAt = ago?.let { now - it }, epgCoveredUntil = left?.let { now + it },
    )

    private fun due(ago: Long?, left: Long?, days: Int = 3) = SyncManager.guideDue(guide(ago, left), days, now)

    @Test
    fun aGuideIsRefreshedWhenItsRunningOut() {
        assertTrue(due(ago = null, left = null))
        // Three days kept: when less than a day is left.
        assertFalse(due(ago = 13 * HOUR, left = 2 * DAY))
        assertTrue(due(ago = 13 * HOUR, left = 20 * HOUR))
        // One day kept: when less than half a day is left.
        assertFalse(due(ago = 13 * HOUR, left = 13 * HOUR, days = 1))
        assertTrue(due(ago = 13 * HOUR, left = 11 * HOUR, days = 1))
    }

    @Test
    fun butNoMoreThanEvery12HoursAndAtLeastEveryTwoDays() {
        // A provider with little guide doesn't get asked at every start.
        assertFalse(due(ago = 6 * HOUR, left = HOUR))
        // Schedules change, so even a long guide is refreshed now and then.
        assertTrue(due(ago = 2 * DAY, left = 5 * DAY))
    }

    @Test
    fun guidesImportedBeforeAreRefreshedEvery12Hours() {
        // An older version kept only when it imported the guide, not what's left.
        val older = PlaylistEntity(name = "Guide", type = PlaylistType.M3U_URL, url = "", epgSyncedAt = now - 13 * HOUR)
        assertTrue(SyncManager.guideDue(older, 3, now))
        assertFalse(SyncManager.guideDue(older.copy(epgSyncedAt = now - 11 * HOUR), 3, now))
    }

    private companion object {
        const val HOUR = 60 * 60 * 1000L
        const val DAY = 24 * HOUR
    }
}
