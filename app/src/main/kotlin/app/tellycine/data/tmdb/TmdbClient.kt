package app.tellycine.data.tmdb

import app.tellycine.BuildConfig
import app.tellycine.core.tmdb.Tmdb
import app.tellycine.core.tmdb.TmdbKind
import app.tellycine.core.tmdb.TmdbTitle
import app.tellycine.data.net.HttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale

/**
 * TMDB's API (https://developer.themoviedb.org), with the API key or read access token people get
 * for themselves (Tellycine has none of its own). TMDB asks apps to stay well under about 40
 * requests a second, so requests go one at a time at most every [spacingMs], and wait when TMDB
 * says there were too many. Answers come in the device's language where TMDB has it.
 */
class TmdbClient(
    private val http: OkHttpClient,
    /** TMDB's address; tests point it at a pretend server. */
    var baseUrl: String = "https://api.themoviedb.org/3",
    private val spacingMs: Long = 100,
    private val busyWaitMs: Long = 2_000,
    private val locale: () -> Locale = Locale::getDefault,
) {
    /** TMDB didn't accept the key. */
    class KeyRejected : IOException("TMDB didn't accept the API key")

    enum class KeyCheck { WORKS, REJECTED, UNREACHABLE }

    private val turn = Mutex()
    private var lastAt = 0L

    /** The country for age ratings: the device's, or the US. */
    val region: String get() = locale().country.takeIf { it.length == 2 }?.uppercase(Locale.ROOT) ?: "US"

    private val language: String
        get() {
            val l = locale()
            return when {
                // TMDB's English is American English; other English versions are often incomplete.
                l.language.isEmpty() || l.language == "en" -> "en-US"
                l.country.length == 2 -> "${l.language}-${l.country}"
                else -> l.language
            }
        }

    /** Whether TMDB accepts [key]. */
    suspend fun checkKey(key: String): KeyCheck = try {
        get(key, "authentication")
        KeyCheck.WORKS
    } catch (e: KeyRejected) {
        KeyCheck.REJECTED
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        KeyCheck.UNREACHABLE
    }

    /** Films or series ([kind]) called [query], from [year] if given, best first. */
    suspend fun search(key: String, kind: TmdbKind, query: String, year: Int? = null): List<TmdbTitle> {
        val yearParam = year?.let { (if (kind == TmdbKind.MOVIE) "year" else "first_air_date_year") to it.toString() }
        val params = listOfNotNull("query" to query, "include_adult" to "false", yearParam)
        return get(key, "search/${kind.path}", *params.toTypedArray())?.let { Tmdb.parseSearch(it, kind) }.orEmpty()
    }

    /** A title's page as JSON, with the parts in [append] (see [Tmdb.detailsAppend]); null if TMDB doesn't have it. */
    suspend fun page(key: String, kind: TmdbKind, id: Int, append: String? = null): String? {
        val params = if (append == null) emptyList() else listOf(
            "append_to_response" to append,
            // Pictures in the device's language or English, and those without words.
            "include_image_language" to listOf(language.substringBefore('-'), "en", "null").distinct().joinToString(","),
        )
        return get(key, "${kind.path}/$id", *params.toTypedArray())
    }

    /** A series' season as JSON. */
    suspend fun season(key: String, tvId: Int, number: Int): String? = get(key, "tv/$tvId/season/$number")

    /** A collection of films as JSON. */
    suspend fun collection(key: String, id: Int): String? = get(key, "collection/$id")

    private suspend fun get(key: String, path: String, vararg query: Pair<String, String>): String? {
        // API keys are 32 characters; read access tokens are much longer, and go in a header.
        val token = key.length > 40
        val url = "$baseUrl/$path".toHttpUrl().newBuilder().apply {
            if (!token) addQueryParameter("api_key", key)
            addQueryParameter("language", language)
            query.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()
        val request = Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .apply { if (token) header("Authorization", "Bearer $key") }
            .build()
        repeat(BUSY_RETRIES) { attempt ->
            turn.withLock {
                val wait = lastAt + spacingMs - System.currentTimeMillis()
                if (wait > 0) delay(wait)
                lastAt = System.currentTimeMillis()
            }
            val (code, body) = withContext(Dispatchers.IO) {
                http.newCall(request).execute().use { it.code to it.body?.string() }
            }
            when (code) {
                in 200..299 -> return body
                401 -> throw KeyRejected()
                404 -> return null
                429 -> delay(busyWaitMs * (attempt + 1))
                else -> throw HttpException(code, "TMDB returned HTTP $code")
            }
        }
        throw HttpException(429, "TMDB is busy")
    }

    private companion object {
        const val BUSY_RETRIES = 4
        val USER_AGENT = "Tellycine/${BuildConfig.VERSION_NAME} (+https://github.com/harborsyntax/tellycine)"
    }
}
