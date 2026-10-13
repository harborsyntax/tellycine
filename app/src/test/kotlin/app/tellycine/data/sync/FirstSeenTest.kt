package app.tellycine.data.sync

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.TellycineApp
import app.tellycine.core.ContentKind
import app.tellycine.data.db.EpisodeEntity
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import app.tellycine.data.db.StreamEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirstSeenTest {
    private val db = ApplicationProvider.getApplicationContext<TellycineApp>().container.database
    private val dao = db.content()

    private fun movie(pid: Long, id: String, added: Long? = null) =
        StreamEntity(pid, ContentKind.MOVIE, id, id, 0, null, null, "https://example.com/$id.mp4", addedAt = added)

    private fun series(pid: Long, id: String, updated: Long? = null) =
        StreamEntity(pid, ContentKind.SERIES, id, id, 0, null, null, "", updatedAt = updated)

    private fun episodes(pid: Long, seriesId: String, count: Int) = (1..count).map {
        EpisodeEntity(pid, "$seriesId-$it", seriesId, 1, it, "$seriesId $it", "https://example.com/$seriesId/$it.mp4", null)
    }

    /** A refresh as the import does one: everything replaced, in one transaction. */
    private fun refresh(pid: Long, now: Long, vararg streams: StreamEntity) = refresh(pid, now, false, emptyList(), *streams)

    private fun refresh(pid: Long, now: Long, countsEpisodes: Boolean, episodes: List<EpisodeEntity>, vararg streams: StreamEntity) =
        runBlocking(Dispatchers.IO) {
            db.runInTransaction {
                val firstSeen = FirstSeen(db, pid, countsEpisodes)
                dao.deleteStreams(pid)
                dao.deleteEpisodes(pid)
                dao.insertStreams(streams.toList())
                dao.insertEpisodes(episodes)
                firstSeen.restore(now)
            }
        }

    private fun stream(pid: Long, kind: ContentKind, id: String) = runBlocking(Dispatchers.IO) { dao.getStream(pid, kind, id) }!!

    private fun playlist(type: PlaylistType) = runBlocking(Dispatchers.IO) {
        db.playlists().insert(PlaylistEntity(name = type.name, type = type, url = "https://example.com/p"))
    }

    private fun added(pid: Long, kind: ContentKind, id: String) = runBlocking(Dispatchers.IO) { dao.getStream(pid, kind, id) }!!.addedAt

    @Test
    fun titlesKeepTheDateTheyFirstAppearedAcrossRefreshes() {
        val pid = runBlocking(Dispatchers.IO) {
            db.playlists().insert(PlaylistEntity(name = "M3U", type = PlaylistType.M3U_URL, url = "https://example.com/p.m3u"))
        }
        val channel = StreamEntity(pid, ContentKind.LIVE, "c1", "Channel", 0, null, null, "https://example.com/c1.ts")
        refresh(pid, now = 1_000, movie(pid, "old"), channel)
        assertEquals(1_000L, added(pid, ContentKind.MOVIE, "old"))

        // Later, "old" is still there and two more have arrived, one with its own date from the provider.
        refresh(pid, now = 2_000, movie(pid, "old"), movie(pid, "new"), movie(pid, "dated", added = 555), channel)
        assertEquals(1_000L, added(pid, ContentKind.MOVIE, "old"))
        assertEquals(2_000L, added(pid, ContentKind.MOVIE, "new"))
        assertEquals(555L, added(pid, ContentKind.MOVIE, "dated"))
        // Live channels aren't sorted by date.
        assertNull(added(pid, ContentKind.LIVE, "c1"))
    }

    @Test
    fun m3uSeriesChangeWhenTheyGainEpisodes() {
        val pid = playlist(PlaylistType.M3U_URL)
        refresh(pid, 1_000, true, episodes(pid, "a", 3) + episodes(pid, "b", 2), series(pid, "a"), series(pid, "b"))
        // New series: when they were first seen.
        assertEquals(1_000L, stream(pid, ContentKind.SERIES, "a").changedAt)

        // "a" gains an episode; "b" doesn't; "c" is new.
        refresh(pid, 2_000, true, episodes(pid, "a", 4) + episodes(pid, "b", 2) + episodes(pid, "c", 1), series(pid, "a"), series(pid, "b"), series(pid, "c"))
        assertEquals(2_000L, stream(pid, ContentKind.SERIES, "a").changedAt)
        assertEquals(1_000L, stream(pid, ContentKind.SERIES, "b").changedAt)
        assertEquals(2_000L, stream(pid, ContentKind.SERIES, "c").changedAt)

        // Losing one isn't news.
        refresh(pid, 3_000, true, episodes(pid, "a", 3) + episodes(pid, "b", 2), series(pid, "a"), series(pid, "b"))
        assertEquals(2_000L, stream(pid, ContentKind.SERIES, "a").changedAt)
    }

    @Test
    fun xtreamSeriesUseTheProvidersDate() {
        val pid = playlist(PlaylistType.XTREAM)
        refresh(pid, 1_000, series(pid, "dated", updated = 500), series(pid, "undated"))
        assertEquals(500L, stream(pid, ContentKind.SERIES, "dated").changedAt)
        assertEquals(1_000L, stream(pid, ContentKind.SERIES, "undated").changedAt)
        refresh(pid, 2_000, series(pid, "dated", updated = 1_500), series(pid, "undated"))
        assertEquals(1_500L, stream(pid, ContentKind.SERIES, "dated").changedAt)
        assertEquals(1_000L, stream(pid, ContentKind.SERIES, "undated").changedAt)
    }

    @Test
    fun airDatesFromTvmazeLastAcrossRefreshes() {
        val pid = playlist(PlaylistType.XTREAM)
        refresh(pid, 1_000, series(pid, "s"))
        runBlocking(Dispatchers.IO) { db.tvmaze().setAired(pid, "s", "2025-05-01") }
        refresh(pid, 2_000, series(pid, "s"))
        assertEquals("2025-05-01", stream(pid, ContentKind.SERIES, "s").aired)
    }
}
