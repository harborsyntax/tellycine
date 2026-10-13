package app.tellycine.data.tmdb

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.TellycineApp
import app.tellycine.core.ContentKind
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.settings.SettingsRepository
import app.tellycine.testing.FakeHttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URLDecoder
import java.time.Instant
import java.time.ZoneOffset
import java.util.Collections
import java.util.Locale

/** Looking titles up on a pretend TMDB: what's asked the first time, and how little after that. */
@RunWith(AndroidJUnit4::class)
class TmdbUpdaterTest {
    private val container = ApplicationProvider.getApplicationContext<TellycineApp>().container
    private val db = container.database
    private val requests: MutableList<String> = Collections.synchronizedList(ArrayList())
    private var whoNextAirs: String? = null
    private var busyFor = 0
    private val rejectedKeys = Collections.synchronizedSet(mutableSetOf("rejected-key-000000000000000000"))
    private val keysUsed: MutableList<String?> = Collections.synchronizedList(ArrayList())

    private fun series(id: Int, name: String, date: String) =
        """{"id":$id,"name":"$name","first_air_date":"$date","vote_average":8.0,"vote_count":100,"poster_path":"/$id.jpg"}"""

    private fun film(id: Int, title: String, date: String, votes: Int = 100) =
        """{"id":$id,"title":"$title","release_date":"$date","vote_average":7.5,"vote_count":$votes,"poster_path":"/$id.jpg"}"""

    private fun results(vararg titles: String) = """{"page":1,"results":[${titles.joinToString(",")}]}"""

