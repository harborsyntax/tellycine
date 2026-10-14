package app.tellycine.core.tmdb

import app.tellycine.core.ShowNames
import app.tellycine.core.YouTube
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.math.abs

/** TMDB's two catalogues, and the path of each in its API. */
enum class TmdbKind(val path: String) { MOVIE("movie"), TV("tv") }

/** A film or series as a search, a list or its own page gives it. Image paths go through [Tmdb.image]. */
data class TmdbTitle(
    val id: Int,
    val title: String,
    val originalTitle: String?,
    /** When a film came out, or a series started: "2019-05-03". */
    val date: String?,
    val overview: String?,
    val poster: String?,
    val backdrop: String?,
    /** Its score out of 10, or null before anyone has voted. */
    val rating: Double?,
    val votes: Int,
)

/** Someone in a title's cast ([role] is their character) or crew. */
data class TmdbPerson(val id: Int, val name: String, val role: String?, val photo: String?)

/** An episode a series' page points to: its latest, or its next. */
data class TmdbEpisodeRef(val season: Int, val episode: Int, val name: String?, val airDate: String?)

/** A season as a series' page lists it. */
data class TmdbSeasonRef(
    val number: Int,
    val name: String?,
    val overview: String?,
    val poster: String?,
    val airDate: String?,
    val episodes: Int,
)

/** The collection (a film series) a film belongs to. */
data class TmdbCollectionRef(val id: Int, val name: String, val poster: String?)

/** Everything a film's or series' page shows from TMDB. Parts not asked for are empty. */
data class TmdbDetails(
    val kind: TmdbKind,
    val id: Int,
    val title: String,
    val originalTitle: String?,
    val tagline: String?,
    val overview: String?,
    /** When a film came out, or a series started. */
    val date: String?,
    /** For a series: when its latest episode aired, which, and the next one if it's known. */
    val lastAired: String?,
    val lastEpisode: TmdbEpisodeRef?,
    val nextEpisode: TmdbEpisodeRef?,
    /** TMDB's status, such as "Released", "Returning Series" or "Ended". */
    val status: String?,
    /** A series that has finished (ended or cancelled). */
    val ended: Boolean,
    /** A film's running time, or a series' usual episode length, in minutes. */
    val runtime: Int?,
    val genres: List<String>,
    val rating: Double?,
    val votes: Int,
    /** The age rating for the region asked for (or the US): "15", "TV-MA". */
    val certification: String?,
    val poster: String?,
    /** Wide pictures, best first: the main one, then others without words on. */
    val backdrops: List<String>,
    val directors: List<String>,
    val writers: List<String>,
    val creators: List<String>,
    val cast: List<TmdbPerson>,
    /** For a series: the channels or services it's made for. */
    val networks: List<String>,
    /** The trailer's YouTube address. */
    val trailer: String?,
    val collection: TmdbCollectionRef?,
    val seasons: List<TmdbSeasonRef>,
    val seasonCount: Int?,
    val episodeCount: Int?,
    /** What TMDB recommends for people who liked this, best first. */
    val recommendations: List<TmdbTitle>,
)

data class TmdbEpisode(
    val season: Int,
    val number: Int,
    val name: String?,
    val overview: String?,
    val still: String?,
    val airDate: String?,
    val runtime: Int?,
)

data class TmdbSeason(val number: Int, val name: String?, val overview: String?, val poster: String?, val episodes: List<TmdbEpisode>)

data class TmdbCollection(val id: Int, val name: String, val parts: List<TmdbTitle>)

/**
 * Reading the answers of TMDB's API (https://developer.themoviedb.org), and matching its films and
 * series to a playlist's titles.
 */
object Tmdb {
    private const val IMAGES = "https://image.tmdb.org/t/p/"

    /** Image sizes TMDB serves. */
    const val POSTER = "w342"
    const val BACKDROP = "w1280"
    const val PROFILE = "w185"
    const val STILL = "w300"

    /** The address of the picture at [path] in [size] ([POSTER], [BACKDROP]...). */
    fun image(path: String?, size: String): String? = path?.takeIf { it.startsWith("/") }?.let { "$IMAGES$size$it" }

    /** What to append to a film's or series' page for everything [parseDetails] reads. */
    fun detailsAppend(kind: TmdbKind): String = when (kind) {
        TmdbKind.MOVIE -> "credits,videos,images,release_dates,recommendations"
        TmdbKind.TV -> "aggregate_credits,credits,videos,images,content_ratings,recommendations"
    }

    /** Search results (`/search/movie`, `/search/tv`), best first. */
    fun parseSearch(json: String, kind: TmdbKind): List<TmdbTitle> =
        parse(json)?.array("results")?.mapNotNull { it.obj()?.let { o -> title(o, kind) } }.orEmpty()

