package app.tellycine.ui.search

import app.tellycine.core.ContentKind
import app.tellycine.data.db.ProgrammeEntity
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.repo.ProgrammeHit
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchRankingTest {
    private fun channel(playlistId: Long, name: String, order: Int) =
        StreamEntity(playlistId, ContentKind.LIVE, "$playlistId-$name", name, order, null, null, "https://example.com/$order")

    @Test
    fun namesStartingWithTheSearchComeFirstThenTheActivePlaylist() {
        // Each playlist's matches arrive in its own order; the active playlist (1) is first.
        val active = listOf(channel(1, "Metro News", 0), channel(1, "City Metro", 1))
        val other = listOf(channel(2, "Metro Sport", 0), channel(2, "Late Metro", 1))
        assertEquals(
            listOf("Metro News", "Metro Sport", "City Metro", "Late Metro"),
            rankByName("metro", listOf(active, other), limit = 10).map { it.name },
        )
        assertEquals(listOf("Metro News", "Metro Sport"), rankByName("metro", listOf(active, other), limit = 2).map { it.name })
    }

    @Test
    fun guideResultsFromEveryPlaylistAreInTheGuidesOrder() {
        val now = 1_000_000L
        fun hit(playlistId: Long, title: String, start: Long, stop: Long) =
            ProgrammeHit(ProgrammeEntity(playlistId = playlistId, channelId = "c", startMs = start, stopMs = stop, title = title, description = null, category = null), channel(playlistId, "C", 0))
        val hits = listOf(
            hit(1, "Past, recent", now - 2_000, now - 1_000),
            hit(1, "Later", now + 5_000, now + 9_000),
            hit(2, "On now", now - 500, now + 500),
            hit(2, "Past, older", now - 9_000, now - 5_000),
            hit(2, "Next", now + 1_000, now + 2_000),
        )
        assertEquals(
            listOf("On now", "Next", "Later", "Past, recent", "Past, older"),
            rankByTime(hits, now, limit = 10).map { it.programme.title },
        )
    }
}
