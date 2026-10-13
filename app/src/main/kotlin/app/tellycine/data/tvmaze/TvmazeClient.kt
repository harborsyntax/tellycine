package app.tellycine.data.tvmaze

import app.tellycine.BuildConfig
import app.tellycine.core.tvmaze.Tvmaze
import app.tellycine.core.tvmaze.TvmazeShow
import app.tellycine.data.net.HttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * TVmaze's public API (https://www.tvmaze.com/api): free, without a key, and limited to 20
 * requests in 10 seconds, so requests go one at a time at most every [spacingMs], and wait when
 * TVmaze says there were too many.
 */
class TvmazeClient(
    private val http: OkHttpClient,
    /** TVmaze's address; tests point it at a pretend server. */
    var baseUrl: String = "https://api.tvmaze.com",
    private val spacingMs: Long = 550,
    private val busyWaitMs: Long = 10_000,
) {
    private val turn = Mutex()
    private var lastAt = 0L

    /** The show that best matches [name], with its latest and next episodes; null if none. */
    suspend fun bestMatch(name: String): TvmazeShow? =
        get("singlesearch/shows", "q" to name, embedEpisodes = true)?.let(Tvmaze::parseShow)

    /** Shows that match [name], best first. */
    suspend fun search(name: String): List<TvmazeShow> = get("search/shows", "q" to name)?.let(Tvmaze::parseSearch).orEmpty()

    /** Show [id], with its latest and next episodes; null if TVmaze no longer has it. */
    suspend fun show(id: Int): TvmazeShow? = get("shows/$id", embedEpisodes = true)?.let(Tvmaze::parseShow)

    /** Shows changed in the last "day", "week" or "month" ([since]), or ever (null): TVmaze's id to seconds. */
    suspend fun updates(since: String?): Map<Int, Long> =
        get("updates/shows", *listOfNotNull(since?.let { "since" to it }).toTypedArray())?.let(Tvmaze::parseUpdates).orEmpty()

    private suspend fun get(path: String, vararg query: Pair<String, String>, embedEpisodes: Boolean = false): String? {
        val url = "$baseUrl/$path".toHttpUrl().newBuilder().apply {
            query.forEach { (name, value) -> addQueryParameter(name, value) }
            if (embedEpisodes) {
                addEncodedQueryParameter("embed[]", "previousepisode")
                addEncodedQueryParameter("embed[]", "nextepisode")
            }
        }.build()
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        repeat(BUSY_RETRIES) { attempt ->
            turn.withLock {
                val wait = lastAt + spacingMs - System.currentTimeMillis()
                if (wait > 0) delay(wait)
                lastAt = System.currentTimeMillis()
            }
            val answer = withContext(Dispatchers.IO) {
                http.newCall(request).execute().use { it.code to if (it.isSuccessful) it.body?.string().orEmpty() else null }
            }
            when (answer.first) {
                in 200..299 -> return answer.second
                404 -> return null
                429 -> delay(busyWaitMs * (attempt + 1))
                else -> throw HttpException(answer.first, "TVmaze returned HTTP ${answer.first}")
            }
        }
        throw HttpException(429, "TVmaze is busy")
    }

    private companion object {
        const val BUSY_RETRIES = 4
        val USER_AGENT = "Tellycine/${BuildConfig.VERSION_NAME} (+https://github.com/harborsyntax/tellycine)"
    }
}