    /**
     * A film's or series' page (`/movie/{id}`, `/tv/{id}`), with whatever was appended to it
     * ([detailsAppend]). The age rating is the one for [region] ("GB"), or else the US one.
     */
    fun parseDetails(json: String, kind: TmdbKind, region: String = "US"): TmdbDetails? {
        val o = parse(json) ?: return null
        val base = title(o, kind) ?: return null
        val credits = o.obj("credits")
        val crew = credits?.array("crew").objects()
        val status = o.str("status")
        return TmdbDetails(
            kind = kind,
            id = base.id,
            title = base.title,
            originalTitle = base.originalTitle,
            tagline = o.str("tagline"),
            overview = base.overview,
            date = base.date,
            lastAired = o.obj("last_episode_to_air")?.str("air_date") ?: o.str("last_air_date"),
            lastEpisode = o.obj("last_episode_to_air")?.let(::episodeRef),
            nextEpisode = o.obj("next_episode_to_air")?.let(::episodeRef),
            status = status,
            ended = status == "Ended" || status == "Canceled",
            runtime = o.int("runtime")?.takeIf { it > 0 }
                ?: o.array("episode_run_time")?.firstOrNull()?.int()?.takeIf { it > 0 }
                ?: o.obj("last_episode_to_air")?.int("runtime")?.takeIf { it > 0 },
            genres = o.array("genres").objects().mapNotNull { it.str("name") },
            rating = base.rating,
            votes = base.votes,
            certification = certification(o, kind, region) ?: certification(o, kind, "US").takeIf { region != "US" },
            poster = base.poster,
            backdrops = backdrops(o, base.backdrop),
            directors = crew.filter { it.str("job") == "Director" }.mapNotNull { it.str("name") }.distinct().take(3),
            writers = crew.filter { it.str("department") == "Writing" && it.str("job") in WRITING_JOBS }
                .mapNotNull { it.str("name") }.distinct().take(3),
            creators = o.array("created_by").objects().mapNotNull { it.str("name") }.distinct(),
            cast = cast(o),
            networks = o.array("networks").objects().mapNotNull { it.str("name") }.distinct(),
            trailer = trailer(o),
            collection = o.obj("belongs_to_collection")?.let { c ->
                val id = c.int("id") ?: return@let null
                TmdbCollectionRef(id, c.str("name") ?: return@let null, c.str("poster_path"))
            },
            seasons = o.array("seasons").objects().mapNotNull { s ->
                val number = s.int("season_number") ?: return@mapNotNull null
                TmdbSeasonRef(number, s.str("name"), s.str("overview"), s.str("poster_path"), s.str("air_date"), s.int("episode_count") ?: 0)
            },
            seasonCount = o.int("number_of_seasons"),
            episodeCount = o.int("number_of_episodes"),
            recommendations = o.obj("recommendations")?.array("results").objects().mapNotNull { title(it, kind) },
        )
    }

    /** A season of a series (`/tv/{id}/season/{n}`), with its episodes. */
    fun parseSeason(json: String): TmdbSeason? {
        val o = parse(json) ?: return null
        val number = o.int("season_number") ?: return null
        return TmdbSeason(
            number = number,
            name = o.str("name"),
            overview = o.str("overview"),
            poster = o.str("poster_path"),
            episodes = o.array("episodes").objects().mapNotNull { e ->
                TmdbEpisode(
                    season = e.int("season_number") ?: number,
                    number = e.int("episode_number") ?: return@mapNotNull null,
                    name = e.str("name"),
                    overview = e.str("overview"),
                    still = e.str("still_path"),
                    airDate = e.str("air_date"),
                    runtime = e.int("runtime")?.takeIf { it > 0 },
                )
            },
        )
    }

    /** A collection (`/collection/{id}`): its films, in order of release. */
    fun parseCollection(json: String): TmdbCollection? {
        val o = parse(json) ?: return null
        return TmdbCollection(
            id = o.int("id") ?: return null,
            name = o.str("name") ?: return null,
            parts = o.array("parts").objects().mapNotNull { title(it, TmdbKind.MOVIE) }.sortedBy { it.date ?: "9999" },
        )
    }

    /**
     * Which of [results] is the title [title] from [year]: one with the same name (or original
     * name, [ShowNames.normalise]d), from that year when both say (or the year either side, as
     * providers' years vary). A remake from another year isn't it. Results are best first.
     */
    fun bestMatch(results: List<TmdbTitle>, title: String, year: Int?): TmdbTitle? {
        val wanted = ShowNames.normalise(title)
        if (wanted.isEmpty()) return null
        val named = results.filter { r ->
            ShowNames.normalise(r.title) == wanted || r.originalTitle?.let(ShowNames::normalise) == wanted
        }
        if (year == null) return named.firstOrNull()
        return named.firstOrNull { ShowNames.yearOf(it.date) == year }
            ?: named.firstOrNull { r -> ShowNames.yearOf(r.date)?.let { abs(it - year) <= 1 } == true }
    }

