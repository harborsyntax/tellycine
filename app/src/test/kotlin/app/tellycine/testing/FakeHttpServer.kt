package app.tellycine.testing

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.concurrent.thread

/**
 * A pretend web server on this machine: [answer] gets each request's path and query string and
 * returns the body to send, or null for "404 Not Found", or throws [Status] for another status.
 */
class FakeHttpServer(private val answer: (path: String, query: String) -> String?) : AutoCloseable {
    /** Throw from the answer to reply with [code], such as 401 or 429, and no body. */
    class Status(val code: Int) : RuntimeException("HTTP $code")

    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val url = "http://127.0.0.1:${socket.localPort}"

    /** Each request's headers, in order, with their names in lower case. */
    val headers: MutableList<Map<String, String>> = Collections.synchronizedList(ArrayList())

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
        headers += generateSequence { input.readLine()?.takeIf { it.isNotEmpty() } }
            .associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
        val (status, body) = try {
            answer(target.substringBefore('?'), target.substringAfter('?', ""))?.let { "200 OK" to it.toByteArray() } ?: ("404 Not Found" to null)
        } catch (e: Status) {
            "${e.code} Error" to null
        }
        val out = client.getOutputStream()
        out.write("HTTP/1.1 $status\r\nContent-Length: ${body?.size ?: 0}\r\nConnection: close\r\n\r\n".toByteArray())
        body?.let(out::write)
        out.flush()
    }

    override fun close() = socket.close()
}
