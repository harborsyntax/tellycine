package app.tellycine.core.xtream

data class XtreamAccount(
    val authenticated: Boolean,
    val status: String?,
    val message: String?,
    val expiresAtMs: Long?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    val allowedOutputFormats: List<String>,
    val serverTimezone: String?,
    /** Software behind the Xtream API when it identifies itself (currently only Dispatcharr). */
    val serverSoftware: String? = null,
) {
    val isDispatcharr get() = serverSoftware == SOFTWARE_DISPATCHARR

    companion object {
        const val SOFTWARE_DISPATCHARR = "Dispatcharr"
    }
}

data class XtreamCategory(
    val id: String,
    val name: String,
    val parentId: String?,
)

/** A live channel, VOD item or series (series use `series_id` as [id]). */
data class XtreamItem(
    val id: String,
    val name: String,
    val icon: String?,
    val categoryId: String?,
    val epgChannelId: String?,
    val containerExtension: String?,
    val num: Int?,
    val rating: Double?,
    /** When the provider added it. */
    val addedMs: Long?,
    /** When the provider last changed it: for a series, usually when an episode was added. */
    val updatedMs: Long? = null,
    val plot: String?,
    val year: String?,
    /** Its release date, as [app.tellycine.core.ReleaseDates.normalise] gives it. */
    val released: String? = null,
    val genre: String?,
    val catchupDays: Int?,
    val backdrop: String?,
    /** The film's or series' id on TMDB, when the provider gives it. */
    val tmdbId: Int? = null,
)

data class XtreamEpisode(
    val id: String,
    val season: Int,
    val episodeNum: Int,
    val title: String,
    val containerExtension: String?,
    val image: String?,
    val plot: String?,
    val durationSecs: Int?,
    val rating: Double?,
)

data class XtreamSeriesInfo(
    val name: String?,
    val cover: String?,
    /** Every backdrop the panel lists, best first. */
    val backdrops: List<String>,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: Double?,
    /** The trailer's YouTube address, if the panel gives one. */
    val trailer: String?,
    /** Season number to its display name/cover, if the panel provides one. */
    val seasons: Map<Int, XtreamSeason>,
    val episodes: List<XtreamEpisode>,
    val tmdbId: Int? = null,
)

data class XtreamSeason(
    val number: Int,
    val name: String?,
    val cover: String?,
)

data class XtreamVodInfo(
    val name: String?,
    val cover: String?,
    /** Every backdrop the panel lists, best first. */
    val backdrops: List<String>,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val durationSecs: Int?,
    val rating: Double?,
    /** The trailer's YouTube address, if the panel gives one. */
    val trailer: String?,
    val containerExtension: String?,
    val tmdbId: Int? = null,
)

/** What a server offers of Xtream Codes' API ([XtreamParser.apiStatus]). */
enum class XtreamApi {
    /** No Xtream Codes API. */
    NONE,

    /** An Xtream Codes API, which didn't accept the login it was given (or wasn't given one). */
    OFFERED,

    /** An Xtream Codes API that accepts the login. */
    WORKS,
}
