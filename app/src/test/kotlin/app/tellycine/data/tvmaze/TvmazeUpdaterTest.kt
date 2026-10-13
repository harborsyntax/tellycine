package app.tellycine.data.tvmaze

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.TellycineApp
import app.tellycine.core.ContentKind
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import app.tellycine.data.db.StreamEntity
import app.tellycine.testing.FakeHttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URLDecoder
import java.time.ZoneOffset
import java.util.Collections

/** Looking series up on a pretend TVmaze: what's asked the first time, and how little after that. */
@RunWith(AndroidJUnit4::class)
class TvmazeUpdaterTest {
    private val container = ApplicationProvider.getApplicationContext<TellycineApp>().container
    private val db = container.database
    private val requests: MutableList<String> = Collections.synchronizedList(ArrayList())
    private var changes = "{}"
    private var nextAiredForWho = ""

    private fun show(id: Int, name: String, premiered: String, ended: Boolean, updated: Long, last: String, next: String = "") =
        """{"id":$id,"name":"$name","premiered":"$premiered","status":"${if (ended) "Ended" else "Running"}","updated":$updated,
           "_embedded":{"previousepisode":{"airdate":"$last"}${if (next.isNotEmpty()) ""","nextepisode":{"airdate":"$next"}""" else ""}}}"""

    private val server = FakeHttpServer { path, query ->
        val q = URLDecoder.decode(query, "UTF-8")
        requests += "$path?" + q.split("&").filterNot { it.startsWith("embed[]") }.joinToString("&")
        val name = q.substringAfter("q=", "").substringBefore("&")
        when (path) {
            "/singlesearch/shows" -> when (name) {
                "Breaking Bad" -> show(169, "Breaking Bad", "2008-01-20", true, 100, "2013-09-29")
                // The best match by name is the 1963 series.
                "Doctor Who" -> show(766, "Doctor Who", "1963-11-23", true, 100, "1989-12-06")
                else -> null
            }
            "/search/shows" -> """[{"score":1,"show":${show(766, "Doctor Who", "1963-11-23", true, 100, "1989-12-06")}},
                                   {"score":0.9,"show":${show(210, "Doctor Who", "2005-03-26", false, 100, "2025-05-31")}}]"""
            "/shows/169" -> show(169, "Breaking Bad", "2008-01-20", true, 200, "2013-09-29")
            "/shows/210" -> show(210, "Doctor Who", "2005-03-26", false, 100, "2025-05-31", nextAiredForWho)
            "/updates/shows" -> changes
            else -> null
        }
    }

    private var now = System.currentTimeMillis()
    private val updater = TvmazeUpdater(
        db, container.settings, TvmazeClient(OkHttpClient(), server.url, spacingMs = 0), MainScope(), clock = { now }, zone = ZoneOffset.UTC,
    )

    @After
    fun tearDown() = server.close()

    private fun seed() = runBlocking(Dispatchers.IO) {
        container.settings.setTvmazeCheckedAt(0)
        val playlists = db.playlists()
        val one = playlists.insert(PlaylistEntity(name = "One", type = PlaylistType.XTREAM, url = "https://example.com"))
        val two = playlists.insert(PlaylistEntity(name = "Two", type = PlaylistType.M3U_URL, url = "https://example.com/p.m3u"))
        fun series(pid: Long, id: String, name: String, year: String? = null) =
            StreamEntity(pid, ContentKind.SERIES, id, name, 0, null, null, "", year = year, changedAt = now - 1_000_000)
        db.content().insertStreams(
            listOf(
                series(one, "bb", "EN - Breaking Bad", "2008"),
                series(one, "who", "Doctor Who (2005)"),
                series(one, "zz", "Nobody Has Heard Of This"),
                // The same series in another playlist: one lookup does for both.
                series(two, "bb2", "Breaking Bad (2008) [4K]"),
            )
        )
        one to two
    }

    private fun aired(pid: Long, id: String) = runBlocking(Dispatchers.IO) { db.content().getStream(pid, ContentKind.SERIES, id) }!!.aired

    private fun check(): List<String> {
        requests.clear()
        runBlocking { updater.check() }
        return requests.toList()
    }

    @Test
    fun looksEverySeriesUpOnceThenOnlyWhatChanged() {
        val (one, two) = seed()
        assertEquals(
            listOf(
                "/singlesearch/shows?q=Breaking Bad",
                "/singlesearch/shows?q=Doctor Who",
                // Not the 1963 one: the one from 2005.
                "/search/shows?q=Doctor Who",
                "/shows/210?",
                "/singlesearch/shows?q=Nobody Has Heard Of This",
            ),
            check(),
        )
        assertEquals("2013-09-29", aired(one, "bb"))
        assertEquals("2013-09-29", aired(two, "bb2"))
        assertEquals("2025-05-31", aired(one, "who"))
        assertNull(aired(one, "zz"))
        with(updater.status.value) {
            assertEquals(3, series)
            assertEquals(2, found)
            assertEquals(1, notFound)
            assertEquals(false, running)
        }

        // Straight after: nothing to ask.
        now += 60_000
        assertEquals(emptyList<String>(), check())

        // A day later, TVmaze has changed Breaking Bad: one list of changes, and that show again.
        now += 24 * 60 * 60 * 1000L
        changes = """{"169":200,"210":100}"""
        assertEquals(listOf("/updates/shows?since=week", "/shows/169?"), check())

        // Over a month on (longer than TVmaze's lists go back, so the whole list), the one TVmaze
        // didn't have is tried again.
        now += 31L * 24 * 60 * 60 * 1000
        changes = "{}"
        assertEquals(listOf("/updates/shows?", "/singlesearch/shows?q=Nobody Has Heard Of This"), check())
    }

    @Test
    fun looksAgainOnceTheNextEpisodeHasAired() {
        val (one, _) = seed()
        nextAiredForWho = java.time.Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate().plusDays(2).toString()
        check()
        now += 60 * 60 * 1000L
        assertEquals(listOf("/updates/shows?since=day"), check())
        // Three days later the episode is out: Doctor Who is asked about again.
        now += 3L * 24 * 60 * 60 * 1000
        assertEquals(listOf("/updates/shows?since=week", "/shows/210?"), check())
        assertEquals("2025-05-31", aired(one, "who"))
    }

    @Test
    fun looksAgainWhenASeriesGainsEpisodes() {
        val (one, _) = seed()
        check()
        now += 2 * 60 * 60 * 1000L
        runBlocking(Dispatchers.IO) {
            val bb = db.content().getStream(one, ContentKind.SERIES, "bb")!!
            db.content().insertStreams(listOf(bb.copy(changedAt = now - 1_000)))
        }
        assertEquals(listOf("/updates/shows?since=day", "/shows/169?"), check())
    }
}
