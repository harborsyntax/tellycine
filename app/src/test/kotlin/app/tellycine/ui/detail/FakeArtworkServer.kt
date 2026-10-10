package app.tellycine.ui.detail

import app.tellycine.AppContainer
import app.tellycine.TellycineApp
import app.tellycine.core.ContentKind
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import app.tellycine.data.db.StreamEntity
import coil3.SingletonImageLoader
import coil3.request.allowHardware
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import kotlin.concurrent.thread

/**
 * Decodes images on the main thread as plain bitmaps, until [SingletonImageLoader.reset]:
 * Robolectric can't draw hardware bitmaps, and its graphics can fail on other threads.
 */
fun useTestImageLoader(app: TellycineApp) {
    SingletonImageLoader.setUnsafe(
        app.newImageLoader(app).newBuilder()
            .coroutineContext(Dispatchers.Main.immediate)
            .fetcherCoroutineContext(Dispatchers.IO)
            .allowHardware(false)
            // Its fade-in is timed by a clock that stands still in tests.
            .crossfade(false)
            .build()
    )
}

/**
 * A pretend Xtream server on this machine, with artwork: a movie with two backdrops and a
 * trailer, and a series with season posters. The pictures are plain made-up gradients.
 */
class FakeArtworkServer : AutoCloseable {
    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val url = "http://127.0.0.1:${socket.localPort}"

    private val images = mapOf(
        "/poster.png" to picture(400, 600, 0xFF0E7C86.toInt(), 0xFF46238C.toInt()),
        "/b1.png" to picture(1280, 720, 0xFFE8743B.toInt(), 0xFF5A1E5C.toInt()),
        "/b2.png" to picture(1280, 720, 0xFF2D6CDF.toInt(), 0xFF0B1F3A.toInt()),
        "/series.png" to picture(400, 600, 0xFF2E8B57.toInt(), 0xFF0F3D2E.toInt()),
        "/s1.png" to picture(400, 600, 0xFF3CB371.toInt(), 0xFF114B3A.toInt()),
        "/s2.png" to picture(400, 600, 0xFFDAA520.toInt(), 0xFF5C3B0B.toInt()),
    )

    init {
        thread(isDaemon = true, name = "fake-artwork-server") {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (_: Exception) {
                    break
                }
                thread(isDaemon = true) { client.use(::answer) }
            }
        }
    }

    /** Answers one request, then closes the connection: just enough HTTP for the app. */
    private fun answer(client: Socket) {
        val input = client.getInputStream().bufferedReader()
        val target = input.readLine()?.split(' ')?.getOrNull(1) ?: return
        while (!input.readLine().isNullOrEmpty()) Unit // headers
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "")
        val body = when {
            path in images -> images.getValue(path)
            "action=get_vod_info" in query -> vodInfo().toByteArray()
            "action=get_series_info" in query -> seriesInfo().toByteArray()
            else -> null
        }
        val out = client.getOutputStream()
        val status = if (body == null) "404 Not Found" else "200 OK"
        val type = if (path.endsWith(".png")) "image/png" else "application/json"
        out.write("HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${body?.size ?: 0}\r\nConnection: close\r\n\r\n".toByteArray())
        body?.let(out::write)
        out.flush()
    }

    /** Adds this server as a playlist holding movie "m1" and series "s1"; returns its id. */
    fun seed(container: AppContainer): Long = runBlocking(Dispatchers.IO) {
        val db = container.database
        val pid = db.playlists().insert(
            PlaylistEntity(name = "Artwork IPTV", type = PlaylistType.XTREAM, url = url, username = "u", password = "p", lastSyncedAt = System.currentTimeMillis())
        )
        db.content().insertStreams(
            listOf(
                StreamEntity(pid, ContentKind.MOVIE, "m1", "Neon Harbour", 0, null, "$url/poster.png", "$url/movie/u/p/m1.mkv", year = "2025"),
                StreamEntity(pid, ContentKind.SERIES, "s1", "Tidewater", 0, null, "$url/series.png", ""),
            )
        )
        pid
    }

    private fun vodInfo() = """
        {"info":{"name":"Neon Harbour","movie_image":"$url/poster.png","backdrop_path":["$url/b1.png","$url/b2.png"],
          "youtube_trailer":"dQw4w9WgXcQ","plot":"A night-shift harbour pilot finds a ship that isn't on any chart.",
          "genre":"Thriller","releasedate":"2025-03-01","duration_secs":6300,"rating":"7.6",
          "director":"Ada Marlow","cast":"Ren Okafor, Lise Varga"},
         "movie_data":{"stream_id":"m1","container_extension":"mkv"}}
    """.trimIndent()

    private fun seriesInfo() = """
        {"info":{"name":"Tidewater","cover":"$url/series.png","backdrop_path":[],"youtube_trailer":"https://youtu.be/dQw4w9WgXcQ",
          "plot":"A coastal town keeps its secrets below the waterline.","genre":"Drama","releaseDate":"2023-05-01"},
         "seasons":[{"season_number":1,"name":"Season 1","cover":"$url/s1.png"},{"season_number":2,"name":"Season 2","cover":"$url/s2.png"}],
         "episodes":{"1":[{"id":"e11","episode_num":1,"title":"Low Tide","container_extension":"mp4","info":{}},
                          {"id":"e12","episode_num":2,"title":"Slack Water","container_extension":"mp4","info":{}}],
                     "2":[{"id":"e21","episode_num":1,"title":"Undertow","container_extension":"mp4","season":2,"info":{}}]}}
    """.trimIndent()

    override fun close() = socket.close()

    private companion object {
        /**
         * A diagonal gradient between two colours, as a PNG. Written by hand rather than drawn,
         * so it can be made before the test's Android graphics are set up.
         */
        fun picture(w: Int, h: Int, from: Int, to: Int): ByteArray {
            val rows = ByteArrayOutputStream()
            for (y in 0 until h) {
                rows.write(0) // no filter
                for (x in 0 until w) {
                    val t = (x.toFloat() / w + y.toFloat() / h) / 2
                    for (shift in intArrayOf(16, 8, 0)) {
                        val a = (from shr shift) and 0xFF
                        val b = (to shr shift) and 0xFF
                        rows.write((a + (b - a) * t).toInt())
                    }
                }
            }
            val pixels = ByteArrayOutputStream().also { out -> DeflaterOutputStream(out).use { it.write(rows.toByteArray()) } }
            val header = java.nio.ByteBuffer.allocate(13).putInt(w).putInt(h).put(8).put(2).put(0).put(0).put(0).array()
            return ByteArrayOutputStream().apply {
                write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
                chunk("IHDR", header)
                chunk("IDAT", pixels.toByteArray())
                chunk("IEND", ByteArray(0))
            }.toByteArray()
        }

        private fun ByteArrayOutputStream.chunk(type: String, data: ByteArray) {
            val typeBytes = type.toByteArray()
            write(java.nio.ByteBuffer.allocate(4).putInt(data.size).array())
            write(typeBytes)
            write(data)
            val crc = CRC32().apply { update(typeBytes); update(data) }
            write(java.nio.ByteBuffer.allocate(4).putInt(crc.value.toInt()).array())
        }
    }
}
