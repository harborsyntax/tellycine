package app.tellycine.core.xtream

import java.net.URLEncoder

/** Builds Xtream Codes API and stream URLs for one account. */
class XtreamUrls(server: String, val username: String, val password: String) {

    val baseUrl: String = normaliseServer(server)

    private val u get() = enc(username)
    private val p get() = enc(password)

    fun api(action: String? = null, vararg params: Pair<String, String>): String = buildString {
        append(baseUrl).append("/player_api.php?username=").append(u).append("&password=").append(p)
        if (action != null) append("&action=").append(action)
        params.forEach { (k, v) -> append('&').append(k).append('=').append(enc(v)) }
    }

    fun xmltv(): String = "$baseUrl/xmltv.php?username=$u&password=$p"

    fun live(streamId: String, extension: String): String = "$baseUrl/live/$u/$p/$streamId.$extension"

    fun movie(streamId: String, extension: String?): String =
        "$baseUrl/movie/$u/$p/$streamId.${extension?.ifBlank { null } ?: "mp4"}"

    fun episode(episodeId: String, extension: String?): String =
        "$baseUrl/series/$u/$p/$episodeId.${extension?.ifBlank { null } ?: "mp4"}"

    /**
     * Catch-up / timeshift URL for live streams with archive support.
     * [start] must already be formatted as `yyyy-MM-dd:HH-mm` in the server's timezone.
     */
    fun timeshift(streamId: String, start: String, durationMinutes: Int): String =
        "$baseUrl/timeshift/$u/$p/$durationMinutes/$start/$streamId.ts"

    companion object {
        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        private val PLAYLIST_PATH = Regex("""(?i)^(https?://[^/?#]+)/playlist/([^/?#]+)/([^/?#]+)""")

        /** The scheme, host and port of [url]: where its server would answer `player_api.php`. */
        fun origin(url: String): String? = Regex("""(?i)^(https?://[^/?#]+)""").find(url.trim())?.groupValues?.get(1)

        fun normaliseServer(input: String): String {
            var s = input.trim()
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
            // Strip any path that users commonly paste along with the host.
            val schemeEnd = s.indexOf("://") + 3
            val pathStart = s.indexOf('/', schemeEnd)
            val path = if (pathStart >= 0) s.substring(pathStart) else ""
            val knownEndpoints = listOf("/player_api.php", "/get.php", "/xmltv.php", "/panel_api.php")
            val cut = knownEndpoints.map { path.indexOf(it) }.filter { it >= 0 }.minOrNull()
            s = if (cut != null) s.substring(0, pathStart + cut) else s.substringBefore('?')
            return s.trimEnd('/')
        }

        /**
         * Detects Xtream credentials embedded in an M3U link such as
         * `http://host:8080/get.php?username=u&password=p&type=m3u_plus`, or
         * `http://host:8080/playlist/u/p/m3u_plus` (XUI.one).
         */
        fun fromM3uUrl(url: String): Triple<String, String, String>? {
            PLAYLIST_PATH.find(url.trim())?.let { m ->
                val (origin, user, pass) = m.destructured
                fun decode(s: String) = java.net.URLDecoder.decode(s, "UTF-8")
                return Triple(normaliseServer(origin), decode(user), decode(pass))
            }
            val q = url.substringAfter('?', "")
            if (q.isEmpty() || !url.contains("get.php", ignoreCase = true)) return null
            val params = q.split('&').associate {
                it.substringBefore('=').lowercase() to java.net.URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
            }
            val user = params["username"]?.ifBlank { null } ?: return null
            val pass = params["password"]?.ifBlank { null } ?: return null
            return Triple(normaliseServer(url), user, pass)
        }
    }
}
