package app.tellycine.data.sports

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.TellycineApp
import app.tellycine.core.ContentKind
import app.tellycine.core.sports.AiringKind
import app.tellycine.core.sports.Sport
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import app.tellycine.data.db.ProgrammeEntity
import app.tellycine.data.db.SportAiringEntity
import app.tellycine.data.db.StreamEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Finding sport in a guide, all of it or only what a refresh added, and the same either way. */
@RunWith(AndroidJUnit4::class)
class SportsIndexerTest {
    private val app = ApplicationProvider.getApplicationContext<TellycineApp>()
    private val db = app.container.database
    private val indexer = app.container.sportsIndexer
    private val now = System.currentTimeMillis() / HOUR * HOUR

    private fun programme(pid: Long, channel: String, startHours: Int, title: String, category: String? = null) =
        ProgrammeEntity(playlistId = pid, channelId = channel, startMs = now + startHours * HOUR, stopMs = now + (startHours + 2) * HOUR, title = title, description = null, category = category)

    private fun airings(pid: Long): List<SportAiringEntity> = runBlocking(Dispatchers.IO) {
        db.openHelper.readableDatabase.query("SELECT channelId, startMs, title, kind FROM sport_airings WHERE playlistId = $pid ORDER BY channelId, startMs").use { c ->
            generateSequence { if (c.moveToNext()) c else null }.map {
                SportAiringEntity(pid, it.getString(0), it.getLong(1), 0, Sport.OTHER, AiringKind.valueOf(it.getString(3)), it.getString(2), null, null, null, null, "", null, null)
            }.toList()
        }
    }

    @Test
    fun aRefreshAddsWhatsNewAndDropsWhatsGone() = runBlocking(Dispatchers.IO) {
        val pid = db.playlists().insert(PlaylistEntity(name = "Sport", type = PlaylistType.M3U_URL, url = "x"))
        db.content().insertStreams(listOf(
            StreamEntity(pid, ContentKind.LIVE, "1", "Sky Sports Main Event", 0, null, null, "https://e/1.ts", epgChannelId = "main"),
            StreamEntity(pid, ContentKind.LIVE, "2", "BBC One", 1, null, null, "https://e/2.ts", epgChannelId = "bbc1"),
            StreamEntity(pid, ContentKind.LIVE, "3", "EPL 03: Arsenal v Chelsea 15:00", 2, null, null, "https://e/3.ts"),
        ))
        db.content().insertProgrammes(listOf(
            programme(pid, "main", 0, "Live Premier League: Arsenal v Chelsea"),
            programme(pid, "main", 2, "Live Darts: Grand Slam of Darts"),
            programme(pid, "bbc1", 0, "EastEnders", "Soap"),
            programme(pid, "bbc1", 2, "Match of the Day", "Sport"),
        ))
        indexer.index(pid)
        assertEquals(listOf("Arsenal v Chelsea", "Grand Slam of Darts"), airings(pid).filter { it.channelId == "main" }.map { it.title })
        assertTrue(airings(pid).any { it.channelId == "#3" })
        // Highlights are kept, to show when asked for; soaps aren't.
        assertEquals(listOf("Match of the Day"), airings(pid).filter { it.channelId == "bbc1" }.map { it.title })

        // A refresh: the darts became snooker, and there's a match tomorrow.
        db.openHelper.writableDatabase.execSQL("DELETE FROM programmes WHERE playlistId = $pid AND title LIKE '%Darts%'")
        db.content().insertProgrammes(listOf(
            programme(pid, "main", 2, "Live Snooker: UK Championship"),
            programme(pid, "main", 24, "Live Premier League: Chelsea v Spurs"),
        ))
        indexer.index(pid, full = false)
        val refreshed = airings(pid)
        assertEquals(listOf("Arsenal v Chelsea", "UK Championship", "Chelsea v Spurs"), refreshed.filter { it.channelId == "main" }.map { it.title })

        // The same as looking through all of it.
        indexer.index(pid, full = true)
        assertEquals(refreshed, airings(pid))
    }

    private companion object {
        const val HOUR = 60 * 60 * 1000L
    }
}
