package app.tellycine.data.repo

import app.tellycine.AppContainer
import app.tellycine.core.ContentKind
import app.tellycine.data.db.CategoryEntity
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import app.tellycine.data.db.ProgrammeEntity
import app.tellycine.data.db.StreamEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * A second playlist beside the demo one. Its categories are named like the demo's but with other
 * ids and spelling (" news ", "drama"), and its stream ids repeat the demo's ("ch0", "mv0").
 */
object SecondPlaylist {
    const val NAME = "Second IPTV"

    fun seed(container: AppContainer): Long = runBlocking(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val db = container.database
        val pid = db.playlists().insert(
            PlaylistEntity(
                name = NAME, type = PlaylistType.M3U_URL, url = "https://example.com/second.m3u",
                lastSyncedAt = now, epgSyncedAt = now, liveCount = 2, movieCount = 1,
            )
        )
        val dao = db.content()
        dao.insertCategories(
            listOf(
                CategoryEntity(pid, ContentKind.LIVE, "7", " news ", 0, 1),
                CategoryEntity(pid, ContentKind.LIVE, "8", "Football", 1, 1),
                CategoryEntity(pid, ContentKind.MOVIE, "d", "drama", 0, 1),
            )
        )
        dao.insertStreams(
            listOf(
                StreamEntity(pid, ContentKind.LIVE, "ch0", "Second News HD", 0, "7", null, "https://example.com/second/0.m3u8", epgChannelId = "s-news"),
                StreamEntity(pid, ContentKind.LIVE, "ch1", "Second Football", 1, "8", null, "https://example.com/second/1.m3u8"),
                StreamEntity(pid, ContentKind.MOVIE, "mv0", "Quiet Tide", 0, "d", null, "https://example.com/second/mv0.mp4", year = "2025", genre = "Drama"),
            )
        )
        dao.insertProgrammes(
            listOf(
                ProgrammeEntity(playlistId = pid, channelId = "s-news", startMs = now - 30 * 60_000, stopMs = now + 30 * 60_000, title = "Second Headlines", description = null, category = null),
            )
        )
        pid
    }
}
