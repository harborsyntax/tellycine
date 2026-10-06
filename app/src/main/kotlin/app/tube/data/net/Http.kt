package app.tube.data.net

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
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

class Downloader(private val context: Context, private val client: OkHttpClient) {

    /**
     * Downloads [url] (http(s) or content://) into [dest], reporting progress as
     * (bytesRead, totalBytesOrNull). Cancellation of the calling coroutine aborts the transfer.
     */
    suspend fun download(
        url: String,
        dest: File,
        userAgent: String?,
        onProgress: (Long, Long?) -> Unit = { _, _ -> },
    ) {
        if (url.startsWith("content://") || url.startsWith("file://")) {
            val input = context.contentResolver.openInputStream(Uri.parse(url))
                ?: throw IOException("Unable to open $url")
            input.use { copy(it, dest, null, onProgress) }
            return
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent?.ifBlank { null } ?: DEFAULT_USER_AGENT)
            .build()
        val call = client.newCall(request)
        val response = try {
            call.execute()
        } catch (e: IllegalArgumentException) {
            throw IOException("Invalid URL: $url")
        }
        response.use {
            if (!it.isSuccessful) {
                throw HttpException(it.code, "Server returned HTTP ${it.code}${if (it.code == 401 || it.code == 403) " (check credentials or user agent)" else ""}")
            }
            val body = it.body ?: throw IOException("Empty response")
            val length = body.contentLength().takeIf { len -> len > 0 }
            body.byteStream().use { input -> copy(input, dest, length, onProgress) }
        }
    }

    private suspend fun copy(input: InputStream, dest: File, total: Long?, onProgress: (Long, Long?) -> Unit) {
        val buffer = ByteArray(64 * 1024)
        var read = 0L
        var lastReport = 0L
        dest.outputStream().buffered(256 * 1024).use { out ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                read += n
                if (read - lastReport > 512 * 1024) {
                    lastReport = read
                    onProgress(read, total)
                }
            }
        }
        onProgress(read, total)
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
