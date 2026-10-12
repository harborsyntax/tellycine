package app.tellycine.data.sync

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.TellycineApp
import app.tellycine.core.ContentKind
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

    /** A refresh as the import does one: everything replaced, in one transaction. */
    private fun refresh(pid: Long, now: Long, vararg streams: StreamEntity) = runBlocking(Dispatchers.IO) {
        db.runInTransaction {
            val firstSeen = FirstSeen(db, pid)
            dao.deleteStreams(pid)
            dao.insertStreams(streams.toList())
            firstSeen.restore(now)
        }
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
}
