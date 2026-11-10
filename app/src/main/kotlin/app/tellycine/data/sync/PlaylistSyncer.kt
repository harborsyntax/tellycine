package app.tellycine.data.sync

import android.content.Context
import android.os.Process
import android.util.Log
import android.util.Xml
import app.tellycine.core.ContentKind
import app.tellycine.core.ReleaseDates
import app.tellycine.core.gunzipIfNeeded
import app.tellycine.core.m3u.M3uHeader
import app.tellycine.core.m3u.M3uParser
import app.tellycine.core.xmltv.EpgMatcher
import app.tellycine.core.xmltv.GuideUrls
import app.tellycine.core.xmltv.XmltvParser
import app.tellycine.core.xtream.XtreamAccount
import app.tellycine.core.xtream.XtreamItem
import app.tellycine.core.xtream.XtreamParser
import app.tellycine.core.xtream.XtreamUrls
import app.tellycine.data.db.CatchupWindow
import app.tellycine.data.db.CategoryEntity
import app.tellycine.data.db.EpisodeEntity
import app.tellycine.data.db.GuideSourceEntity
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import app.tellycine.data.db.StagedProgrammeEntity
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.db.TellycineDatabase
import app.tellycine.data.net.Downloader
import app.tellycine.data.net.FileVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.Executors

typealias ProgressReporter = (message: String, fraction: Float?) -> Unit

data class ContentSyncResult(
    val live: Int,
    val movies: Int,
    val series: Int,
    val detectedEpgUrl: String,
    val account: XtreamAccount?,
)

/** What refreshing a playlist's TV guide did. */
sealed interface GuideRefresh {
    /** It was imported: [programmes] of them. */
    data class Imported(val programmes: Int) : GuideRefresh

    /** It hadn't changed since it was last imported, for the same channels, so it wasn't imported again. */
    data object Unchanged : GuideRefresh

    /** There was nothing to import: no guide, or no channels with a guide id. */
    data object None : GuideRefresh
}

/**
 * Downloads and imports playlists and EPG data.
 *
 * Large sources are first downloaded to a temporary file and then imported. A playlist is
 * imported in a single database transaction: thanks to WAL mode the UI keeps showing the previous
 * data until the import commits, and a failed import never leaves a half-empty playlist behind.
 * A guide, often far bigger, is staged first and then swapped in, in a short transaction ([syncEpg]).
 */
