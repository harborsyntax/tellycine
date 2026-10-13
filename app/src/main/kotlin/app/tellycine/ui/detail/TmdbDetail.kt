package app.tellycine.ui.detail

import app.tellycine.core.ShowNames
import app.tellycine.core.tmdb.Tmdb
import app.tellycine.core.tmdb.TmdbDetails
import app.tellycine.core.tmdb.TmdbKind
import app.tellycine.core.tmdb.TmdbSeason
import app.tellycine.ui.components.formatDuration
import app.tellycine.ui.components.formatIsoDate

/** Someone in the cast, with their photo. */
data class PersonUi(val name: String, val role: String?, val photo: String?)

/**
 * [this] with what TMDB says about it: what the playlist leaves out filled in, and what only TMDB
 * has added. TMDB's score, names and pictures are preferred; the playlist's plot, artwork and
 * running time stay when it has them.
 */
fun DetailInfo.withTmdb(d: TmdbDetails): DetailInfo {
    val series = d.kind == TmdbKind.TV
    val credits = buildList {
        if (d.directors.isNotEmpty()) add((if (d.directors.size > 1) "Directors" else "Director") to d.directors.joinToString(", "))
        if (d.creators.isNotEmpty()) add("Created by" to d.creators.joinToString(", "))
        if (d.writers.isNotEmpty() && !series) add((if (d.writers.size > 1) "Writers" else "Writer") to d.writers.joinToString(", "))
        if (d.networks.isNotEmpty()) add((if (d.networks.size > 1) "Networks" else "Network") to d.networks.joinToString(", "))
    }
    val shownTitle = ShowNames.normalise(ShowNames.clean(title).title)
    return copy(
        poster = poster ?: Tmdb.image(d.poster, LARGE_POSTER),
        backdrops = d.backdrops.mapNotNull { Tmdb.image(it, Tmdb.BACKDROP) }.ifEmpty { backdrops },
        plot = plot?.takeIf { it.isNotBlank() } ?: d.overview,
        year = year ?: ShowNames.yearOf(d.date)?.toString(),
        // A series' running time is an episode's, which each episode shows.
        runtime = runtime ?: d.runtime?.takeIf { !series }?.let { formatDuration(it * 60) },
        genre = genre?.takeIf { it.isNotBlank() } ?: d.genres.take(3).joinToString(", ").ifEmpty { null },
        certification = d.certification,
        status = if (series) statusLabel(d.status) else null,
        rating = d.rating ?: rating,
        // TMDB's lists of names, with photos, take over from the playlist's.
        director = director.takeIf { credits.isEmpty() },
        cast = cast.takeIf { d.cast.isEmpty() },
        trailer = trailer ?: d.trailer,
        seasonCovers = seasons.keys.mapNotNull { n ->
            (seasonCovers[n] ?: d.seasons.firstOrNull { it.number == n }?.poster?.let { Tmdb.image(it, Tmdb.POSTER) })?.let { n to it }
        }.toMap(),
        seasonOverviews = d.seasons.mapNotNull { s -> s.overview?.let { s.number to it } }.toMap(),
        tagline = d.tagline,
        originalTitle = d.originalTitle?.takeIf {
            val original = ShowNames.normalise(it)
            original != shownTitle && original != ShowNames.normalise(d.title)
        },
        credits = credits,
        people = d.cast.map { PersonUi(it.name, it.role, Tmdb.image(it.photo, Tmdb.PROFILE)) },
        nextEpisode = d.nextEpisode?.let { e ->
            listOfNotNull("S${e.season} E${e.episode}", e.name, formatIsoDate(e.airDate)).joinToString(" · ")
        },
        tmdbId = d.id,
        fromTmdb = true,
    )
}

/**
 * The episodes with TMDB's details for [season] (matched by season and number): names in place of
 * placeholders such as "Episode 3" or "S01E03", and plots, pictures, lengths and air dates where the
 * playlist has none. A real name from the playlist stays, as numbering doesn't always agree.
 */
fun List<EpisodeUi>.withTmdbSeason(season: TmdbSeason, seriesTitle: String): List<EpisodeUi> {
    val byNumber = season.episodes.associateBy { it.number }
    return map { ep ->
        val t = byNumber[ep.number]?.takeIf { ep.season == season.number } ?: return@map ep
        ep.copy(
            title = t.name?.takeIf { isPlaceholderTitle(ep.title, ep.number, seriesTitle) } ?: ep.title,
            plot = ep.plot?.takeIf { it.isNotBlank() } ?: t.overview,
            image = ep.image ?: Tmdb.image(t.still, Tmdb.STILL),
            durationSecs = ep.durationSecs?.takeIf { it > 0 } ?: t.runtime?.times(60),
            airDate = t.airDate,
        )
    }
}

/** Whether a playlist's episode name is a placeholder rather than a real name. */
fun isPlaceholderTitle(title: String, number: Int, seriesTitle: String): Boolean {
    val t = title.trim()
    if (t.isEmpty()) return true
    if (Regex("""(?i)^(episode|ep\.?|e)\s*0*$number$""").matches(t)) return true
    if (Regex("""(?i)\bS\d{1,3}\s*[ .-]?\s*E\d{1,4}\b""").containsMatchIn(t)) return true
    val plain = ShowNames.normalise(ShowNames.clean(t).title)
    return plain.isEmpty() || plain.all { it.isDigit() || it == ' ' } || plain == ShowNames.normalise(ShowNames.clean(seriesTitle).title)
}

private fun statusLabel(status: String?): String? = when (status) {
    "Returning Series" -> "Returning series"
    "Ended" -> "Ended"
    "Canceled" -> "Cancelled"
    "In Production" -> "In production"
    "Planned" -> "Planned"
    else -> null
}

private const val LARGE_POSTER = "w500"