    private val WRITING_JOBS = setOf("Screenplay", "Writer", "Story", "Novel", "Author", "Teleplay")

    private fun title(o: JsonObject, kind: TmdbKind): TmdbTitle? {
        val id = o.int("id") ?: return null
        val movie = kind == TmdbKind.MOVIE
        val votes = o.int("vote_count") ?: 0
        return TmdbTitle(
            id = id,
            title = (if (movie) o.str("title") else o.str("name")) ?: return null,
            originalTitle = if (movie) o.str("original_title") else o.str("original_name"),
            date = if (movie) o.str("release_date") else o.str("first_air_date"),
            overview = o.str("overview"),
            poster = o.str("poster_path"),
            backdrop = o.str("backdrop_path"),
            rating = o.double("vote_average")?.takeIf { votes > 0 && it > 0 },
            votes = votes,
        )
    }

    private fun episodeRef(o: JsonObject): TmdbEpisodeRef? {
        val season = o.int("season_number") ?: return null
        val episode = o.int("episode_number") ?: return null
        return TmdbEpisodeRef(season, episode, o.str("name"), o.str("air_date"))
    }

    private fun certification(o: JsonObject, kind: TmdbKind, region: String): String? = when (kind) {
        TmdbKind.MOVIE -> o.obj("release_dates")?.array("results").objects()
            .firstOrNull { it.str("iso_3166_1") == region }
            ?.array("release_dates").objects()
            // Cinema releases first, then any other with a rating.
            .sortedBy { if (it.int("type") == 3) 0 else 1 }
            .firstNotNullOfOrNull { it.str("certification") }
        TmdbKind.TV -> o.obj("content_ratings")?.array("results").objects()
            .firstOrNull { it.str("iso_3166_1") == region }?.str("rating")
    }

    private fun backdrops(o: JsonObject, main: String?): List<String> {
        val images = o.obj("images")?.array("backdrops").objects()
        // Pictures without words go first; TMDB lists each language's best first.
        val textless = images.filter { it.str("iso_639_1") == null }.mapNotNull { it.str("file_path") }
        val others = images.filter { it.str("iso_639_1") != null }.mapNotNull { it.str("file_path") }
        return (listOfNotNull(main) + textless + others).distinct().take(MAX_BACKDROPS)
    }

    private fun cast(o: JsonObject): List<TmdbPerson> {
        // A series' cast across all its seasons, rather than its latest season's.
        val aggregate = o.obj("aggregate_credits")?.array("cast").objects()
        val people = aggregate.ifEmpty { o.obj("credits")?.array("cast").objects() }
        return people.sortedBy { it.int("order") ?: Int.MAX_VALUE }.mapNotNull { p ->
            TmdbPerson(
                id = p.int("id") ?: return@mapNotNull null,
                name = p.str("name") ?: return@mapNotNull null,
                role = p.str("character") ?: p.array("roles").objects().firstNotNullOfOrNull { it.str("character") },
                photo = p.str("profile_path"),
            )
        }.distinctBy { it.id }.take(MAX_CAST)
    }

    /** The best YouTube trailer: an official trailer, else any trailer, else a teaser. */
    private fun trailer(o: JsonObject): String? {
        val videos = o.obj("videos")?.array("results").objects().filter { it.str("site") == "YouTube" && it.str("key") != null }
        val best = videos.firstOrNull { it.str("type") == "Trailer" && it.bool("official") }
            ?: videos.firstOrNull { it.str("type") == "Trailer" }
            ?: videos.firstOrNull { it.str("type") == "Teaser" }
        return YouTube.trailerUrl(best?.str("key"))
    }

    private const val MAX_BACKDROPS = 10
    private const val MAX_CAST = 20

    private fun parse(json: String): JsonObject? = runCatching { JsonParser.parseString(json) }.getOrNull()?.obj()

    private fun JsonElement.obj(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.obj(key: String): JsonObject? = get(key)?.obj()
    private fun JsonObject.array(key: String): JsonArray? = get(key)?.takeIf { it.isJsonArray }?.asJsonArray
    private fun JsonArray?.objects(): List<JsonObject> = this?.mapNotNull { it.obj() }.orEmpty()

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.int(key: String): Int? = get(key)?.int()
    private fun JsonElement.int(): Int? = takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble.toInt() }.getOrNull() }
    private fun JsonObject.double(key: String): Double? = get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble }.getOrNull() }
    private fun JsonObject.bool(key: String): Boolean = get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asBoolean }.getOrNull() } == true
}
