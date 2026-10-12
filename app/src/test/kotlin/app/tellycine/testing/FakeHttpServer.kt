package app.tellycine.testing

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * A pretend web server on this machine: [answer] gets each request's path and query string and
 * returns the body to send, or null for "404 Not Found".
 */
class FakeHttpServer(private val answer: (path: String, query: String) -> String?) : AutoCloseable {
    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val url = "http://127.0.0.1:${socket.localPort}"

    init {
        thread(isDaemon = true, name = "fake-http-server") {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (_: Exception) {
                    break
                }
                thread(isDaemon = true) { client.use(::respond) }
            }
        }
    }

    private fun respond(client: Socket) {
        val input = client.getInputStream().bufferedReader()
        val target = input.readLine()?.split(' ')?.getOrNull(1) ?: return
        while (!input.readLine().isNullOrEmpty()) Unit // headers
        val body = answer(target.substringBefore('?'), target.substringAfter('?', ""))?.toByteArray()
        val out = client.getOutputStream()
        out.write("HTTP/1.1 ${if (body == null) "404 Not Found" else "200 OK"}\r\nContent-Length: ${body?.size ?: 0}\r\nConnection: close\r\n\r\n".toByteArray())
        body?.let(out::write)
        out.flush()
    }

    override fun close() = socket.close()
}
