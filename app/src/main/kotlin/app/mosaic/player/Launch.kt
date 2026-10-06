package app.mosaic.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import app.mosaic.container
import app.mosaic.core.ContentKind
import app.mosaic.data.db.HistoryEntity
import app.mosaic.data.net.DEFAULT_USER_AGENT
import kotlinx.coroutines.launch

/**
 * Starts playback with the user's default player: the built-in one, or another app.
 * Requests that need an auth header (Dispatcharr recordings) always use the built-in player.
 */
fun Context.play(request: PlayRequest) {
    val external = container.externalPlayerDefault.value && request.apiKey == null
    if (external && openExternally(request, chooser = false)) return
    startActivity(request.toIntent(this))
}

/**
 * Opens [request] in another video app. With [chooser], Android's app picker is always
 * shown; otherwise the system resolver is used, which lets the user pick an app "Always".
 *
 * @return false if no installed app can play it.
 */
fun Context.openExternally(request: PlayRequest, chooser: Boolean): Boolean {
    val uri = Uri.parse(request.url)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeTypeFor(request.url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Conventions understood by VLC, MX Player, Just Player and others.
        .putExtra("title", listOfNotNull(request.title, request.subtitle).joinToString(" · "))
        .putExtra(Intent.EXTRA_TITLE, request.title)
        .putExtra("headers", headerArray(request))
        .putExtra("android.media.intent.extra.HTTP_HEADERS", Bundle().apply {
            headerPairs(request).forEach { (k, v) -> putString(k, v) }
        })
    if (!request.isLive && request.startPositionMs > 0) {
        intent.putExtra("position", request.startPositionMs.toInt()) // MX Player / VLC (ms)
    }
    val launch = if (chooser) Intent.createChooser(intent, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else intent
    return try {
        startActivity(launch)
        recordExternalPlay(request)
        true
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "No installed app can play this stream", Toast.LENGTH_SHORT).show()
        false
    }
}

/** Shares a URL as plain text through Android's share sheet. */
fun Context.shareUrl(label: String, url: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, label)
        .putExtra(Intent.EXTRA_TEXT, url)
    startActivity(Intent.createChooser(send, "Share stream URL").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun headerPairs(request: PlayRequest): List<Pair<String, String>> = buildList {
    add("User-Agent" to (request.userAgent?.ifBlank { null } ?: DEFAULT_USER_AGENT))
    request.referrer?.ifBlank { null }?.let { add("Referer" to it) }
}

private fun headerArray(request: PlayRequest): Array<String> =
    headerPairs(request).flatMap { listOf(it.first, it.second) }.toTypedArray()

private fun mimeTypeFor(url: String): String {
    val path = Uri.parse(url).path?.lowercase().orEmpty()
    return when {
        path.endsWith(".m3u8") || path.endsWith(".m3u") -> "application/x-mpegURL"
        path.endsWith(".mpd") -> "application/dash+xml"
        path.endsWith(".mkv") -> "video/x-matroska"
        path.endsWith(".mp4") || path.endsWith(".m4v") -> "video/mp4"
        path.endsWith(".ts") -> "video/mp2t"
        else -> "video/*"
    }
}

/** Keeps "recently watched" useful when playback happens in another app. */
private fun Context.recordExternalPlay(request: PlayRequest) {
    if (request.catchup || request.historyKey.isBlank()) return
    val c = container
    c.appScope.launch {
        c.content.touchHistory(
            HistoryEntity(
                playlistId = request.playlistId,
                itemKey = request.historyKey,
                kind = request.kind,
                streamId = request.streamId,
                title = request.title,
                subtitle = if (request.kind == ContentKind.LIVE) null else request.subtitle,
                artwork = request.artwork,
                url = request.url,
                positionMs = 0,
                durationMs = 0,
                lastWatchedAt = System.currentTimeMillis(),
            )
        )
    }
}
