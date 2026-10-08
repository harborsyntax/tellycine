package app.tellycine.data.repo

import app.tellycine.core.Catchup
import app.tellycine.core.xtream.XtreamUrls
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import app.tellycine.data.db.ProgrammeEntity
import app.tellycine.data.db.StreamEntity

/** Decides whether a guide entry can be replayed and builds its archive URL. */
object CatchupResolver {
    private const val DAY = 24 * 60 * 60 * 1000L

    /** True when the channel offers an archive the app knows how to request. */
    fun supports(playlist: PlaylistEntity?, stream: StreamEntity): Boolean {
        if (playlist == null || (stream.catchupDays ?: 0) <= 0) return false
        return when (playlist.type) {
            PlaylistType.XTREAM -> true
            else -> stream.catchupType != null || stream.catchupSource != null
        }
    }

    /** Whether [programme] has started and is still inside the channel's archive window. */
    fun isReplayable(playlist: PlaylistEntity?, stream: StreamEntity, programme: ProgrammeEntity, now: Long = System.currentTimeMillis()): Boolean {
        if (!supports(playlist, stream)) return false
        val days = stream.catchupDays ?: 0
        return programme.startMs < now && programme.startMs >= now - days * DAY
    }

    fun url(playlist: PlaylistEntity, stream: StreamEntity, programme: ProgrammeEntity, now: Long = System.currentTimeMillis()): String? {
        // An in-progress programme is requested up to "now" so the server has the data.
        val stop = programme.stopMs.coerceAtMost(now).coerceAtLeast(programme.startMs + 60_000)
        return when (playlist.type) {
            PlaylistType.XTREAM -> Catchup.xtream(
                urls = XtreamUrls(playlist.url, playlist.username, playlist.password),
                streamId = stream.streamId,
                startMs = programme.startMs,
                stopMs = programme.stopMs,
                serverZone = Catchup.zoneOrUtc(playlist.serverTimezone),
            )
            else -> Catchup.m3u(stream.url, stream.catchupType, stream.catchupSource, programme.startMs, stop, now)
        }
    }
}
