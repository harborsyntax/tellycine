package app.tellycine.data.sync

import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.TellycineApp
import app.tellycine.core.ContentKind
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.db.SyncState
import app.tellycine.testing.FakeHttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Refreshing a TV guide: staged and swapped in, at low priority, and skipped when it hasn't changed. */
@RunWith(AndroidJUnit4::class)
class GuideImportTest {
    private val app = ApplicationProvider.getApplicationContext<TellycineApp>()
    private val db = app.container.database
    private val dao = db.content()
    private val syncer = PlaylistSyncer(app, db, app.container.downloader)

    /** What the server has: a guide, and a playlist with a channel for each id in [channels]. */
    private var guide = ""
    private var channels = listOf<String>()
    private var etag: String? = null
    private var guideRequests = 0
    private val server: FakeHttpServer = FakeHttpServer { path, _ -> answer(path) }

    private fun answer(path: String): String? = when (path) {
        "/guide.xml" -> {
            guideRequests++
            if (etag != null && server.headers.last()["if-none-match"] == etag) throw FakeHttpServer.Status(304)
            guide
        }
        "/list.m3u" -> "#EXTM3U\n" + channels.joinToString("") { "#EXTINF:-1 tvg-id=\"$it\" group-title=\"News\",$it\nhttp://example.com/$it.ts\n" }
        else -> null
    }

    @After
    fun tearDown() = server.close()

    private val start = System.currentTimeMillis() / HOUR * HOUR

    private class Show(val channel: String, val start: Long, val minutes: Int, val title: String)

    private fun xmltv(vararg shows: Show): String = buildString {
        val time = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<tv>\n")
        shows.map { it.channel }.distinct().forEach { append("<channel id=\"$it\"><display-name>$it</display-name></channel>\n") }
        for (show in shows) {
            append("<programme start=\"${time.format(Date(show.start))}\" stop=\"${time.format(Date(show.start + show.minutes * MINUTE))}\" ")
            append("channel=\"${show.channel}\"><title>${show.title}</title></programme>\n")
        }
        append("</tv>\n")
    }

    private fun playlist(vararg channels: String): Long = runBlocking(Dispatchers.IO) {
        val id = db.playlists().insert(
            PlaylistEntity(name = "Guide", type = PlaylistType.M3U_URL, url = "${server.url}/list.m3u", epgUrl = "${server.url}/guide.xml")
        )
        addChannels(id, *channels)
        id
    }

    private fun addChannels(pid: Long, vararg channels: String) = dao.insertStreams(
        channels.map { StreamEntity(pid, ContentKind.LIVE, it, it, 0, null, null, "https://example.com/$it.ts", epgChannelId = it) }
    )

    private fun stored(pid: Long) = runBlocking(Dispatchers.IO) { db.playlists().get(pid)!! }

    private fun refresh(pid: Long, days: Int = 3, report: ProgressReporter = { _, _ -> }): GuideRefresh =
        runBlocking { syncer.syncEpg(stored(pid), days, report) }

    private class Row(val id: Long, val channel: String, val title: String)

    private fun rows(pid: Long): List<Row> = db.openHelper.readableDatabase
        .query("SELECT id, channelId, title FROM programmes WHERE playlistId = ? ORDER BY channelId, startMs", arrayOf(pid))
        .use { c -> generateSequence { if (c.moveToNext()) Row(c.getLong(0), c.getString(1), c.getString(2)) else null }.toList() }

    private fun titles(pid: Long) = rows(pid).map { "${it.channel}: ${it.title}" }

    private fun staged() = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM programmes_staging").use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun aRefreshSwapsInOnlyWhatChanged() {
        val pid = playlist("one", "two", "three", "four")
        guide = xmltv(
            Show("one", start, 60, "News"), Show("one", start + HOUR, 60, "Film"), Show("two", start, 120, "Cartoons"),
            Show("three", start, 180, "Sport"), Show("four", start, 240, "Music"),
        )
        assertEquals(GuideRefresh.Imported(5), refresh(pid))
        val first = rows(pid)
        assertEquals(
            listOf("four: Music", "one: News", "one: Film", "three: Sport", "two: Cartoons"),
            first.map { "${it.channel}: ${it.title}" },
        )
        assertEquals(0, staged())
        with(stored(pid)) {
            assertEquals(5, programmeCount)
            assertFalse(epgTrimmed)
            // Three in four channels have a guide for two hours.
            assertEquals(start + 2 * HOUR, epgCoveredUntil)
        }

