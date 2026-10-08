package app.tellycine.data.remote

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.concurrent.Executors

/** An uploaded file, kept as a slice of the request body rather than a second copy of it. */
class UploadedFile(val name: String, private val data: ByteArray, private val offset: Int, val size: Int) {
    fun open(): InputStream = ByteArrayInputStream(data, offset, size)
    fun bytes(): ByteArray = data.copyOfRange(offset, offset + size)
}

/** What a phone sent from the setup page: form fields, and a playlist file if one was attached. */
class PhoneSubmission(val fields: Map<String, String>, val file: UploadedFile?)

/**
 * A tiny web server, running only while the TV's "add from phone" screen is open, that serves a
 * playlist form to phones on the same network. Requests must carry the random [token] from the
 * address shown on the TV.
 */
class PhoneSetupServer(private val onSubmit: suspend (PhoneSubmission) -> Result<String>) {
    val token: String = buildString {
        val random = SecureRandom()
        repeat(8) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }
    private val lock = Any()
    private var server: ServerSocket? = null
    private var job: Job? = null
    private var stopped = false

    /** A few threads of its own, so slow or idle connections can't hold up the rest of the app. */
    private val executor = Executors.newFixedThreadPool(MAX_CONNECTIONS)
    private val dispatcher = executor.asCoroutineDispatcher()

    /** Starts listening; returns the address to open on the phone, or null without a local network. */
    fun start(scope: CoroutineScope, host: String? = lanAddress()): String? {
        if (host == null) return null
        val socket = ServerSocket(0)
        synchronized(lock) {
            // The screen may have closed while the socket was opening.
            if (stopped) {
                socket.close()
                return null
            }
            server = socket
            job = scope.launch(Dispatchers.IO) {
                while (isActive && !socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    launch(dispatcher) { client.use { handle(it) } }
                }
            }
        }
        return "http://$host:${socket.localPort}/?t=$token"
    }

    fun stop() {
        synchronized(lock) {
            stopped = true
            runCatching { server?.close() }
            job?.cancel()
        }
        executor.shutdownNow()
    }