    private val server = FakeHttpServer { path, query ->
        val params = query.split("&").filter { it.isNotEmpty() }
            .associate { URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        keysUsed += params["api_key"]
        if (params["api_key"] in rejectedKeys) throw FakeHttpServer.Status(401)
        if (busyFor > 0) {
            busyFor--
            throw FakeHttpServer.Status(429)
        }
        requests += "$path?" + params.filterKeys { it in setOf("query", "year", "first_air_date_year") }
            .map { (k, v) -> "$k=$v" }.joinToString("&")
        val q = params["query"]
        when (path) {
            "/authentication" -> """{"success":true}"""
            "/search/tv" -> when (q) {
                "Breaking Bad" -> results(series(1396, "Breaking Bad", "2008-01-20"))
                // Both, the 1963 one first: the year picks the 2005 one.
                "Doctor Who" -> results(series(121, "Doctor Who", "1963-11-23"), series(57243, "Doctor Who", "2005-03-26"))
                else -> results()
            }
            "/search/movie" -> when {
                // The latest Heat by name; the 1995 one only when asked with the year.
                q == "Heat" && params["year"] == "1995" -> results(film(949, "Heat", "1995-12-15"))
                q == "Heat" -> results(film(1111, "Heat", "2013-02-01"))
                q == "Tiny Film" -> results(film(5555, "Tiny Film", "2021-06-01", votes = 2))
                else -> results()
            }
            "/tv/1396" -> """{"id":1396,"name":"Breaking Bad","first_air_date":"2008-01-20","status":"Ended","vote_average":8.9,"vote_count":15000,
                "poster_path":"/bb.jpg","last_episode_to_air":{"season_number":5,"episode_number":16,"air_date":"2013-09-29"}}"""
            "/tv/57243" -> """{"id":57243,"name":"Doctor Who","first_air_date":"2005-03-26","status":"Returning Series","vote_average":7.4,"vote_count":3000,
                "poster_path":"/who.jpg","last_episode_to_air":{"season_number":14,"episode_number":8,"air_date":"2025-05-31"}
                ${whoNextAirs?.let { ""","next_episode_to_air":{"season_number":15,"episode_number":1,"air_date":"$it"}""" } ?: ""}}"""
            "/movie/27205" -> """{"id":27205,"title":"Inception","release_date":"2010-07-15","vote_average":8.4,"vote_count":37000,"poster_path":"/inc.jpg"}"""
            "/movie/157336" -> """{"id":157336,"title":"Interstellar","release_date":"2014-11-05","vote_average":8.5,"vote_count":36000,"poster_path":"/int.jpg"}"""
            else -> null
        }
    }

    private var now = System.currentTimeMillis()
    private val client = TmdbClient(OkHttpClient(), server.url, spacingMs = 0, busyWaitMs = 10, locale = { Locale.UK })
    private val updater = TmdbUpdater(db, container.settings, client, MainScope(), clock = { now }, zone = ZoneOffset.UTC)

    @Before
    fun turnOn(): Unit = runBlocking {
        // The app's own updater starts when this is turned on: somewhere it can't reach.
        container.tmdb.client.baseUrl = "http://127.0.0.1:9"
        container.settings.setTmdbApiKey(KEY)
        container.settings.setTmdbEnabled(true)
    }

    @After
    fun tearDown(): Unit = runBlocking {
        // Settings outlive a test.
        container.settings.setTmdbApiKey("")
        server.close()
    }

    private fun seed() = runBlocking(Dispatchers.IO) {
        val playlists = db.playlists()
        val one = playlists.insert(PlaylistEntity(name = "One", type = PlaylistType.XTREAM, url = "https://example.com"))
        val two = playlists.insert(PlaylistEntity(name = "Two", type = PlaylistType.M3U_URL, url = "https://example.com/p.m3u"))
        fun series(pid: Long, id: String, name: String, year: String? = null) =
            StreamEntity(pid, ContentKind.SERIES, id, name, 0, null, null, "", year = year, changedAt = now - 1_000_000)
        fun movie(pid: Long, id: String, name: String, year: String? = null, tmdbId: Int? = null) =
            StreamEntity(pid, ContentKind.MOVIE, id, name, 0, null, null, "https://example.com/$id.mp4", year = year, tmdbId = tmdbId)
        db.content().insertStreams(
            listOf(
                series(one, "bb", "EN - Breaking Bad", "2008"),
                series(one, "who", "Doctor Who (2005)"),
                series(one, "zz", "Nobody Has Heard Of This"),
                // The same series in another playlist: one lookup does for both.
                series(two, "bb2", "Breaking Bad (2008) [4K]"),
                movie(one, "heat", "Heat", "1995"),
                movie(one, "tiny", "Tiny Film (2021)"),
                // The provider says which film it is: no searching.
                movie(one, "inc", "Inception", "2010", tmdbId = 27205),
            )
        )
        one to two
    }

    private fun stream(pid: Long, kind: ContentKind, id: String) = runBlocking(Dispatchers.IO) { db.content().getStream(pid, kind, id) }!!

    private fun check(): List<String> {
        requests.clear()
        runBlocking { updater.check() }
        return requests.sorted()
    }

    @Test
    fun looksEveryTitleUpOnceThenOnlyWhatsDue() {
        val (one, two) = seed()
        assertEquals(
            listOf(
                "/movie/27205?",
                "/search/movie?query=Heat",
                // The 2013 one isn't it: asked again with the year.
                "/search/movie?query=Heat&year=1995",
                "/search/movie?query=Tiny Film",
                "/search/tv?query=Breaking Bad",
                "/search/tv?query=Doctor Who",
                "/search/tv?query=Nobody Has Heard Of This",
                // Films found by searching need nothing more; series need their pages for air dates.
                "/tv/1396?",
                "/tv/57243?",
            ),
            check(),
        )
        with(stream(one, ContentKind.SERIES, "bb")) {
            assertEquals(1396, tmdbId)
            assertEquals(8.9, tmdbRating)
            assertEquals("https://image.tmdb.org/t/p/w342/bb.jpg", tmdbPoster)
            assertEquals("2013-09-29", aired)
        }
        assertEquals(1396, stream(two, ContentKind.SERIES, "bb2").tmdbId)
        assertEquals("2013-09-29", stream(two, ContentKind.SERIES, "bb2").aired)
        assertEquals(57243, stream(one, ContentKind.SERIES, "who").tmdbId)
        assertEquals("2025-05-31", stream(one, ContentKind.SERIES, "who").aired)
        assertNull(stream(one, ContentKind.SERIES, "zz").tmdbId)
        with(stream(one, ContentKind.MOVIE, "heat")) {
            assertEquals(949, tmdbId)
            assertEquals(7.5, tmdbRating)
            // Films have no air dates.
            assertNull(aired)
        }
        // Two votes: found, but its score doesn't count.
        assertEquals(5555, stream(one, ContentKind.MOVIE, "tiny").tmdbId)
        assertNull(stream(one, ContentKind.MOVIE, "tiny").tmdbRating)
        assertEquals(8.4, stream(one, ContentKind.MOVIE, "inc").tmdbRating)
        with(updater.status.value) {
            assertEquals(6, titles)
            assertEquals(5, found)
            assertEquals(1, notFound)
            assertEquals(false, running)
            assertNull(error)
        }

        // Straight after: nothing to ask.
        now += 60_000
        assertEquals(emptyList<String>(), check())

        // Over a week on, the running series is asked about again.
        now += 8 * DAY
        assertEquals(listOf("/tv/57243?"), check())

        // Over a month on, the one TMDB didn't have is tried again too.
        now += 23 * DAY
        assertEquals(listOf("/search/tv?query=Nobody Has Heard Of This", "/tv/57243?"), check())

        // The provider now says Inception is another film: that one's asked for.
        runBlocking(Dispatchers.IO) {
            db.content().insertStreams(listOf(stream(one, ContentKind.MOVIE, "inc").copy(tmdbId = 157336)))
        }
        assertEquals(listOf("/movie/157336?"), check())
        assertEquals(8.5, stream(one, ContentKind.MOVIE, "inc").tmdbRating)
    }

    @Test
    fun looksAgainOnceTheNextEpisodeHasAired() {
        val (one, _) = seed()
        whoNextAirs = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate().plusDays(2).toString()
        check()
        now += DAY
        assertEquals(emptyList<String>(), check())
        // Three days later the episode is out: Doctor Who is asked about again.
        now += 2 * DAY
        whoNextAirs = null
        assertEquals(listOf("/tv/57243?"), check())
        assertEquals("2025-05-31", stream(one, ContentKind.SERIES, "who").aired)
    }

    @Test
    fun looksAgainWhenASeriesGainsEpisodes() {
        val (one, _) = seed()
        check()
        now += 2 * 60 * 60 * 1000L
        runBlocking(Dispatchers.IO) {
            db.content().insertStreams(listOf(stream(one, ContentKind.SERIES, "who").copy(changedAt = now - 1_000)))
        }
        assertEquals(listOf("/tv/57243?"), check())
        // A finished series isn't, as it won't have new episodes on TMDB.
        now += 60_000
        runBlocking(Dispatchers.IO) {
            db.content().insertStreams(listOf(stream(one, ContentKind.SERIES, "bb").copy(changedAt = now - 1_000)))
        }
        assertEquals(emptyList<String>(), check())
    }

    @Test
    fun aRejectedKeyStopsTheCheck() {
        val (one, _) = seed()
        runBlocking { container.settings.setTmdbApiKey("rejected-key-000000000000000000") }
        check()
        assertEquals(TmdbUpdater.KEY_REJECTED, updater.status.value.error)
        assertNull(stream(one, ContentKind.SERIES, "bb").tmdbId)
    }

    @Test
    fun waitsWhenTmdbIsBusy() {
        val (one, _) = seed()
        // Too many requests twice over: each is tried again after a wait.
        busyFor = 2
        check()
        assertNull(updater.status.value.error)
        assertEquals(1396, stream(one, ContentKind.SERIES, "bb").tmdbId)
    }

    @Test
    fun turnedOffChecksNothing() {
        seed()
        runBlocking { container.settings.setTmdbEnabled(false) }
        assertEquals(emptyList<String>(), check())
    }

    @Test
    fun turningItOffLeavesWhatThePlaylistsSay() {
        val (one, _) = seed()
        check()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val running = TmdbUpdater(db, container.settings, client, scope, clock = { now }, zone = ZoneOffset.UTC)
            running.start()
            // Counted, so it has seen it's on (and waits a little, as at launch, before looking anything up).
            eventually { running.status.value.titles == 6 }
            runBlocking { container.settings.setTmdbEnabled(false) }
            eventually { stream(one, ContentKind.SERIES, "bb").tmdbRating == null }
            with(stream(one, ContentKind.SERIES, "bb")) {
                assertNull(tmdbPoster)
                assertNull(aired)
            }
        } finally {
            scope.cancel()
        }
    }