        // Later, the film is a director's cut, the cartoons have gone and there's a quiz.
        guide = xmltv(
            Show("one", start, 60, "News"), Show("one", start + HOUR, 60, "Film: Director's Cut"), Show("two", start + HOUR, 30, "Quiz"),
            Show("three", start, 180, "Sport"), Show("four", start, 240, "Music"),
        )
        assertEquals(GuideRefresh.Imported(5), refresh(pid))
        val second = rows(pid)
        assertEquals(
            listOf("four: Music", "one: News", "one: Film: Director's Cut", "three: Sport", "two: Quiz"),
            second.map { "${it.channel}: ${it.title}" },
        )
        // What hadn't changed stayed as it was, rather than being written again.
        assertEquals(first[1].id, second[1].id)
        assertEquals(0, staged())
    }

    @Test
    fun aBigGuideIsReadAtLowPriorityWithoutHoldingTheDatabase() {
        val ids = (1..21).map { "ch$it" }
        val pid = playlist(*ids.toTypedArray())
        // 21,000 programmes over three and a half days.
        guide = xmltv(*ids.flatMap { ch -> (0 until 1000).map { Show(ch, start + it * 5 * MINUTE, 5, "$ch $it") } }.toTypedArray())
        var priority: Int? = null
        var otherWrite: Boolean? = null
        val result = refresh(pid, days = 7) { message, _ ->
            // Part way through reading it: the rest of the app can write meanwhile.
            if (message.startsWith("Importing TV guide… ") && otherWrite == null) {
                priority = Process.getThreadPriority(Process.myTid())
                val wrote = CountDownLatch(1)
                thread { runBlocking { db.playlists().setSyncState(pid, SyncState.SYNCING, null) }; wrote.countDown() }
                otherWrite = wrote.await(5, TimeUnit.SECONDS)
            }
        }
        assertEquals(GuideRefresh.Imported(21_000), result)
        assertEquals(true, otherWrite)
        assertEquals(Process.THREAD_PRIORITY_BACKGROUND, priority)
    }

    @Test
    fun aGuideTheServerSaysHasntChangedIsntDownloadedAgain() {
        val pid = playlist("one")
        guide = xmltv(Show("one", start, 60, "News"))
        server.replyHeaders = mapOf("ETag" to "\"v1\"")
        assertEquals(GuideRefresh.Imported(1), refresh(pid))
        val imported = stored(pid)

        etag = "\"v1\""
        assertEquals(GuideRefresh.Unchanged, refresh(pid))
        assertEquals("\"v1\"", server.headers.last()["if-none-match"])
        with(stored(pid)) {
            assertEquals(imported.epgSyncedAt, epgSyncedAt)
            assertTrue(epgCheckedAt!! >= imported.epgCheckedAt!!)
        }
        assertEquals(listOf("one: News"), titles(pid))
    }

    @Test
    fun aGuideIsComparedWithTheOneImportedBefore() {
        val pid = playlist("one")
        guide = xmltv(Show("one", start, 60, "News"))
        assertEquals(GuideRefresh.Imported(1), refresh(pid))
        assertEquals(GuideRefresh.Unchanged, refresh(pid))

        // Not when it has changed,
        guide = xmltv(Show("one", start, 60, "News"), Show("one", start + HOUR, 60, "Weather"))
        assertEquals(GuideRefresh.Imported(2), refresh(pid))
        assertEquals(GuideRefresh.Unchanged, refresh(pid))
        // or the playlist has a channel it didn't have,
        guide = xmltv(Show("one", start, 60, "News"), Show("one", start + HOUR, 60, "Weather"), Show("two", start, 60, "Quiz"))
        runBlocking(Dispatchers.IO) { addChannels(pid, "two") }
        assertEquals(GuideRefresh.Imported(3), refresh(pid))
        assertEquals(GuideRefresh.Unchanged, refresh(pid))
        // or keeps more days.
        assertEquals(GuideRefresh.Imported(3), refresh(pid, days = 5))
        assertEquals(GuideRefresh.Unchanged, refresh(pid, days = 5))
    }

    @Test
    fun aGuideCutShortIsImportedAgainToReachFurther() {
        val pid = playlist("one")
        guide = xmltv(Show("one", start, 60, "Tonight"), Show("one", start + 5 * DAY, 60, "Next week"))
        assertEquals(GuideRefresh.Imported(1), refresh(pid))
        assertTrue(stored(pid).epgTrimmed)
        // The same guide, but by now more of it is wanted.
        assertEquals(GuideRefresh.Imported(1), refresh(pid))
    }

    @Test
    fun aPlaylistRefreshRefreshesItsGuideOnlyForNewChannels() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val manager = SyncManager(db, syncer, app.container.settings, scope)
        fun sync(pid: Long, epg: Boolean) {
            manager.sync(pid, content = true, epg = epg)
            val until = System.currentTimeMillis() + 30_000
            while (manager.isSyncing(pid) && System.currentTimeMillis() < until) Thread.sleep(20)
            assertEquals(SyncState.IDLE, stored(pid).syncState)
        }
        try {
            channels = listOf("one")
            guide = xmltv(Show("one", start, 60, "News"), Show("two", start, 60, "Quiz"))
            val pid = playlist()
            sync(pid, epg = true)
            assertEquals(1, guideRequests)
            assertEquals(listOf("one: News"), titles(pid))

            // The same channels: the guide waits until it's due.
            sync(pid, epg = false)
            assertEquals(1, guideRequests)
            // A new one: its programmes are wanted now.
            channels = listOf("one", "two")
            sync(pid, epg = false)
            assertEquals(2, guideRequests)
            assertEquals(listOf("one: News", "two: Quiz"), titles(pid))
        } finally {
            scope.cancel()
        }
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
