package app.tellycine.data.sync

import android.content.Context
import android.util.Log
import android.util.Xml
import app.tellycine.core.ContentKind
import app.tellycine.core.ReleaseDates
import app.tellycine.core.gunzipIfNeeded
import app.tellycine.core.m3u.M3uHeader
import app.tellycine.core.m3u.M3uParser
import app.tellycine.core.xmltv.EpgMatcher
import app.tellycine.core.xmltv.XmltvParser
import app.tellycine.core.xtream.XtreamAccount
import app.tellycine.core.xtream.XtreamItem
import app.tellycine.core.xtream.XtreamParser
import app.tellycine.core.xtream.XtreamUrls
import app.tellycine.data.db.CategoryEntity
import app.tellycine.data.db.EpisodeEntity
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import app.tellycine.data.db.ProgrammeEntity
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.db.TellycineDatabase
import app.tellycine.data.net.Downloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

typealias ProgressReporter = (message: String, fraction: Float?) -> Unit

data class ContentSyncResult(
    val live: Int,
    val movies: Int,
    val series: Int,
    val detectedEpgUrl: String,
    val account: XtreamAccount?,
)

/**
 * Downloads and imports playlists and EPG data.
 *
 * Large sources are first downloaded to a temporary file and then imported in a single
 * database transaction. Thanks to WAL mode the UI keeps showing the previous data until
 * the import commits, and a failed import never leaves a half-empty playlist behind.
 */