class PlaylistSyncer(
    private val context: Context,
    private val db: TellycineDatabase,
    private val downloader: Downloader,
) {
    private val dao = db.content()
    private val tempDir get() = File(context.cacheDir, "sync").apply { mkdirs() }

    /**
     * Where refreshes read what they downloaded and write it to the database: a thread of their
     * own at background priority, so the app and the player get the processor first. (On a TV box,
     * reading a big guide can keep a core busy for minutes.) Refreshes run one at a time.
     */
    private val importer = Executors.newSingleThreadExecutor { work ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            work.run()
        }, "tellycine-import").apply { isDaemon = true }
    }.asCoroutineDispatcher()

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
            return withContext(importer) { importM3u(playlist, file, job, report) }
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

            withContext(importer) { db.runInTransaction {
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
            } }

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
        tmdbId = tmdbId,
    )

    private inline fun readJson(file: File?, block: (java.io.Reader) -> Unit) {
        if (file == null || !file.exists() || file.length() == 0L) return
        file.inputStream().gunzipIfNeeded().reader(Charsets.UTF_8).buffered(256 * 1024).use(block)
    }

    // ------------------------------------------------------------------------------------
    // EPG
    // ------------------------------------------------------------------------------------

    fun epgUrlsFor(playlist: PlaylistEntity): List<String> =
        GuideUrls.toLoad(own = playlist.epgUrl, supplied = playlist.detectedEpgUrl, combine = playlist.epgCombine)

    /**
     * What importing [playlist]'s guide depends on besides the guides themselves, as a fingerprint:
     * its channels' guide ids and catch-up, the [days] kept, and which guides it loads. The guide
     * needs importing again when this changes ([PlaylistEntity.epgInputs]).
     */
    fun guideInputs(playlist: PlaylistEntity, days: Int): String =
        guideInputs(playlist, days, dao.epgChannelIds(playlist.id), dao.catchupWindows(playlist.id))

    private fun guideInputs(playlist: PlaylistEntity, days: Int, wanted: Collection<String>, catchup: List<CatchupWindow>): String {
        val text = buildString {
            append("import $GUIDE_IMPORT_VERSION, $days days\n")
            epgUrlsFor(playlist).forEach { append(it).append('\n') }
            append("channels\n")
            wanted.sorted().forEach { append(it).append('\n') }
            append("catch-up\n")
            catchup.sortedBy { it.epgChannelId }.forEach { append(it.epgChannelId).append('=').append(it.days).append('\n') }
        }
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /**
     * Refreshes [playlist]'s TV guide. Guides that haven't changed since they were last imported,
     * for the same channels, aren't imported again: the server is asked whether they have changed
     * where it said how to ask, and the rest are compared with what was imported before.
     */
    suspend fun syncEpg(playlist: PlaylistEntity, days: Int, report: ProgressReporter): GuideRefresh = withContext(Dispatchers.IO) {
        val pid = playlist.id
        val sources = epgUrlsFor(playlist)
        if (sources.isEmpty()) return@withContext GuideRefresh.None
        val wanted = dao.epgChannelIds(pid).toHashSet()
        val playlists = db.playlists()
        if (wanted.isEmpty()) {
            playlists.setGuideImported(pid, System.currentTimeMillis(), 0, null, false, null)
            return@withContext GuideRefresh.None
        }
        val windows = dao.catchupWindows(pid)
        val catchupDays = windows.associate { it.epgChannelId to it.days }
        val inputs = guideInputs(playlist, days, wanted, windows)
        val before = dao.guideSources(pid).associateBy { it.url }
        // Whether the guide imported last time can still be the one these would import.
        val maybeUnchanged = playlist.epgInputs == inputs && !playlist.epgTrimmed && sources.all { it in before }

        val files = ArrayList<File>()
        try {
            val downloads = LinkedHashMap<String, GuideSourceEntity>()
            /**
             * Downloads the [index]th guide, unless the server says it hasn't changed when [ask]ed:
             * then false. Null if it couldn't be downloaded, when there are others.
             */
            suspend fun fetch(index: Int, url: String, ask: Boolean): Boolean? {
                val file = File(tempDir, "epg-$pid-$index.xml")
                val label = if (sources.size > 1) "TV guide ${index + 1}/${sources.size}" else "TV guide"
                val since = before[url]?.takeIf { ask }?.let { FileVersion(it.etag, it.lastModified) }
                return try {
                    val got = downloader.download(url, file, playlist.userAgent, since) { read, total ->
                        report("Downloading $label… ${formatBytes(read)}", total?.let { read.toFloat() / it })
                    } ?: return false
                    files += file
                    downloads[url] = GuideSourceEntity(pid, url, got.version.etag, got.version.lastModified, got.sha256)
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "EPG download failed: $url", e)
                    file.delete()
                    if (sources.size == 1) throw IOException("TV guide download failed: ${e.message}", e)
                    null
                }
            }

            // Each guide, or the server's word that it hasn't changed since last time.
            val notModified = sources.withIndex().filter { (index, url) -> fetch(index, url, ask = maybeUnchanged) == false }.map { it.value }
            val unchanged = maybeUnchanged && sources.all { url ->
                url in notModified || downloads[url]?.sha256 == before.getValue(url).sha256
            }
            if (unchanged) {
                playlists.setGuideChecked(pid, System.currentTimeMillis())
                return@withContext GuideRefresh.Unchanged
            }
            // Others have, so these are needed after all.
            sources.forEachIndexed { index, url -> if (url in notModified) fetch(index, url, ask = false) }
            if (files.isEmpty()) throw IOException("No TV guide could be downloaded")

            report("Importing TV guide…", null)
            val job = currentCoroutineContext().job
            val staged = withContext(importer) { stageEpg(pid, files, wanted, catchupDays, days, job, report) }

            // Swapped in at normal priority, as the rest of the app waits for it.
            report("Updating TV guide…", null)
            if (!job.isActive) throw CancellationException("Sync cancelled")
            db.runInTransaction {
                dao.deleteUnstagedProgrammes(pid)
                dao.insertStagedProgrammes(pid)
                dao.deleteGuideSources(pid)
                dao.insertGuideSources(downloads.values.toList())
                playlists.setGuideImported(pid, System.currentTimeMillis(), staged.programmes, inputs, staged.trimmed, staged.coveredUntil)
            }
            GuideRefresh.Imported(staged.programmes)
        } finally {
            files.forEach { it.delete() }
            dao.clearStagedProgrammes()
        }
    }

    /** What [stageEpg] staged. */
    private class StagedGuide(val programmes: Int, val trimmed: Boolean, val coveredUntil: Long?)

    /**
     * Reads the guides in [files] into programmes_staging, a few thousand programmes at a time,
     * each lot committed on its own: nothing waits for the database for long while a big guide is
     * read, and the guide shown stays as it was until the staged one is swapped in.
     */
    private fun stageEpg(
        pid: Long, files: List<File>, wanted: Set<String>, catchupDays: Map<String, Int>, days: Int, job: Job,
        report: ProgressReporter,
    ): StagedGuide {
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
        val batch = ArrayList<StagedProgrammeEntity>(BATCH)
        // When each channel's last programme ends.
        val ends = HashMap<String, Long>()
        var trimmed = false
        var stored = 0

        dao.clearStagedProgrammes()
        for (file in files) {
            try {
                file.inputStream().gunzipIfNeeded().use { input ->
                    XmltvParser { Xml.newPullParser() }.parse(
                        input,
                        onChannel = { ch -> matcher.onChannel(ch.id, ch.displayNames) },
                        acceptProgramme = accept@{ channel, start, stop ->
                            if (!job.isActive) throw CancellationException("Sync cancelled")
                            val targets = matcher.targetsFor(channel)
                            if (start >= to) {
                                if (targets.isNotEmpty()) trimmed = true
                                return@accept false
                            }
                            targets.any { stop > fromFor(it) }
                        },
                    ) { prog ->
                        for (target in matcher.targetsFor(prog.channelId)) {
                            if (prog.stopMs <= fromFor(target) || !matcher.isNew(target, prog.startMs)) continue
                            batch += StagedProgrammeEntity(
                                playlistId = pid,
                                channelId = target,
                                startMs = prog.startMs,
                                stopMs = prog.stopMs,
                                title = prog.title,
                                description = prog.description,
                                category = prog.category,
                            )
                            if (prog.stopMs > (ends[target] ?: 0L)) ends[target] = prog.stopMs
                        }
                        if (batch.size >= BATCH) {
                            dao.stageProgrammes(batch)
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
            dao.stageProgrammes(batch)
            stored += batch.size
        }
        // Until when three in four channels with a guide have one.
        val coveredUntil = ends.values.sorted().let { it.getOrNull(it.size / 4) }
        return StagedGuide(stored, trimmed, coveredUntil)
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

        /**
         * Goes up when importing a guide changes, so guides are imported again rather than found
         * unchanged. 2: every category a programme has, not only the first (for the Sports Hub).
         */
        private const val GUIDE_IMPORT_VERSION = 2
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
