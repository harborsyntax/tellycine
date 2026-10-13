package app.tellycine.core.tvmaze

import app.tellycine.core.ShowNames
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.math.abs

/** A show on TVmaze, with when its latest episode aired and when the next one will ("2025-05-01"). */
data class TvmazeShow(
    val id: Int,
    val name: String,
    val premiered: String?,
    val ended: Boolean,
    /** When TVmaze last changed the show, in seconds. */
    val updated: Long?,
    val lastAired: String?,
    val nextAired: String?,
)

/** Reading the answers of TVmaze's API (https://www.tvmaze.com/api), and matching its shows to series. */
object Tvmaze {
    /** A show (`/shows/1`, `/singlesearch/shows`), with its `previousepisode` and `nextepisode` if embedded. */
    fun parseShow(json: String): TvmazeShow? = runCatching { JsonParser.parseString(json) }.getOrNull()?.obj()?.let(::show)

    /** Search results (`/search/shows`): the shows, best first. */
    fun parseSearch(json: String): List<TvmazeShow> =
        runCatching { JsonParser.parseString(json) }.getOrNull()?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.obj()?.get("show")?.obj()?.let(::show) }.orEmpty()

    /** Shows' last changes (`/updates/shows`): TVmaze's id to seconds. */
    fun parseUpdates(json: String): Map<Int, Long> =
        runCatching { JsonParser.parseString(json) }.getOrNull()?.obj()?.entrySet()
            ?.mapNotNull { (id, at) -> id.toIntOrNull()?.let { key -> at.long()?.let { key to it } } }?.toMap().orEmpty()

    /**
     * Whether [show] is the series [title] from [year]: the same name ([ShowNames.normalise]d), and
     * first shown that year (give or take one, as providers' years vary) when both say.
     */
    fun matches(show: TvmazeShow, title: String, year: Int?): Boolean {
        if (ShowNames.normalise(show.name) != ShowNames.normalise(title)) return false
        val premiered = ShowNames.yearOf(show.premiered)
        return year == null || premiered == null || abs(premiered - year) <= 1
    }

    private fun show(o: JsonObject): TvmazeShow? {
        val id = o["id"]?.long()?.toInt() ?: return null
        val embedded = o["_embedded"]?.obj()
        return TvmazeShow(
            id = id,
            name = o["name"]?.string() ?: return null,
            premiered = o["premiered"]?.string(),
            ended = o["status"]?.string() == "Ended",
            updated = o["updated"]?.long(),
            lastAired = embedded?.get("previousepisode")?.obj()?.get("airdate")?.string(),
            nextAired = embedded?.get("nextepisode")?.obj()?.get("airdate")?.string(),
        )
    }

    private fun JsonElement.obj(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonElement.string(): String? = takeIf { it.isJsonPrimitive }?.asString?.trim()?.ifEmpty { null }
    private fun JsonElement.long(): Long? = takeIf { it.isJsonPrimitive }?.asString?.toLongOrNull()
}
