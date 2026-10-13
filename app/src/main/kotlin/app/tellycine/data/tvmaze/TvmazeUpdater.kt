package app.tellycine.data.tvmaze

import android.util.Log
import app.tellycine.core.ShowNames
import app.tellycine.core.tvmaze.Tvmaze
import app.tellycine.core.tvmaze.TvmazeShow
import app.tellycine.data.db.SeriesRef
import app.tellycine.data.db.TellycineDatabase
import app.tellycine.data.db.TvmazeShowEntity
import app.tellycine.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Keeps series' air dates from TVmaze up to date while that's turned on (it's opt-in, as it sends
 * series names to TVmaze). Series with the same cleaned-up name and year ([ShowNames.key]) share
 * one lookup, kept in [TvmazeShowEntity] across refreshes. The first check looks every series up,
 * which takes a while at TVmaze's pace; after that, a check only looks up:
 * - series not looked up before (new to a playlist, or renamed);
 * - shows TVmaze has changed since the last check, from its list of changed shows (one request);
 * - shows whose next episode has aired since, which doesn't change them on TVmaze;
 * - series Tellycine saw gain episodes since it last asked ([app.tellycine.data.db.StreamEntity.changedAt]);
 * - series TVmaze didn't have, once a month, in case it has them now.
 * Ended shows are left alone unless TVmaze changes them.
 */
