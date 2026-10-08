package app.tellycine.data.remote

import android.content.Context
import android.net.Uri
import app.tellycine.AppContainer
import app.tellycine.core.xtream.XtreamUrls
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import app.tellycine.data.repo.PlaylistFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Saves the playlist a phone sent from the setup page and starts loading it; returns its name. */
suspend fun addPlaylistFromPhone(context: Context, container: AppContainer, submission: PhoneSubmission): Result<String> = runCatching {
    val fields = submission.fields
    val name = fields["name"].orEmpty().trim()
    val epg = fields["epg"].orEmpty().trim()
    val playlist = when (fields["type"]) {
        "file" -> {
            val bytes = submission.fileBytes ?: error("Choose a playlist file.")
            val fileName = submission.fileName?.substringAfterLast('/')?.substringAfterLast('\\') ?: "playlist.m3u"
            val uri = withContext(Dispatchers.IO) { PlaylistFiles.save(context, fileName, bytes.inputStream()) }
            PlaylistEntity(name = name.ifEmpty { fileName.substringBeforeLast('.').ifEmpty { "Playlist" } }, type = PlaylistType.M3U_FILE, url = uri, epgUrl = epg)
        }
        "m3u" -> {
            val url = fields["url"].orEmpty().trim()
            require(url.matches(Regex("(?i)^https?://.+"))) { "Enter a valid http(s) playlist URL." }
            // An Xtream Codes get.php link works better through the Xtream API (catch-up, movie details).
            val xtream = XtreamUrls.fromM3uUrl(url)
            if (xtream != null) {
                val (server, username, password) = xtream
                val normalised = XtreamUrls.normaliseServer(server)
                PlaylistEntity(
                    name = name.ifEmpty { Uri.parse(normalised).host ?: "Xtream" },
                    type = PlaylistType.XTREAM,
                    url = normalised,
                    username = username,
                    password = password,
                    epgUrl = epg,
                )
            } else {
                PlaylistEntity(name = name.ifEmpty { Uri.parse(url).host ?: "Playlist" }, type = PlaylistType.M3U_URL, url = url, epgUrl = epg)
            }
        }
        else -> {
            val server = fields["server"].orEmpty().trim()
            val username = fields["username"].orEmpty().trim()
            val password = fields["password"].orEmpty()
            require(server.isNotEmpty()) { "Enter the server address." }
            require(username.isNotEmpty()) { "Enter your username." }
            require(password.isNotEmpty()) { "Enter your password." }
            val normalised = XtreamUrls.normaliseServer(server)
            PlaylistEntity(
                name = name.ifEmpty { Uri.parse(normalised).host ?: "Xtream" },
                type = PlaylistType.XTREAM,
                url = normalised,
                username = username,
                password = password,
                epgUrl = epg,
            )
        }
    }
    container.playlists.save(playlist)
    playlist.name
}
