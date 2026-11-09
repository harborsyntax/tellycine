package app.tellycine.data.net

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

const val DEFAULT_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

fun createHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(90, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .retryOnConnectionFailure(true)
    .build()

class HttpException(val code: Int, message: String) : IOException(message)

/** What a server said identifies the version of a file it sent (its ETag and Last-Modified), if anything. */
data class FileVersion(val etag: String?, val lastModified: String?)

/** A file [Downloader.download] saved: its [version], and the SHA-256 of what was saved. */
data class Downloaded(val version: FileVersion, val sha256: String)

class Downloader(private val context: Context, private val client: OkHttpClient) {

    /**
     * Downloads [url] (http(s) or content://) into [dest], reporting progress as
     * (bytesRead, totalBytesOrNull). Cancellation of the calling coroutine aborts the transfer.
     *
     * With [since], the server is asked whether the file has changed since that version: if it
     * says it hasn't, nothing is written and this returns null.
     */
    suspend fun download(
        url: String,
        dest: File,
        userAgent: String?,
        since: FileVersion? = null,
        onProgress: (Long, Long?) -> Unit = { _, _ -> },
    ): Downloaded? {
        if (url.startsWith("content://") || url.startsWith("file://")) {
            val input = context.contentResolver.openInputStream(Uri.parse(url))
                ?: throw IOException("Unable to open $url")
            return Downloaded(FileVersion(null, null), input.use { copy(it, dest, null, onProgress) })
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent?.ifBlank { null } ?: DEFAULT_USER_AGENT)
            .apply {
                since?.etag?.let { header("If-None-Match", it) }
                since?.lastModified?.let { header("If-Modified-Since", it) }
            }
            .build()
        val call = client.newCall(request)
        val response = try {
            call.execute()
        } catch (e: IllegalArgumentException) {
            throw IOException("Invalid URL: $url")
        }
        response.use {
            if (it.code == 304 && since != null) return null
            if (!it.isSuccessful) {
                throw HttpException(it.code, "Server returned HTTP ${it.code}${if (it.code == 401 || it.code == 403) " (check credentials or user agent)" else ""}")
            }
            val body = it.body ?: throw IOException("Empty response")
            val length = body.contentLength().takeIf { len -> len > 0 }
            val sha256 = body.byteStream().use { input -> copy(input, dest, length, onProgress) }
            return Downloaded(FileVersion(it.header("ETag"), it.header("Last-Modified")), sha256)
        }
    }

    /** Copies [input] into [dest], and returns the SHA-256 of what was copied. */
    private suspend fun copy(input: InputStream, dest: File, total: Long?, onProgress: (Long, Long?) -> Unit): String {
        val buffer = ByteArray(64 * 1024)
        val digest = MessageDigest.getInstance("SHA-256")
        var read = 0L
        var lastReport = 0L
        dest.outputStream().buffered(256 * 1024).use { out ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                digest.update(buffer, 0, n)
                read += n
                if (read - lastReport > 512 * 1024) {
                    lastReport = read
                    onProgress(read, total)
                }
            }
        }
        onProgress(read, total)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Small GET returning the body as a string (used for Xtream detail calls). */
    fun getString(url: String, userAgent: String?): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent?.ifBlank { null } ?: DEFAULT_USER_AGENT)
            .build()
        client.newCall(request).execute().use {
            if (!it.isSuccessful) throw HttpException(it.code, "Server returned HTTP ${it.code}")
            return it.body?.string() ?: ""
        }
    }
}
