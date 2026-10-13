package app.tellycine.data.tmdb

import android.util.Log
import androidx.room.withTransaction
import app.tellycine.core.ContentKind
import app.tellycine.core.ShowNames
import app.tellycine.core.tmdb.Tmdb
import app.tellycine.core.tmdb.TmdbKind
import app.tellycine.core.tmdb.TmdbTitle
import app.tellycine.data.db.TellycineDatabase
import app.tellycine.data.db.TitleRef
import app.tellycine.data.db.TmdbTitleEntity
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
 * Keeps movies' and series' TMDB scores, posters and (for series) air dates up to date while
 * details from TMDB are turned on, with the user's own API key. It's opt-in, as it sends titles'
 * names to TMDB. Titles with the same cleaned-up name and year ([keyOf]) share one lookup, kept in
 * [TmdbTitleEntity] across refreshes; a provider's TMDB id saves searching for it.
 *
 * The first check looks every title up, which takes a while for a big playlist. After that, a
 * check only looks up titles that are new, or due again: films after three months, series whose
 * next episode has aired or that Tellycine saw gain episodes, other running series after a week,
 * finished ones after three months, and titles TMDB didn't have after a month. Nothing is kept for
 * more than the six months TMDB allows.
 */
class TmdbUpdater(
    private val db: TellycineDatabase,
    private val settings: SettingsRepository,
    val client: TmdbClient,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    data class Status(
        /** Titles to look up: different names (and years) across the playlists. */
        val titles: Int = 0,
        val found: Int = 0,
        val notFound: Int = 0,
        /** Whether a check is going on, and how many of its [todo] titles it has done. */
        val running: Boolean = false,
        val done: Int = 0,
        val todo: Int = 0,
        /** When TMDB was last asked about any of them. */
        val checkedAt: Long? = null,
        /** Why the last check stopped, if it did. */
        val error: String? = null,
    )

    private class Title(val key: String, val kind: ContentKind, val name: String, val year: Int?, val refs: List<TitleRef>) {
        /** The TMDB id the provider gives it (or that was found before), the one most of its copies have. */
        val knownId = refs.mapNotNull { it.tmdbId }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val changedAt = refs.mapNotNull { it.changedAt }.maxOrNull()
    }

    private val dao = db.tmdb()
    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val oneAtATime = Mutex()

    /** Checks while turned on: shortly after launch, when turned on, and when asked ([requestCheck]). */
    fun start() {
        scope.launch {
            forgetOld()
            var launching = true
            var wasOn = false
            settings.settings.map { it.tmdb to it.tmdbApiKey }.distinctUntilChanged().collectLatest { (on, _) ->
                if (!on) {
                    launching = false
                    // Turned off: titles show only what their playlists say. (Off at launch, there's nothing to clear.)
                    if (wasOn) dao.clearFromStreams()
                    wasOn = false
                    return@collectLatest
                }
                wasOn = true
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

    /** Looks up what's due (see the class), then gives every title what's known about it. */
    suspend fun check() = oneAtATime.withLock {
        val key = settings.current().takeIf { it.tmdb }?.tmdbApiKey ?: return@withLock
        val now = clock()
        _status.update { it.copy(running = true, done = 0, todo = 0, error = null) }
        try {
            val titles = group(dao.titlesToLookUp())
            val cache = dao.titles().associateBy { it.titleKey }.toMutableMap()
            // What's already known goes in straight away, for titles new to a playlist, say.
            write(titles.mapNotNull { t -> cache[t.key]?.let { t to it } })

            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            // Titles never looked up go first, so they get their details soonest.
            val todo = titles.filter { due(it, cache[it.key], today, now) }.sortedBy { cache[it.key] != null }
            fun found() = titles.count { cache[it.key]?.tmdbId != null }
            fun notFound() = titles.count { cache[it.key]?.let { t -> t.tmdbId == null && t.checkedAt > 0 } == true }
            _status.update { it.copy(todo = todo.size, titles = titles.size, found = found(), notFound = notFound()) }
            val pending = ArrayList<Pair<Title, TmdbTitleEntity>>()
            for ((i, t) in todo.withIndex()) {
                val entity = lookUp(key, t, cache[t.key])
                dao.upsert(entity)
                cache[t.key] = entity
                pending += t to entity
                // In batches, so the lists showing these titles don't reload for every one.
                if (pending.size >= BATCH || i == todo.lastIndex) {
                    write(pending)
                    pending.clear()
                    _status.update { it.copy(done = i + 1, found = found(), notFound = notFound(), checkedAt = clock()) }
                }
            }

            // Forget titles no playlist has had for a while.
            val keys = titles.mapTo(HashSet()) { it.key }
            cache.values.filter { it.titleKey !in keys && now - it.checkedAt > FORGET_AFTER }.map { it.titleKey }
                .chunked(500).forEach { dao.delete(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: TmdbClient.KeyRejected) {
            _status.update { it.copy(error = KEY_REJECTED) }
        } catch (e: Exception) {
            Log.w(TAG, "TMDB check stopped", e)
            _status.update { it.copy(error = "Couldn't reach TMDB. Tellycine will try again later.") }
        } finally {
            withContext(NonCancellable) {
                _status.update { it.copy(running = false) }
                count()
            }
        }
    }

    /** Counts titles, and how many were found, for [status]. */
    private suspend fun count() {
        val keys = group(dao.titlesToLookUp()).map { it.key }
        val cache = dao.titles().associateBy { it.titleKey }
        _status.update { st ->
            st.copy(
                titles = keys.size,
                found = keys.count { cache[it]?.tmdbId != null },
                notFound = keys.count { cache[it]?.let { t -> t.tmdbId == null && t.checkedAt > 0 } == true },
                checkedAt = cache.values.maxOfOrNull { it.checkedAt }?.takeIf { it > 0 },
            )
        }
    }

    /** Forgets anything TMDB said more than six months ago, whether or not it's turned on. */
    private suspend fun forgetOld() {
        val before = clock() - KEEP_AT_MOST
        dao.deleteTitlesBefore(before)
        dao.deletePagesBefore(before)
    }

    private fun group(refs: List<TitleRef>): List<Title> = refs
        .groupBy { ref -> keyOf(ref.kind, ref.name, ref.year, ref.released) }
        .map { (key, refs) ->
            val first = refs.first()
            val cleaned = ShowNames.clean(first.name)
            Title(key, first.kind, cleaned.title, yearOf(first.year, first.released, cleaned.year), refs)
        }

    private fun due(t: Title, old: TmdbTitleEntity?, today: LocalDate, now: Long): Boolean {
        if (old == null) return true
        // The provider gives an id that isn't the one found.
        if (t.knownId != null && t.knownId != old.tmdbId) return true
        val age = now - old.checkedAt
        if (age > REFRESH_AT_MOST) return true
        if (old.tmdbId == null) return age > RETRY_NOT_FOUND
        if (t.kind == ContentKind.MOVIE || old.ended) return age > SETTLED_REFRESH
        if (age > RUNNING_REFRESH) return true
        // Seen to gain episodes since (a date in the future is the provider's clock, not news).
        if (t.changedAt != null && t.changedAt > old.checkedAt && t.changedAt <= now) return true
        val next = old.nextAired?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
        val checkedOn = Instant.ofEpochMilli(old.checkedAt).atZone(zone).toLocalDate()
        return (next < today && checkedOn <= next) || (next == today && checkedOn < today)
    }

    /** Asks TMDB about [t]: by its known id, or else by searching for its name (and year). */
    private suspend fun lookUp(key: String, t: Title, old: TmdbTitleEntity?): TmdbTitleEntity {
        val kind = t.kind.tmdbKind
        val notFound = TmdbTitleEntity(t.key, t.kind, null, null, null, null, null, false, clock())
        var searched: TmdbTitle? = null
        val id = t.knownId ?: old?.tmdbId ?: find(key, kind, t.name, t.year)?.also { searched = it }?.id ?: return notFound
        // A film's search result has all the list needs; a series' page has its air dates.
        val found = searched
        if (kind == TmdbKind.MOVIE && found != null) {
            return TmdbTitleEntity(t.key, t.kind, id, found.rating.takeIf { found.votes >= MIN_VOTES }, found.poster, null, null, false, clock())
        }
        val details = client.page(key, kind, id)?.let { Tmdb.parseDetails(it, kind) } ?: return notFound
        return TmdbTitleEntity(
            titleKey = t.key,
            kind = t.kind,
            tmdbId = id,
            rating = details.rating.takeIf { details.votes >= MIN_VOTES },
            poster = details.poster,
            lastAired = details.lastAired,
            nextAired = details.nextEpisode?.airDate,
            ended = details.ended,
            checkedAt = clock(),
        )
    }

    /** The title called [name] from [year]: searched for by name, then (for a common name) with the year. */
    private suspend fun find(key: String, kind: TmdbKind, name: String, year: Int?): TmdbTitle? =
        Tmdb.bestMatch(client.search(key, kind, name), name, year)
            ?: year?.let { Tmdb.bestMatch(client.search(key, kind, name, it), name, it) }

    private class Change(val ref: TitleRef, val tmdbId: Int?, val rating: Double?, val poster: String?, val aired: String?)

    /** Gives each copy of the titles what TMDB said, where that's changed. */
    private suspend fun write(found: List<Pair<Title, TmdbTitleEntity>>) {
        val changes = found.flatMap { (t, e) ->
            val poster = Tmdb.image(e.poster, Tmdb.POSTER)
            val aired = if (t.kind == ContentKind.SERIES) e.lastAired else null
            t.refs.mapNotNull { ref ->
                val id = ref.tmdbId ?: e.tmdbId
                val same = ref.tmdbId == id && ref.tmdbRating == e.rating && ref.tmdbPoster == poster && ref.aired == aired
                if (same) null else Change(ref, id, e.rating, poster, aired)
            }
        }
        if (changes.isEmpty()) return
        db.withTransaction {
            for (c in changes) dao.setTmdb(c.ref.playlistId, c.ref.kind, c.ref.streamId, c.tmdbId, c.rating, c.poster, c.aired)
        }
    }

    companion object {
        private const val TAG = "TmdbUpdater"
        const val KEY_REJECTED = "TMDB didn't accept your API key. Check it in Settings."
        private const val DAY = 24 * 60 * 60 * 1000L
        private const val LAUNCH_DELAY = 20_000L
        private const val BATCH = 25
        /** Fewer votes than this, and a score doesn't mean much. */
        private const val MIN_VOTES = 5
        private const val RUNNING_REFRESH = 7 * DAY
        private const val RETRY_NOT_FOUND = 30 * DAY
        private const val SETTLED_REFRESH = 90 * DAY
        private const val FORGET_AFTER = 90 * DAY
        private const val REFRESH_AT_MOST = 150 * DAY
        /** TMDB allows keeping what it says for six months at most. */
        const val KEEP_AT_MOST = 180 * DAY

        /** Which titles count as the same one: the same kind, [ShowNames.key] (cleaned-up name) and year. */
        fun keyOf(kind: ContentKind, name: String, year: String?, released: String?): String {
            val cleaned = ShowNames.clean(name)
            return "${kind.name}|${ShowNames.key(cleaned.title, yearOf(year, released, cleaned.year))}"
        }

        private fun yearOf(year: String?, released: String?, fromName: Int?): Int? =
            ShowNames.yearOf(year) ?: ShowNames.yearOf(released) ?: fromName
    }
}

/** The TMDB catalogue for movies or series. */
val ContentKind.tmdbKind: TmdbKind get() = if (this == ContentKind.MOVIE) TmdbKind.MOVIE else TmdbKind.TV
