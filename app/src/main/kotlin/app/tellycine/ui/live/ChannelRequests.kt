package app.tellycine.ui.live

import app.tellycine.core.ContentKind
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.ProgrammeEntity
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.repo.CatchupResolver
import app.tellycine.data.repo.ContentRepository
import app.tellycine.player.PlayRequest
import app.tellycine.ui.components.formatDay
import app.tellycine.ui.components.formatTime

/** Play request for a channel's live stream. */
fun liveRequest(
    playlist: PlaylistEntity?,
    stream: StreamEntity,
    subtitle: String? = null,
    categoryId: String? = null,
) = PlayRequest(
    playlistId = stream.playlistId,
    kind = ContentKind.LIVE,
    streamId = stream.streamId,
    historyKey = ContentRepository.liveKey(stream.streamId),
    url = stream.url,
    title = stream.name,
    subtitle = subtitle,
    artwork = stream.logo,
    userAgent = stream.userAgent ?: playlist?.userAgent,
    referrer = stream.referrer,
    categoryId = categoryId,
)

/** Play request for a past (or in-progress, from the start) programme, or null if unavailable. */
fun replayRequest(playlist: PlaylistEntity?, stream: StreamEntity, programme: ProgrammeEntity): PlayRequest? {
    val p = playlist ?: return null
    if (!CatchupResolver.isReplayable(p, stream, programme)) return null
    val url = CatchupResolver.url(p, stream, programme) ?: return null
    return PlayRequest(
        playlistId = stream.playlistId,
        kind = ContentKind.LIVE,
        streamId = stream.streamId,
        historyKey = ContentRepository.liveKey(stream.streamId),
        url = url,
        title = programme.title,
        subtitle = "${stream.name} · ${formatDay(programme.startMs)} ${formatTime(programme.startMs)}",
        artwork = stream.logo,
        userAgent = stream.userAgent ?: p.userAgent,
        referrer = stream.referrer,
        catchup = true,
    )
}
