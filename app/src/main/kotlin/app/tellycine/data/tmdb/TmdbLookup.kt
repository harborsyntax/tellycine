package app.tellycine.data.tmdb

import app.tellycine.core.ContentKind
import app.tellycine.core.ShowNames
import app.tellycine.core.tmdb.Tmdb
import app.tellycine.core.tmdb.TmdbCollection
import app.tellycine.core.tmdb.TmdbDetails
import app.tellycine.core.tmdb.TmdbSeason
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.db.TellycineDatabase
import app.tellycine.data.db.TmdbPageEntity
import app.tellycine.data.db.TmdbTitleEntity
import app.tellycine.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What a title's page shows from TMDB, fetched when it's opened (while details from TMDB are turned
 * on): the whole page, with cast, trailers, pictures, age rating and recommendations, kept for a
 * while; and a series' seasons and a film's collection, kept while the app runs.
 */
class TmdbLookup(
    db: TellycineDatabase,
    private val settings: SettingsRepository,
    private val client: TmdbClient,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.tmdb()
    private val seasons = Recent<String, TmdbSeason>(40)
    private val collections = Recent<Int, TmdbCollection>(20)

    /** Whether details from TMDB are turned on, with a key. */
    suspend fun enabled(): Boolean = settings.current().tmdb

    /**
     * TMDB's page for [stream], or null when turned off or TMDB doesn't have it. Uses [providerId]
     * (the TMDB id its provider gives) or the one found for it before, or else searches for it.
     */
    suspend fun details(stream: StreamEntity, providerId: Int? = null): TmdbDetails? {
        val key = key() ?: return null
        val kind = stream.kind.tmdbKind
        val id = providerId ?: stream.tmdbId ?: find(key, stream) ?: return null
        val now = clock()
        val kept = dao.page(stream.kind, id)
        val fresh = if (stream.kind == ContentKind.MOVIE) MOVIE_PAGE_FRESH else SERIES_PAGE_FRESH
        val json = if (kept != null && now - kept.fetchedAt < fresh) {
            kept.json
        } else {
            try {
                client.page(key, kind, id, Tmdb.detailsAppend(kind))?.also { dao.upsertPage(TmdbPageEntity(stream.kind, id, it, now)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: TmdbClient.KeyRejected) {
                throw e
            } catch (e: Exception) {
                // Offline, say: what was fetched before will do.
                kept?.json ?: throw e
            }
        } ?: return null
        return Tmdb.parseDetails(json, kind, client.region)
    }

    /** A season of the series [tvId]. */
    suspend fun season(tvId: Int, number: Int): TmdbSeason? {
        val key = key() ?: return null
        return seasons.getOrPut("$tvId/$number") { client.season(key, tvId, number)?.let(Tmdb::parseSeason) }
    }

    /** The collection (film series) [id]. */
    suspend fun collection(id: Int): TmdbCollection? {
        val key = key() ?: return null
        return collections.getOrPut(id) { client.collection(key, id)?.let(Tmdb::parseCollection) }
    }

    /**
     * The [kind] titles in the playlists that are the TMDB titles [tmdbIds], in that order, one each:
     * from [preferred] when it has one, else from the first playlist that does.
     */
    suspend fun inPlaylists(kind: ContentKind, tmdbIds: List<Int>, playlistIds: List<Long>, preferred: Long): List<StreamEntity> {
        if (tmdbIds.isEmpty() || playlistIds.isEmpty()) return emptyList()
        val order = playlistIds.sortedBy { if (it == preferred) 0 else 1 }
        val found = tmdbIds.distinct().chunked(500).flatMap { dao.streamsFor(kind, it, playlistIds) }
            .groupBy { it.tmdbId }
            .mapValues { (_, copies) -> copies.minBy { order.indexOf(it.playlistId) } }
        return tmdbIds.distinct().mapNotNull { found[it] }
    }

    private suspend fun key(): String? = settings.current().takeIf { it.tmdb }?.tmdbKey

    /** Searches TMDB for [stream], keeping what's found for the lists to fill in later. */
    private suspend fun find(key: String, stream: StreamEntity): Int? {
        val titleKey = TmdbUpdater.keyOf(stream.kind, stream.name, stream.year, stream.released)
        dao.title(titleKey)?.let { return it.tmdbId }
        val cleaned = ShowNames.clean(stream.name)
        val year = ShowNames.yearOf(stream.year) ?: ShowNames.yearOf(stream.released) ?: cleaned.year
        val kind = stream.kind.tmdbKind
        val match = Tmdb.bestMatch(client.search(key, kind, cleaned.title), cleaned.title, year)
            ?: year?.let { Tmdb.bestMatch(client.search(key, kind, cleaned.title, it), cleaned.title, it) }
            ?: return null
        // Checked "never", so the next check fills in its score, poster and air dates.
        dao.upsert(TmdbTitleEntity(titleKey, stream.kind, match.id, null, null, null, null, false, checkedAt = 0))
        return match.id
    }

    /** The [size] most recently used answers. */
    private class Recent<K, V : Any>(private val size: Int) {
        private val map = object : LinkedHashMap<K, V>(size, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = this.size > this@Recent.size
        }
        private val lock = Mutex()

        suspend fun getOrPut(key: K, load: suspend () -> V?): V? {
            lock.withLock { map[key] }?.let { return it }
            val value = load() ?: return null
            lock.withLock { map[key] = value }
            return value
        }
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
        const val MOVIE_PAGE_FRESH = 30 * DAY
        const val SERIES_PAGE_FRESH = 3 * DAY
    }
}
