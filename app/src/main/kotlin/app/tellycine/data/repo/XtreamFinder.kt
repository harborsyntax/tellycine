package app.tellycine.data.repo

import app.tellycine.core.xtream.XtreamApi
import app.tellycine.core.xtream.XtreamParser
import app.tellycine.core.xtream.XtreamUrls
import app.tellycine.data.net.Downloader
import app.tellycine.data.net.HttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An Xtream Codes login to suggest instead of an M3U link: [loginWorks] when the server accepted it. */
data class XtreamOffer(val server: String, val username: String, val password: String, val loginWorks: Boolean)

/**
 * Asks the server behind an M3U link whether it also has an Xtream Codes API, which gives movie
 * and series details, catch-up and dates to sort by: with the login in the link when it has one
 * ([XtreamUrls.fromM3uUrl]), or without one, to see whether there's an API at all.
 */
class XtreamFinder(private val downloader: Downloader) {
    private val parser = XtreamParser()

    /** The offer, or null if the server has no Xtream Codes API. Throws if it can't be reached. */
    suspend fun check(url: String, userAgent: String?): XtreamOffer? = withContext(Dispatchers.IO) {
        val login = XtreamUrls.fromM3uUrl(url)
        val server = login?.first ?: XtreamUrls.origin(url) ?: return@withContext null
        val api = if (login != null) XtreamUrls(server, login.second, login.third).api() else "$server/player_api.php"
        val status = try {
            parser.apiStatus(downloader.getString(api, userAgent))
        } catch (e: HttpException) {
            // Not found, forbidden…: nothing there to use.
            XtreamApi.NONE
        }
        when (status) {
            XtreamApi.NONE -> null
            XtreamApi.OFFERED -> XtreamOffer(server, login?.second.orEmpty(), login?.third.orEmpty(), loginWorks = false)
            XtreamApi.WORKS -> XtreamOffer(server, login?.second.orEmpty(), login?.third.orEmpty(), loginWorks = login != null)
        }
    }
}