    private fun eventually(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "Timed out" }
            Thread.sleep(20)
        }
    }

    @Test
    fun tellycinesOwnKeyUnlessTheirOwnIsSet() {
        val (one, _) = seed()
        // The same settings, in a build with Tellycine's own key.
        val settings = SettingsRepository(ApplicationProvider.getApplicationContext(), builtInTmdbKey = BUILT_IN)
        val updater = TmdbUpdater(db, settings, client, MainScope(), clock = { now }, zone = ZoneOffset.UTC)
        fun check(): Set<String?> {
            keysUsed.clear()
            runBlocking { updater.check() }
            return keysUsed.toSet()
        }

        // Without a key of their own, it stays on, with Tellycine's.
        runBlocking { settings.setTmdbApiKey("") }
        assertEquals(true, runBlocking { settings.current().tmdb })
        assertEquals(setOf(BUILT_IN), check())
        assertEquals(1396, stream(one, ContentKind.SERIES, "bb").tmdbId)

        // Theirs, once they add one.
        runBlocking { settings.setTmdbApiKey(KEY) }
        now += 8 * DAY
        assertEquals(setOf(KEY), check())

        // TMDB stops accepting Tellycine's: it says so, and what they can do.
        runBlocking { settings.setTmdbApiKey("") }
        rejectedKeys += BUILT_IN
        now += 8 * DAY
        check()
        assertEquals(TmdbUpdater.BUILT_IN_KEY_REJECTED, updater.status.value.error)
    }

    @Test
    fun keysAndTokensAreSentTheirOwnWays(): Unit = runBlocking {
        assertEquals(TmdbClient.KeyCheck.WORKS, client.checkKey(KEY))
        assertEquals(TmdbClient.KeyCheck.REJECTED, client.checkKey("rejected-key-000000000000000000"))
        val token = "eyJhbGciOiJIUzI1NiJ9." + "a".repeat(60)
        server.headers.clear()
        requests.clear()
        assertEquals(TmdbClient.KeyCheck.WORKS, client.checkKey(token))
        // A read access token goes in a header, not the address.
        assertEquals("Bearer $token", server.headers.single()["authorization"])
        assertEquals(listOf("/authentication?"), requests)
        server.close()
        assertEquals(TmdbClient.KeyCheck.UNREACHABLE, client.checkKey(KEY))
    }

    private companion object {
        const val KEY = "0123456789abcdef0123456789abcdef"
        const val BUILT_IN = "fedcba9876543210fedcba9876543210"
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
