package app.tellycine.core

import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Wraps [input] in a [GZIPInputStream] when it starts with the gzip magic bytes.
 * Many providers serve `.xml.gz` EPGs or gzip playlists without a Content-Encoding header.
 */
fun InputStream.gunzipIfNeeded(): InputStream {
    val buffered = if (this is BufferedInputStream) this else BufferedInputStream(this, 64 * 1024)
    buffered.mark(2)
    val b1 = buffered.read()
    val b2 = buffered.read()
    buffered.reset()
    return if (b1 == 0x1f && b2 == 0x8b) {
        BufferedInputStream(GZIPInputStream(buffered, 64 * 1024), 64 * 1024)
    } else {
        buffered
    }
}