    private suspend fun handle(client: Socket) {
        try {
            client.soTimeout = 10_000
            val input = BufferedInputStream(client.getInputStream())
            val head = readHead(input, deadline = System.nanoTime() + HEAD_TIMEOUT_NS) ?: return
            val lines = head.split("\r\n")
            val (method, target) = lines.first().split(" ").let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "/") }
            val headers = lines.drop(1).mapNotNull { line ->
                val i = line.indexOf(':')
                if (i > 0) line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim() else null
            }.toMap()
            val path = target.substringBefore('?')
            val query = parseUrlEncoded(target.substringAfter('?', ""))
            if (query["t"] != token) {
                respond(client, 403, page("This link has expired", "<p>Open the address shown on your TV again.</p>"))
                return
            }
            when {
                method == "GET" && path == "/" -> respond(client, 200, formPage(token))
                method == "POST" && path == "/add" -> {
                    val length = headers["content-length"]?.toLongOrNull() ?: 0
                    if (length > MAX_BODY) {
                        respond(client, 413, page("That file is too big", "<p>Playlist files up to 100 MB can be sent.</p>"))
                        return
                    }
                    val body = readBody(input, length.toInt())
                    val type = headers["content-type"].orEmpty()
                    val submission = if (type.startsWith("multipart/form-data")) {
                        parseMultipart(body, type)
                    } else {
                        PhoneSubmission(parseUrlEncoded(String(body, Charsets.UTF_8)), null)
                    }
                    val result = onSubmit(submission)
                    result.fold(
                        onSuccess = { name ->
                            respond(client, 200, page("Added to your TV", "<p><b>${escape(name)}</b> is loading on your TV now.</p><p><a href=\"/?t=$token\">Add another playlist</a></p>"))
                        },
                        onFailure = { e ->
                            respond(client, 400, page("Couldn't add the playlist", "<p>${escape(e.message ?: "Something went wrong.")}</p><p><a href=\"/?t=$token\">Go back</a></p>"))
                        },
                    )
                }
                else -> respond(client, 404, page("Not found", ""))
            }
        } catch (e: OutOfMemoryError) {
            // A playlist file too big for the TV's memory: say so rather than crash.
            runCatching { respond(client, 413, page("That file is too big for this TV", "<p>Use a playlist URL instead.</p>")) }
        } catch (e: Exception) {
            Log.w("PhoneSetupServer", "Request failed", e)
        }
    }

    /** Reads the request line and headers, giving up at [deadline] so a trickling client can't hold a thread. */
    private fun readHead(input: InputStream, deadline: Long): String? {
        val out = ByteArrayOutputStream()
        var matched = 0
        while (out.size() < 16_384) {
            if (System.nanoTime() > deadline) return null
            val b = input.read()
            if (b < 0) return null
            out.write(b)
            matched = when {
                b == '\r'.code && (matched == 0 || matched == 2) -> matched + 1
                b == '\n'.code && (matched == 1 || matched == 3) -> matched + 1
                else -> 0
            }
            if (matched == 4) return out.toString(Charsets.ISO_8859_1.name()).trimEnd()
        }
        return null
    }

    private fun readBody(input: InputStream, length: Int): ByteArray {
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) break
            read += n
        }
        return if (read == length) body else body.copyOf(read)
    }

    private fun respond(client: Socket, status: Int, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            403 -> "Forbidden"
            413 -> "Payload Too Large"
            else -> "Not Found"
        }
        val out = client.getOutputStream()
        out.write(
            ("HTTP/1.1 $status $reason\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray(Charsets.ISO_8859_1)
        )
        out.write(bytes)
        out.flush()
    }

    companion object {
        private const val ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"
        private const val MAX_BODY = 100L * 1024 * 1024
        private const val MAX_CONNECTIONS = 4
        private const val HEAD_TIMEOUT_NS = 15_000_000_000L

        /** This device's address on the local network (Wi-Fi or Ethernet). */
        fun lanAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()

        fun parseUrlEncoded(text: String): Map<String, String> = text.split('&').filter { it.isNotEmpty() }.associate { pair ->
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }

        /** Splits a multipart/form-data body into fields and (at most) one file. */
        fun parseMultipart(body: ByteArray, contentType: String): PhoneSubmission {
            val boundary = contentType.substringAfter("boundary=", "").trim().removeSurrounding("\"")
            require(boundary.isNotEmpty()) { "The form couldn't be read." }
            val delimiter = "--$boundary".toByteArray(Charsets.ISO_8859_1)
            val fields = LinkedHashMap<String, String>()
            var upload: UploadedFile? = null
            var start = indexOf(body, delimiter, 0)
            while (start >= 0) {
                val partStart = start + delimiter.size
                // "--" after the delimiter marks the end of the body.
                if (partStart + 1 < body.size && body[partStart] == '-'.code.toByte() && body[partStart + 1] == '-'.code.toByte()) break
                val next = indexOf(body, delimiter, partStart)
                if (next < 0) break
                val headerEnd = indexOf(body, "\r\n\r\n".toByteArray(), partStart)
                if (headerEnd in 0 until next) {
                    val head = String(body, partStart, headerEnd - partStart, Charsets.UTF_8)
                    val contentStart = headerEnd + 4
                    val contentEnd = (next - 2).coerceAtLeast(contentStart) // Drop the CRLF before the delimiter.
                    val disposition = head.lines().firstOrNull { it.trim().lowercase().startsWith("content-disposition") }.orEmpty()
                    val name = Regex("name=\"([^\"]*)\"").find(disposition)?.groupValues?.get(1)
                    val filename = Regex("filename=\"([^\"]*)\"").find(disposition)?.groupValues?.get(1)
                    if (name != null) {
                        if (filename != null) {
                            if (filename.isNotBlank() && contentEnd > contentStart) {
                                upload = UploadedFile(filename, body, contentStart, contentEnd - contentStart)
                            }
                        } else {
                            fields[name] = String(body, contentStart, contentEnd - contentStart, Charsets.UTF_8)
                        }
                    }
                }
                start = next
            }
            return PhoneSubmission(fields, upload)
        }

        private fun indexOf(data: ByteArray, pattern: ByteArray, from: Int): Int {
            if (pattern.isEmpty()) return from
            var i = from
            outer@ while (i <= data.size - pattern.size) {
                for (j in pattern.indices) {
                    if (data[i + j] != pattern[j]) {
                        i++
                        continue@outer
                    }
                }
                return i
            }
            return -1
        }

        private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

        private fun page(title: String, body: String) = """
            <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
            <title>${escape(title)}</title><style>$CSS</style></head>
            <body><main><h1>${escape(title)}</h1>$body</main></body></html>
        """.trimIndent()

        private fun formPage(token: String) = page(
            "Add a playlist to Tellycine",
            """
            <p class="note">Your TV and this phone need to be on the same network. Details go straight to your TV.</p>
            <form method="post" action="/add?t=$token" enctype="multipart/form-data">
              <fieldset class="types">
                <label><input type="radio" name="type" value="xtream" checked> Xtream / Dispatcharr</label>
                <label><input type="radio" name="type" value="m3u"> M3U link</label>
                <label><input type="radio" name="type" value="file"> Playlist file</label>
              </fieldset>
              <div data-for="xtream">
                <label>Server<input name="server" inputmode="url" autocapitalize="off" autocorrect="off" placeholder="http://example.com:8080"></label>
                <label>Username<input name="username" autocapitalize="off" autocorrect="off"></label>
                <label>Password<input name="password" type="password"></label>
              </div>
              <div data-for="m3u" hidden>
                <label>Playlist URL<input name="url" inputmode="url" autocapitalize="off" autocorrect="off" placeholder="http://example.com/playlist.m3u"></label>
              </div>
              <div data-for="file" hidden>
                <label>Playlist file<input name="file" type="file" accept=".m3u,.m3u8,audio/x-mpegurl,application/x-mpegurl,text/plain,*/*"></label>
              </div>
              <label>Name (optional)<input name="name"></label>
              <label>XMLTV guide URL (optional)<input name="epg" inputmode="url" autocapitalize="off" autocorrect="off"></label>
              <button type="submit">Add to TV</button>
            </form>
            <script>
              const show = () => {
                const t = document.querySelector('input[name=type]:checked').value;
                document.querySelectorAll('[data-for]').forEach(d => d.hidden = d.dataset.for !== t);
              };
              document.querySelectorAll('input[name=type]').forEach(r => r.addEventListener('change', show));
              show();
            </script>
            """.trimIndent(),
        )

        private const val CSS = """
            :root { color-scheme: dark; }
            body { margin: 0; background: #17130e; color: #ebe1d9; font: 16px/1.45 system-ui, sans-serif; }
            main { max-width: 520px; margin: 0 auto; padding: 24px 16px 48px; }
            h1 { font-size: 22px; margin: 8px 0 12px; }
            .note { color: #d3c4b4; font-size: 14px; }
            fieldset { border: 0; padding: 0; margin: 16px 0; display: flex; flex-wrap: wrap; gap: 8px; }
            fieldset label { display: flex; align-items: center; gap: 6px; background: #2e2924; padding: 10px 12px; border-radius: 12px; }
            label { display: block; margin: 12px 0; font-size: 14px; color: #d3c4b4; }
            input:not([type=radio]) { display: block; width: 100%; box-sizing: border-box; margin-top: 6px; padding: 12px; border-radius: 10px;
              border: 1px solid #4f4539; background: #1f1b16; color: #ebe1d9; font-size: 16px; }
            button { margin-top: 16px; width: 100%; padding: 14px; border: 0; border-radius: 24px; background: #ffb951; color: #462a00;
              font-size: 16px; font-weight: 600; }
            a { color: #ffb951; }
        """
    }
}
