package app.tellycine.data.repo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream

/**
 * Playlist files kept in app storage. On TV a picked file is copied here (many TVs have no file
 * picker that grants lasting access), as is a file sent from a phone, so refreshes can re-read it.
 */
object PlaylistFiles {
    private fun dir(context: Context) = File(context.filesDir, "playlists").apply { mkdirs() }

    /** Saves [input] as [name]; returns the file's URI for the playlist. */
    fun save(context: Context, name: String, input: InputStream): String {
        val safe = name.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80).ifBlank { "playlist.m3u" }
        val file = File(dir(context), "${System.currentTimeMillis()}-$safe")
        file.outputStream().use { input.copyTo(it) }
        return Uri.fromFile(file).toString()
    }

    /** Copies a picked document into app storage; returns its URI and display name, or null if it can't be read. */
    fun copy(context: Context, uri: Uri): Pair<String, String>? = runCatching {
        val name = displayName(context, uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "playlist.m3u"
        val saved = context.contentResolver.openInputStream(uri)!!.use { save(context, name, it) }
        saved to name
    }.getOrNull()

    /** Deletes a copy made by [save], leaving any other URI alone. */
    fun delete(context: Context, uri: String) {
        val path = Uri.parse(uri).takeIf { it.scheme == "file" }?.path ?: return
        val file = File(path)
        if (file.parentFile?.canonicalPath == dir(context).canonicalPath) file.delete()
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}
