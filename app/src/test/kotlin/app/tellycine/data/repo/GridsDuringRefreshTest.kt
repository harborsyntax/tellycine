package app.tellycine.data.repo

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.TellycineApp
import app.tellycine.core.ContentKind
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.db.TellycineDatabase
import app.tellycine.data.settings.VodOrder
import app.tellycine.screenshots.DemoData
import app.tellycine.testing.firstPage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Screens load what's there while a refresh is writing to the database, rather than waiting for it. */
@RunWith(AndroidJUnit4::class)
class GridsDuringRefreshTest {
    private val container = ApplicationProvider.getApplicationContext<TellycineApp>().container
    private val db = container.database
    private val demo = DemoData.seed(container)

    @Before
    fun watching(): Unit = db.watchAllTables() // as the app does as it starts, well before a refresh

    /** Runs [check] while a refresh's import holds the database: one long write transaction. */
    private fun duringRefresh(check: suspend () -> Unit) {
        val writing = CountDownLatch(1)
        val done = CountDownLatch(1)
        val refresh = thread {
            db.runInTransaction {
                db.content().insertStreams(
                    listOf(StreamEntity(demo, ContentKind.MOVIE, "new", "New Film", 99, null, null, "https://example.com/new.mp4"))
                )
                writing.countDown()
                done.await(30, TimeUnit.SECONDS)
            }
        }
        try {
            writing.await()
            runBlocking { withTimeout(5_000) { check() } }
        } finally {
            done.countDown()
            refresh.join()
        }
    }

    @Test
    fun moviesLoadWhileARefreshWrites() = duringRefresh {
        val page = firstPage { container.content.itemPages(listOf(demo), ContentKind.MOVIE, Categories.ALL, VodOrder.DEFAULT) }
        // What was there before the refresh started.
        assertEquals("Velocity", page.data.first().name)
        assertEquals(0, page.data.count { it.streamId == "new" })
    }

    @Test
    fun aScreenFirstToWatchATableLoadsWhileARefreshWrites() = duringRefresh {
        // Nothing else watches watch history here, as on a TV before its Library is opened.
        assertEquals(listOf("mv1"), db.content().observeHistory(demo, listOf(ContentRepository.movieKey("mv1"))).first().map { it.streamId })
    }

    @Test
    fun everyTableIsWatched() {
        val tables = db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            generateSequence { if (c.moveToNext()) c.getString(0) else null }.toSet()
        } - setOf("android_metadata", "room_master_table", "sqlite_sequence", "programmes_staging")
        assertEquals(tables, TellycineDatabase.WATCHED_TABLES.toSet())
    }
}