class PlaylistSyncer(
    private val context: Context,
    private val db: TellycineDatabase,
    private val downloader: Downloader,
) {
    private val dao = db.content()
    private val tempDir get() = File(context.cacheDir, "sync").apply { mkdirs() }

    // ------------------------------------------------------------------------------------
    // Content
    // ------------------------------------------------------------------------------------

    suspend fun syncContent(playlist: PlaylistEntity, preferHls: Boolean, report: ProgressReporter): ContentSyncResult =
        withContext(Dispatchers.IO) {
            when (playlist.type) {
                PlaylistType.XTREAM -> syncXtream(playlist, preferHls, report)
                PlaylistType.M3U_URL, PlaylistType.M3U_FILE -> syncM3u(playlist, report)
            }
        }

    private suspend fun syncM3u(playlist: PlaylistEntity, report: ProgressReporter): ContentSyncResult {
        val file = File(tempDir, "playlist-${playlist.id}.m3u")
        try {
            report("Downloading playlist…", null)
            downloader.download(playlist.url, file, playlist.userAgent) { read, total ->
                report("Downloading playlist… ${formatBytes(read)}", total?.let { read.toFloat() / it })
            }
            report("Importing channels…", null)
            val job = currentCoroutineContext().job
            return importM3u(playlist, file, job, report)
        } finally {
            file.delete()
        }
    }

    private fun importM3u(playlist: PlaylistEntity, file: File, job: Job, report: ProgressReporter): ContentSyncResult {
        val pid = playlist.id
        val hasher = StableId()
        val counts = IntArray(ContentKind.entries.size)
        // (kind, group) -> [sortOrder, itemCount]
        val categories = LinkedHashMap<Pair<ContentKind, String>, IntArray>()
        val seriesSeen = HashMap<String, IntArray>() // seriesId -> [episodeCounter]
        val streams = ArrayList<StreamEntity>(BATCH)
        val episodes = ArrayList<EpisodeEntity>(BATCH)
        var detectedEpg = ""
        var header: M3uHeader? = null
        var processed = 0

        fun countCategory(kind: ContentKind, group: String) {
            val entry = categories.getOrPut(kind to group) { intArrayOf(categories.size, 0) }
            entry[1]++
        }

        db.runInTransaction {
            val firstSeen = FirstSeen(db, pid, countsEpisodes = true)
            dao.deleteStreams(pid)
            dao.deleteCategories(pid)
            dao.deleteEpisodes(pid)

            file.inputStream().gunzipIfNeeded().bufferedReader().use { reader ->
                M3uParser().parse(
                    reader,
                    onHeader = {
                        header = it
                        detectedEpg = it.epgUrls.joinToString(",")
                    },
                ) { e ->
                    if (!job.isActive) throw CancellationException("Sync cancelled")
                    val kind = ContentKind.classify(e.url)
                    if (kind != ContentKind.LIVE && !playlist.vodEnabled) return@parse
                    val group = e.group?.trim()?.ifEmpty { null } ?: UNCATEGORIZED

                    if (kind == ContentKind.SERIES) {
                        val match = EPISODE_REGEX.find(e.name)
                        val seriesName = match?.groupValues?.get(1)?.trim(' ', '-', '_', '.', '|', ':')?.ifEmpty { null } ?: e.name
                        val seriesId = hasher.of("${group}|${seriesName}")
                        val state = seriesSeen.getOrPut(seriesId) {
                            val year = ReleaseDates.yearInTitle(seriesName)
                            streams += StreamEntity(
                                playlistId = pid, kind = kind, streamId = seriesId, name = seriesName,
                                sortOrder = counts[kind.ordinal]++, categoryId = group, logo = e.logo, url = "",
                                year = year, released = year,
                            )
                            countCategory(kind, group)
                            intArrayOf(0)
                        }
                        state[0]++
                        episodes += EpisodeEntity(
                            playlistId = pid,
                            episodeId = hasher.of("${e.url}|${e.name}"),
                            seriesId = seriesId,
                            season = match?.groupValues?.get(2)?.toIntOrNull() ?: 1,
                            episode = match?.groupValues?.get(3)?.toIntOrNull() ?: state[0],
                            title = e.name,
                            url = e.url,
                            logo = e.logo,
                        )
                    } else {
                        val live = kind == ContentKind.LIVE
                        val catchupType = if (live) e.catchupType ?: header?.catchupType else null
                        val catchupSource = if (live) e.catchupSource ?: header?.catchupSource else null
                        val hasCatchup = catchupType != null || catchupSource != null
                        val year = if (live) null else ReleaseDates.yearInTitle(e.name)
                        streams += StreamEntity(
                            playlistId = pid,
                            kind = kind,
                            streamId = hasher.of("${group}|${e.name}|${e.url}"),
                            name = e.name,
                            sortOrder = counts[kind.ordinal]++,
                            categoryId = group,
                            logo = e.logo,
                            url = e.url,
                            epgChannelId = if (kind == ContentKind.LIVE) (e.tvgId ?: e.tvgName ?: e.name).trim().lowercase() else null,
                            number = e.attributes["tvg-chno"]?.toIntOrNull(),
                            catchupDays = if (hasCatchup) (e.catchupDays ?: header?.catchupDays ?: DEFAULT_M3U_CATCHUP_DAYS) else null,
                            catchupType = catchupType,
                            catchupSource = catchupSource,
                            userAgent = e.userAgent,
                            referrer = e.referrer,
                            year = year,
                            released = year,
                        )
                        countCategory(kind, group)
                    }

                    if (streams.size >= BATCH) {
                        dao.insertStreams(streams); streams.clear()
                    }
                    if (episodes.size >= BATCH) {
                        dao.insertEpisodes(episodes); episodes.clear()
                    }
                    if (++processed % 5000 == 0) report("Importing… ${"%,d".format(processed)} entries", null)
                }
            }
            if (streams.isNotEmpty()) dao.insertStreams(streams)
            if (episodes.isNotEmpty()) dao.insertEpisodes(episodes)
            dao.insertCategories(categories.map { (key, v) ->
                CategoryEntity(pid, key.first, key.second, key.second, v[0], v[1])
            })
            firstSeen.restore()
        }

        return ContentSyncResult(
            live = counts[ContentKind.LIVE.ordinal],
            movies = counts[ContentKind.MOVIE.ordinal],
            series = counts[ContentKind.SERIES.ordinal],
            detectedEpgUrl = detectedEpg,
            account = null,
        )
    }

    private suspend fun syncXtream(playlist: PlaylistEntity, preferHls: Boolean, report: ProgressReporter): ContentSyncResult {
        val urls = XtreamUrls(playlist.url, playlist.username, playlist.password)
        val parser = XtreamParser()
        val ua = playlist.userAgent

        report("Signing in…", null)
        val account = parser.parseAccount(downloader.getString(urls.api(), ua).reader())
        if (!account.authenticated) {
            throw IOException(account.message ?: "Login failed. Check the server address, username and password.")
        }
        val formats = account.allowedOutputFormats
        val liveExt = when {
            preferHls && (formats.isEmpty() || "m3u8" in formats) -> "m3u8"
            formats.isEmpty() || "ts" in formats -> "ts"
            else -> formats.first()
        }

        val endpoints = listOf("get_live_categories", "get_live_streams") +
            if (playlist.vodEnabled) {
                listOf("get_vod_categories", "get_vod_streams", "get_series_categories", "get_series")
            } else {
                emptyList()
            }
        val files = endpoints.associateWith { File(tempDir, "xtream-${playlist.id}-$it.json") }
        try {
            var done = 0
            report("Downloading channels, movies and series…", 0f)
            coroutineScope {
                endpoints.map { action ->
                    async {
                        try {
                            downloader.download(urls.api(action), files.getValue(action), ua)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Live TV is essential; VOD/series are optional on many accounts.
                            if (action.startsWith("get_live")) throw e
                            Log.w(TAG, "Xtream $action failed", e)
                            files.getValue(action).writeText("[]")
                        }
                        synchronized(files) {
                            done++
                            report("Downloading… $done of ${endpoints.size}", done.toFloat() / endpoints.size)
                        }
                    }
                }.awaitAll()
            }

            report("Importing…", null)
            val job = currentCoroutineContext().job
            val counts = IntArray(ContentKind.entries.size)
            val pid = playlist.id

            db.runInTransaction {
                val firstSeen = FirstSeen(db, pid)
                dao.deleteStreams(pid)
                dao.deleteCategories(pid)
                dao.deleteEpisodes(pid)

                for ((kind, catAction, itemsAction) in listOf(
                    Triple(ContentKind.LIVE, "get_live_categories", "get_live_streams"),
                    Triple(ContentKind.MOVIE, "get_vod_categories", "get_vod_streams"),
                    Triple(ContentKind.SERIES, "get_series_categories", "get_series"),
                )) {
                    val categoryNames = LinkedHashMap<String, String>()
                    readJson(files[catAction]) { reader ->
                        parser.parseCategories(reader) { categoryNames[it.id] = it.name }
                    }
                    val categoryCounts = HashMap<String, Int>()
                    val batch = ArrayList<StreamEntity>(BATCH)
                    readJson(files[itemsAction]) { reader ->
                        parser.parseItems(reader) { item ->
                            if (!job.isActive) throw CancellationException("Sync cancelled")
                            val categoryId = item.categoryId?.takeIf { it in categoryNames }
                            if (categoryId != null) categoryCounts[categoryId] = (categoryCounts[categoryId] ?: 0) + 1
                            batch += item.toEntity(pid, kind, counts[kind.ordinal]++, categoryId, urls, liveExt)
                            if (batch.size >= BATCH) {
                                dao.insertStreams(batch); batch.clear()
                            }
                        }
                    }
                    if (batch.isNotEmpty()) dao.insertStreams(batch)
                    dao.insertCategories(
                        categoryNames.entries.filter { (categoryCounts[it.key] ?: 0) > 0 }
                            .mapIndexed { index, (id, name) ->
                                CategoryEntity(pid, kind, id, name, index, categoryCounts[id] ?: 0)
                            }
                    )
                    report("Imported ${"%,d".format(counts[kind.ordinal])} ${kind.label()}", null)
                }
                firstSeen.restore()
            }

            return ContentSyncResult(
                live = counts[ContentKind.LIVE.ordinal],
                movies = counts[ContentKind.MOVIE.ordinal],
                series = counts[ContentKind.SERIES.ordinal],
                detectedEpgUrl = urls.xmltv(),
                account = account,
            )
        } finally {
            files.values.forEach { it.delete() }
        }
    }

    private fun XtreamItem.toEntity(
        pid: Long, kind: ContentKind, order: Int, categoryId: String?, urls: XtreamUrls, liveExt: String,
    ) = StreamEntity(
        playlistId = pid,
        kind = kind,
        streamId = id,
        name = name,
        sortOrder = order,
        categoryId = categoryId,
        logo = icon,
        url = when (kind) {
            ContentKind.LIVE -> urls.live(id, liveExt)
            ContentKind.MOVIE -> urls.movie(id, containerExtension)
            ContentKind.SERIES -> ""
        },
        epgChannelId = if (kind == ContentKind.LIVE) (epgChannelId ?: name).trim().lowercase() else null,
        number = num,
        containerExt = containerExtension,
        rating = rating?.takeIf { it > 0 },
        year = year,
        plot = plot,
        genre = genre,
        addedAt = addedMs,
        updatedAt = updatedMs,
        released = released,
        catchupDays = catchupDays,
        backdrop = backdrop,
    )

    private inline fun readJson(file: File?, block: (java.io.Reader) -> Unit) {
        if (file == null || !file.exists() || file.length() == 0L) return
        file.inputStream().gunzipIfNeeded().reader(Charsets.UTF_8).buffered(256 * 1024).use(block)
    }

    // ------------------------------------------------------------------------------------
    // EPG
    // ------------------------------------------------------------------------------------

    fun epgUrlsFor(playlist: PlaylistEntity): List<String> {
        val source = playlist.epgUrl.ifBlank { playlist.detectedEpgUrl }
        return source.split(',', '\n', ' ').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    /** @return number of programmes stored */
    suspend fun syncEpg(playlist: PlaylistEntity, days: Int, report: ProgressReporter): Int = withContext(Dispatchers.IO) {
        val sources = epgUrlsFor(playlist)
        if (sources.isEmpty()) return@withContext 0
        val wanted = dao.epgChannelIds(playlist.id).toHashSet()
        if (wanted.isEmpty()) return@withContext 0
        val catchupDays = dao.catchupWindows(playlist.id).associate { it.epgChannelId to it.days }

        val files = ArrayList<File>()
        try {
            sources.forEachIndexed { index, url ->
                val file = File(tempDir, "epg-${playlist.id}-$index.xml")
                val label = if (sources.size > 1) "TV guide ${index + 1}/${sources.size}" else "TV guide"
                try {
                    downloader.download(url, file, playlist.userAgent) { read, total ->
                        report("Downloading $label… ${formatBytes(read)}", total?.let { read.toFloat() / it })
                    }
                    files += file
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "EPG download failed: $url", e)
                    file.delete()
                    if (sources.size == 1) throw IOException("TV guide download failed: ${e.message}", e)
                }
            }
            if (files.isEmpty()) throw IOException("No TV guide could be downloaded")

            report("Importing TV guide…", null)
            val job = currentCoroutineContext().job
            importEpg(playlist.id, files, wanted, catchupDays, days, job, report)
        } finally {
            files.forEach { it.delete() }
        }
    }

    private fun importEpg(
        pid: Long, files: List<File>, wanted: Set<String>, catchupDays: Map<String, Int>, days: Int, job: Job,
        report: ProgressReporter,
    ): Int {
        val now = System.currentTimeMillis()
        val defaultFrom = now - 6 * HOUR
        val to = now + days * 24 * HOUR
        // Channels with an archive keep past programmes so they can be replayed.
        fun fromFor(epgId: String): Long {
            val archive = catchupDays[epgId]?.coerceAtMost(MAX_CATCHUP_GUIDE_DAYS) ?: return defaultFrom
            return minOf(defaultFrom, now - archive * 24 * HOUR)
        }
        // Which XMLTV channel feeds each playlist channel, and which programmes were already stored.
        val matcher = EpgMatcher(wanted)
        val batch = ArrayList<ProgrammeEntity>(BATCH)
        var stored = 0

        db.runInTransaction {
            dao.deleteProgrammes(pid)
            for (file in files) {
                try {
                    file.inputStream().gunzipIfNeeded().use { input ->
                        XmltvParser { Xml.newPullParser() }.parse(
                            input,
                            onChannel = { ch -> matcher.onChannel(ch.id, ch.displayNames) },
                            acceptProgramme = accept@{ channel, start, stop ->
                                if (!job.isActive) throw CancellationException("Sync cancelled")
                                if (start >= to) return@accept false
                                matcher.targetsFor(channel).any { stop > fromFor(it) }
                            },
                        ) { prog ->
                            for (target in matcher.targetsFor(prog.channelId)) {
                                if (prog.stopMs <= fromFor(target) || !matcher.isNew(target, prog.startMs)) continue
                                batch += ProgrammeEntity(
                                    playlistId = pid,
                                    channelId = target,
                                    startMs = prog.startMs,
                                    stopMs = prog.stopMs,
                                    title = prog.title,
                                    description = prog.description,
                                    category = prog.category,
                                )
                            }
                            if (batch.size >= BATCH) {
                                dao.insertProgrammes(batch)
                                stored += batch.size
                                batch.clear()
                                if (stored % (BATCH * 10) == 0) report("Importing TV guide… ${"%,d".format(stored)} programmes", null)
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Keep everything parsed up to a malformed section rather than discarding it all.
                    Log.w(TAG, "EPG parse error in ${file.name}", e)
                }
            }
            if (batch.isNotEmpty()) {
                dao.insertProgrammes(batch)
                stored += batch.size
            }
        }
        return stored
    }

    private fun ContentKind.label() = when (this) {
        ContentKind.LIVE -> "channels"
        ContentKind.MOVIE -> "movies"
        ContentKind.SERIES -> "series"
    }

    /** Short stable ids for M3U entries (64 bits of SHA-1 keeps collisions negligible). */
    private class StableId {
        private val digest = MessageDigest.getInstance("SHA-1")
        private val hex = "0123456789abcdef".toCharArray()
        fun of(value: String): String {
            val bytes = digest.digest(value.toByteArray())
            val out = CharArray(16)
            for (i in 0 until 8) {
                val b = bytes[i].toInt() and 0xff
                out[i * 2] = hex[b ushr 4]
                out[i * 2 + 1] = hex[b and 0x0f]
            }
            return String(out)
        }
    }

    companion object {
        private const val TAG = "PlaylistSyncer"
        private const val BATCH = 2000
        private const val HOUR = 60 * 60 * 1000L
        const val UNCATEGORIZED = "Uncategorized"

        /** Archive length assumed for M3U channels that declare catch-up without `catchup-days`. */
        private const val DEFAULT_M3U_CATCHUP_DAYS = 7

        /** Upper bound on past guide data kept for catch-up, to keep the database small. */
        const val MAX_CATCHUP_GUIDE_DAYS = 7

        /** "Show Name S01E02 ..." / "Show Name - S1 E2" */
        private val EPISODE_REGEX = Regex("""^(.*?)[\s._\-|:]*\b[Ss](\d{1,2})[\s._\-]*[Ee](\d{1,4})(?!\d)""")

        fun formatBytes(bytes: Long): String = when {
            bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            bytes >= 1024 -> "${bytes / 1024} KB"
            else -> "$bytes B"
        }
    }
}
