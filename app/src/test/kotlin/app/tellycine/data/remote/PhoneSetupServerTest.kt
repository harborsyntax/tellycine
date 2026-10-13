package app.tellycine.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class PhoneSetupServerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var received: PhoneSubmission? = null
    private val server = PhoneSetupServer { submission ->
        received = submission
        if (submission.fields["server"] == "bad") Result.failure(IllegalArgumentException("Enter the server address.")) else Result.success("My TV list")
    }

    @After
    fun tearDown() {
        server.stop()
        scope.cancel()
    }

    private fun request(url: String, method: String = "GET", contentType: String? = null, body: ByteArray? = null): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", contentType)
            connection.outputStream.use { it.write(body) }
        }
        val code = connection.responseCode
        val text = (if (code < 400) connection.inputStream else connection.errorStream).bufferedReader().readText()
        return code to text
    }

    @Test
    fun servesTheFormOnlyWithTheToken() {
        val url = server.start(scope, host = "127.0.0.1")!!
        val (ok, form) = request(url)
        assertEquals(200, ok)
        assertTrue(form.contains("Add to TV"))
        assertTrue(form.contains("Add another guide URL"))
        assertTrue(form.contains("name=\"epgCombine\""))

        val (forbidden, _) = request(url.substringBefore("?t=") + "?t=wrong")
        assertEquals(403, forbidden)
    }

    @Test
    fun addsAPlaylistFromAnUrlEncodedForm() {
        val url = server.start(scope, host = "127.0.0.1")!!
        val add = url.replace("/?t=", "/add?t=")
        val (code, page) = request(
            add, "POST", "application/x-www-form-urlencoded",
            "type=xtream&server=http%3A%2F%2Fexample.com%3A8080&username=me&password=p%40ss+word".toByteArray(),
        )
        assertEquals(200, code)
        assertTrue(page.contains("My TV list"))
        val fields = received!!.fields
        assertEquals("http://example.com:8080", fields["server"])
        assertEquals("me", fields["username"])
        assertEquals("p@ss word", fields["password"])
    }

    @Test
    fun reportsWhyAPlaylistWasRefused() {
        val url = server.start(scope, host = "127.0.0.1")!!
        val (code, page) = request(url.replace("/?t=", "/add?t="), "POST", "application/x-www-form-urlencoded", "type=xtream&server=bad".toByteArray())
        assertEquals(400, code)
        assertTrue(page.contains("Enter the server address."))
    }

    @Test
    fun parsesMultipartFieldsAndAFile() {
        val boundary = "----tellycine123"
        val body = (
            "--$boundary\r\nContent-Disposition: form-data; name=\"type\"\r\n\r\nfile\r\n" +
                "--$boundary\r\nContent-Disposition: form-data; name=\"name\"\r\n\r\nKitchen TV\r\n" +
                "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"list.m3u\"\r\nContent-Type: audio/x-mpegurl\r\n\r\n" +
                "#EXTM3U\r\n#EXTINF:-1,One\r\nhttp://example.com/1.ts\r\n" +
                "\r\n--$boundary--\r\n"
            ).toByteArray()
        val parsed = PhoneSetupServer.parseMultipart(body, "multipart/form-data; boundary=$boundary")
        assertEquals("file", parsed.fields["type"])
        assertEquals("Kitchen TV", parsed.fields["name"])
        assertEquals("list.m3u", parsed.file?.name)
        assertEquals("#EXTM3U\r\n#EXTINF:-1,One\r\nhttp://example.com/1.ts\r\n", String(parsed.file!!.bytes()))
        assertEquals(String(parsed.file!!.bytes()), parsed.file!!.open().bufferedReader().readText())
    }

    @Test
    fun ignoresAnEmptyFileField() {
        val boundary = "b"
        val body = "--b\r\nContent-Disposition: form-data; name=\"type\"\r\n\r\nm3u\r\n--b\r\nContent-Disposition: form-data; name=\"file\"; filename=\"\"\r\n\r\n\r\n--b--\r\n".toByteArray()
        val parsed = PhoneSetupServer.parseMultipart(body, "multipart/form-data; boundary=\"$boundary\"")
        assertEquals("m3u", parsed.fields["type"])
        assertNull(parsed.file)
    }

    @Test
    fun doesNotStartOnceStopped() {
        server.stop()
        assertNull(server.start(scope, host = "127.0.0.1"))
    }

    @Test
    fun decodesUrlEncodedText() {
        val fields = PhoneSetupServer.parseUrlEncoded("a=1&b=two+words&c=%26%3D&empty=")
        assertEquals(mapOf("a" to "1", "b" to "two words", "c" to "&=", "empty" to ""), fields)
        assertNotNull(PhoneSetupServer.parseUrlEncoded(""))
    }
}