class TvmazeUpdater(
    private val db: TellycineDatabase,
    private val settings: SettingsRepository,
    val client: TvmazeClient,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    data class Status(
        /** Series to look up: different names (and years) across the playlists. */
        val series: Int = 0,
        val found: Int = 0,
        val notFound: Int = 0,
        /** Whether a check is going on, and how many of its [todo] series it has done. */
        val running: Boolean = false,
        val done: Int = 0,
        val todo: Int = 0,
        /** When the last check finished. */
        val checkedAt: Long? = null,
        /** Why the last check stopped, if it did. */
        val error: String? = null,
    )

    private class Series(val key: String, val title: String, val year: Int?, val refs: List<SeriesRef>) {
        val changedAt = refs.mapNotNull { it.changedAt }.maxOrNull()
    }

    private val dao = db.tvmaze()
    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val oneAtATime = Mutex()

    /** Checks while turned on: shortly after launch, when turned on, and when asked ([requestCheck]). */
    fun start() {
        scope.launch {
            var launching = true
            settings.settings.map { it.tvmazeAirDates }.distinctUntilChanged().collectLatest { on ->
                if (!on) {
                    launching = false
                    return@collectLatest
                }
                count()
                // Let the first screen open before any lookups.
                if (launching) delay(LAUNCH_DELAY)
                launching = false
                requests.tryReceive()
                check()
                for (request in requests) check()
            }
        }
    }

    /** Checks again soon, for example after a playlist refresh. Does nothing while turned off. */
    fun requestCheck() {
        requests.trySend(Unit)
    }

    /** Looks up what's due (see the class), then gives every series its air date. */
    suspend fun check() = oneAtATime.withLock {
        val now = clock()
        _status.update { it.copy(running = true, done = 0, todo = 0, error = null) }
        try {
            val series = group(dao.series())
            val cache = dao.shows().associateBy { it.showKey }
            // What's already known goes in straight away, for series new to a playlist, say.
            for (s in series) cache[s.key]?.let { setAired(s, it.lastAired) }

            val lastCheck = settings.tvmazeCheckedAt()
            val matched = cache.values.any { it.tvmazeId != null }
            val listChanges = matched && (lastCheck == null || now - lastCheck >= HOUR)
            val changed = if (listChanges) client.updates(sinceFor(lastCheck?.let { now - it })) else emptyMap()

            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            // Series never looked up go first, so they can be sorted soonest.
            val todo = series.filter { due(it, cache[it.key], changed, today, now) }.sortedBy { cache[it.key] != null }
            var found = series.count { cache[it.key]?.tvmazeId != null }
            var notFound = series.count { cache[it.key]?.let { show -> show.tvmazeId == null } == true }
            _status.update { it.copy(todo = todo.size, series = series.size, found = found, notFound = notFound) }
            for ((i, s) in todo.withIndex()) {
                val before = cache[s.key]
                val show = before?.tvmazeId?.let { client.show(it) } ?: find(s)
                val entity = TvmazeShowEntity(s.key, show?.id, show?.updated, show?.lastAired, show?.nextAired, show?.ended ?: false, clock())
                dao.upsert(entity)
                setAired(s, entity.lastAired)
                when {
                    before == null -> Unit
                    before.tvmazeId != null -> found--
                    else -> notFound--
                }
                if (show != null) found++ else notFound++
                _status.update { it.copy(done = i + 1, found = found, notFound = notFound) }
            }

            if (listChanges || !matched) settings.setTvmazeCheckedAt(now)
            // Forget shows no playlist has had for a while.
            val keys = series.mapTo(HashSet()) { it.key }
            cache.values.filter { it.showKey !in keys && now - it.checkedAt > FORGET_AFTER }.map { it.showKey }
                .chunked(500).forEach { dao.delete(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "TVmaze check stopped", e)
            _status.update { it.copy(error = "Couldn't reach TVmaze. Tellycine will try again later.") }
        } finally {
            withContext(NonCancellable) {
                _status.update { it.copy(running = false) }
                count()
            }
        }
    }

    /** Counts series, and how many were found, for [status]. */
    private suspend fun count() {
        val keys = group(dao.series()).map { it.key }
        val cache = dao.shows().associateBy { it.showKey }
        val checkedAt = settings.tvmazeCheckedAt()
        _status.update { st ->
            st.copy(
                series = keys.size,
                found = keys.count { cache[it]?.tvmazeId != null },
                notFound = keys.count { cache[it]?.let { show -> show.tvmazeId == null } == true },
                checkedAt = checkedAt,
            )
        }
    }

    private fun group(refs: List<SeriesRef>): List<Series> = refs
        .groupBy { ref ->
            val cleaned = ShowNames.clean(ref.name)
            val year = ShowNames.yearOf(ref.year) ?: ShowNames.yearOf(ref.released) ?: cleaned.year
            Triple(ShowNames.key(cleaned.title, year), cleaned.title, year)
        }
        .map { (k, refs) -> Series(k.first, k.second, k.third, refs) }

    private fun due(s: Series, old: TvmazeShowEntity?, changed: Map<Int, Long>, today: LocalDate, now: Long): Boolean {
        if (old == null) return true
        val id = old.tvmazeId ?: return now - old.checkedAt > RETRY_NOT_FOUND
        if ((changed[id] ?: 0) > (old.updated ?: 0)) return true
        // Seen to gain episodes since (a date in the future is the provider's clock, not news).
        if (s.changedAt != null && s.changedAt > old.checkedAt && s.changedAt <= now) return true
        val next = old.nextAired?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
        val checkedOn = Instant.ofEpochMilli(old.checkedAt).atZone(zone).toLocalDate()
        return (next < today && checkedOn <= next) || (next == today && checkedOn < today)
    }

    /** The show that is [s]: TVmaze's best match by name, or with a year, another show of that name and year. */
    private suspend fun find(s: Series): TvmazeShow? {
        val best = client.bestMatch(s.title) ?: return null
        if (Tvmaze.matches(best, s.title, s.year)) return best
        // A remake, say: another show of the same name may be from the right year.
        if (s.year == null) return null
        val other = client.search(s.title).firstOrNull { it.id != best.id && Tvmaze.matches(it, s.title, s.year) } ?: return null
        return client.show(other.id)
    }

    private suspend fun setAired(s: Series, aired: String?) {
        for (ref in s.refs) if (ref.aired != aired) dao.setAired(ref.playlistId, ref.streamId, aired)
    }

    private fun sinceFor(age: Long?): String? = when {
        age == null || age > 30 * DAY -> null
        age > 7 * DAY -> "month"
        age > DAY -> "week"
        else -> "day"
    }

    companion object {
        private const val TAG = "TvmazeUpdater"
        private const val HOUR = 60 * 60 * 1000L
        private const val DAY = 24 * HOUR
        private const val RETRY_NOT_FOUND = 30 * DAY
        private const val FORGET_AFTER = 90 * DAY
        private const val LAUNCH_DELAY = 20_000L
    }
}
