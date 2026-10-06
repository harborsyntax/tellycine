package app.tube.core.xtream

data class XtreamAccount(
    val authenticated: Boolean,
    val status: String?,
    val message: String?,
    val expiresAtMs: Long?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    val allowedOutputFormats: List<String>,
    val serverTimezone: String?,
)

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
    val addedMs: Long?,
    val plot: String?,
    val year: String?,
    val genre: String?,
    val catchupDays: Int?,
    val backdrop: String?,
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
    val backdrop: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: Double?,
    /** Season number to its display name/cover, if the panel provides one. */
    val seasons: Map<Int, XtreamSeason>,
    val episodes: List<XtreamEpisode>,
)

data class XtreamSeason(
    val number: Int,
    val name: String?,
    val cover: String?,
)

data class XtreamVodInfo(
    val name: String?,
    val cover: String?,
    val backdrop: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val durationSecs: Int?,
    val rating: Double?,
    val containerExtension: String?,
)
