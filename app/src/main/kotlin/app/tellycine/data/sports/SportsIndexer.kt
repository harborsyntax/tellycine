package app.tellycine.data.sports

import android.os.Process
import android.util.Log
import app.tellycine.core.sports.AiringKind
import app.tellycine.core.sports.EventChannels
import app.tellycine.core.sports.Sport
import app.tellycine.core.sports.SportClassifier
import app.tellycine.core.sports.SportMatch
import app.tellycine.data.db.ProgrammeHead
import app.tellycine.data.db.SportAiringEntity
import app.tellycine.data.db.SportIndexEntity
import app.tellycine.data.db.TellycineDatabase
import app.tellycine.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors

/**
 * Finds the sport in each playlist's guide and channel names for the Sports Hub, after each
 * refresh, and keeps it in a table of its own ([SportAiringEntity]).
 *
 * It reads the guide a few thousand programmes at a time, and only the descriptions of those that
 * could be sport, on a thread of its own at background priority; then swaps the playlist's sport
 * in, in one short transaction. With the hub turned off it does nothing, and the table is emptied.
 */
class SportsIndexer(
    private val db: TellycineDatabase,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val dao = db.sports()
    private val classifier = SportClassifier()
    private val mutex = Mutex()

    private val worker = Executors.newSingleThreadExecutor { work ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            work.run()
        }, "tellycine-sports").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val _working = MutableStateFlow(0)

    /** Whether it's finding sport in a guide right now, for the hub to say so while it's empty. */
    val working: StateFlow<Boolean> = _working.map { it > 0 }.stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * Keeps up with the hub being turned on and off, and finds sport again in playlists last
     * looked through with older rules (or never). Called once, as the app starts.
     */
    fun start() {
        scope.launch {
            var starting = true
            settings.settings.map { it.sportsHub }.distinctUntilChanged().collect { on ->
                // As the app starts, its first screen goes first.
                if (on && starting) delay(STARTUP_DELAY_MS)
                starting = false
                if (on) indexOutdated() else clear()
            }
        }
    }

    /**
     * Finds the sport in [playlistId]'s guide and channels again, after it's refreshed: in what's
     * new in the guide, or with [full], all of it (its channels have changed).
     */
    fun refresh(playlistId: Long, full: Boolean) {
        scope.launch {
            if (settings.current().sportsHub) index(playlistId, full)
        }
    }

    private suspend fun indexOutdated() {
        val current = try {
            withContext(worker) { dao.indexedWith(VERSION) }.toSet()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't tell what's been looked through", e)
            return
        }
        for (p in db.playlists().getAll()) if (p.id !in current) index(p.id, full = true)
    }

    private suspend fun clear() = mutex.withLock {
        try {
            withContext(worker) { dao.clear() }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't forget the sport found", e)
        }
    }

    /**
     * Finds the sport in [playlistId]'s guide and channel names, as of [now], and keeps it: in the
     * programmes added since it last looked, or with [full], in all of them.
     */
    suspend fun index(playlistId: Long, full: Boolean = true, now: Long = System.currentTimeMillis()) = mutex.withLock {
        _working.update { it + 1 }
        try {
            withContext(worker) { indexBlocking(playlistId, full, now) }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't find sport in playlist $playlistId", e)
        } finally {
            _working.update { it - 1 }
        }
    }

    private fun indexBlocking(playlistId: Long, full: Boolean, now: Long) {
        val started = System.nanoTime()
        val from = now - LIVE_FOR_MS
        // What was found before stays, if it was found with these rules: then only what's new is read.
        val state = dao.indexState(playlistId)?.takeIf { !full && it.version == VERSION }
        var lastId = state?.lastProgrammeId ?: 0L
        val zone = ZoneId.systemDefault()
        val channels = dao.liveChannels(playlistId)
        // Each guide channel by the name of the first channel that uses it.
        val hints = HashMap<String, SportClassifier.ChannelHint>()
        for (ch in channels) {
            val id = ch.epgChannelId ?: continue
            if (id !in hints) hints[id] = classifier.channel(ch.name)
        }

        // The programmes that could be sport. Titles and categories repeat a lot in guides, so
        // what's been decided about each is kept for the next time it comes up.
        val candidates = ArrayList<ProgrammeHead>()
        val sportyCategory = HashMap<String, Boolean>()
        val sportyTitle = HashMap<String, Boolean>()
        var read = 0
        dao.programmeHeads(playlistId, lastId, from).use { c ->
            while (c.moveToNext()) {
                read++
                lastId = maxOf(lastId, c.getLong(0))
                val channelId = c.getString(1)
                val title = c.getString(4)
                val category = c.getString(5)
                val hint = hints[channelId] ?: SportClassifier.ChannelHint.NONE
                val candidate = hint.isSport ||
                    (category != null && sportyCategory.getOrPut(category) { classifier.isCandidate("", category, SportClassifier.ChannelHint.NONE) }) ||
                    sportyTitle.getOrPut(title) { classifier.isCandidate(title, null, SportClassifier.ChannelHint.NONE) }
                if (candidate) candidates += ProgrammeHead(c.getLong(0), channelId, c.getLong(2), c.getLong(3), title, category)
            }
        }
        val descriptions = HashMap<Long, String?>()
        for (ids in candidates.map { it.id }.chunked(MAX_VARIABLES)) {
            for (d in dao.descriptions(ids)) descriptions[d.id] = d.description
        }

        val rows = ArrayList<SportAiringEntity>()
        // The same programme, described the same way on a channel like it, is the same sport.
        val decided = HashMap<List<Any?>, SportMatch?>()
        for (p in candidates) {
            val date = Instant.ofEpochMilli(p.startMs).atZone(zone)
            val description = descriptions[p.id]
            val hint = hints[p.channelId] ?: SportClassifier.ChannelHint.NONE
            val match = decided.getOrPut(listOf(p.title, p.category, description, hint, date.monthValue, date.year)) {
                classifier.classify(p.title, p.category, description, hint, date.monthValue, date.year)
            } ?: continue
            if (match.kind == AiringKind.MAGAZINE) continue
            rows += SportAiringEntity(
                playlistId = playlistId,
                channelId = p.channelId,
                startMs = p.startMs,
                stopMs = p.stopMs,
                sport = match.sport,
                kind = match.kind,
                title = match.title,
                competition = match.competition,
                session = match.session,
                home = match.home,
                away = match.away,
                programmeTitle = p.title,
                detail = description?.take(DETAIL_LENGTH),
                streamId = null,
            )
        }

        // Events that channels' names announce.
        var events = 0
        for (ch in channels) {
            val e = EventChannels.parse(ch.name, now) ?: continue
            val start = e.startMs ?: 0L
            val stop = if (start == 0L) 0L else start + durationOf(e.sport)
            if (start != 0L && stop <= from) continue
            events++
            rows += SportAiringEntity(
                playlistId = playlistId,
                channelId = "#" + ch.streamId,
                startMs = start,
                stopMs = stop,
                sport = e.sport,
                kind = AiringKind.LIVE,
                title = e.title,
                competition = null,
                session = null,
                home = e.home,
                away = e.away,
                programmeTitle = ch.name,
                detail = null,
                streamId = ch.streamId,
            )
        }

        db.runInTransaction {
            if (state == null) {
                dao.deleteAirings(playlistId)
            } else {
                // Keeps what's still in the guide as it was, and what hasn't finished.
                dao.deleteGoneAirings(playlistId)
                dao.deleteEndedAndEventChannels(playlistId, from)
            }
            // Unless the playlist was removed in the meantime.
            if (db.playlists().getBlocking(playlistId) != null) {
                rows.chunked(BATCH).forEach(dao::insert)
                dao.setIndexState(SportIndexEntity(playlistId, lastId, VERSION))
            }
        }
        Log.i(
            TAG,
            "Playlist $playlistId: ${rows.size - events} sport programmes in $read ${if (state == null) "" else "new "}programmes, " +
                "$events event channels, in ${(System.nanoTime() - started) / 1_000_000} ms",
        )
    }

    companion object {
        private const val TAG = "SportsIndexer"

        /**
         * Goes up when the rules for finding sport change, so it's found again everywhere. 2: from
         * a real UK guide: sport the title doesn't name needs more to be an event, seasons and
         * past years are replays, news channels, "Hlts".
         */
        const val VERSION = 2

        /** How long ago an event can have started and still be on: a Test match or a golf round is long. */
        private const val LIVE_FOR_MS = 8 * 60 * 60 * 1000L

        private const val BATCH = 1000
        private const val STARTUP_DELAY_MS = 5_000L
        private const val MAX_VARIABLES = 900
        private const val DETAIL_LENGTH = 300

        /** How long an event a channel's name announces lasts, near enough, as it doesn't say. */
        fun durationOf(sport: Sport): Long = when (sport) {
            Sport.FOOTBALL -> 2 * HOUR
            Sport.MOTOGP -> HOUR
            Sport.F1 -> 2 * HOUR
            Sport.GOLF -> 5 * HOUR
            Sport.DARTS, Sport.BOXING -> 4 * HOUR
            else -> 3 * HOUR
        }

        private const val HOUR = 60 * 60 * 1000L
    }
}
