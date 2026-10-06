package app.mosaic.data.repo

import android.util.LruCache
import app.mosaic.core.xtream.XtreamParser
import app.mosaic.core.xtream.XtreamSeriesInfo
import app.mosaic.core.xtream.XtreamUrls
import app.mosaic.core.xtream.XtreamVodInfo
import app.mosaic.data.db.PlaylistEntity
import app.mosaic.data.net.Downloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** On-demand Xtream metadata (movie details, series seasons and episodes), cached in memory. */
class DetailsRepository(private val downloader: Downloader) {
    private val parser = XtreamParser()
    private val vodCache = LruCache<String, XtreamVodInfo>(64)
    private val seriesCache = LruCache<String, XtreamSeriesInfo>(32)

    suspend fun vodInfo(playlist: PlaylistEntity, vodId: String): XtreamVodInfo = withContext(Dispatchers.IO) {
        val key = "${playlist.id}:$vodId"
        vodCache.get(key) ?: run {
            val urls = XtreamUrls(playlist.url, playlist.username, playlist.password)
            parser.parseVodInfo(downloader.getString(urls.api("get_vod_info", "vod_id" to vodId), playlist.userAgent).reader())
                .also { vodCache.put(key, it) }
        }
    }

    suspend fun seriesInfo(playlist: PlaylistEntity, seriesId: String): XtreamSeriesInfo = withContext(Dispatchers.IO) {
        val key = "${playlist.id}:$seriesId"
        seriesCache.get(key) ?: run {
            val urls = XtreamUrls(playlist.url, playlist.username, playlist.password)
            parser.parseSeriesInfo(downloader.getString(urls.api("get_series_info", "series_id" to seriesId), playlist.userAgent).reader())
                .also { seriesCache.put(key, it) }
        }
    }
}
