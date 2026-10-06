package app.tube.core.xtream

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.Reader

/**
 * Streaming parser for the Xtream Codes `player_api.php` JSON API.
 *
 * Panels are notoriously inconsistent (numbers as strings, empty strings for nulls,
 * objects instead of arrays...), so every value is read loosely.
 */
class XtreamParser {

    fun parseAccount(reader: Reader): XtreamAccount {
        val root = JsonReader(reader).apply { isLenient = true }.use { readValue(it) } as? Map<*, *>
            ?: return XtreamAccount(false, null, "Unexpected response", null, null, null, emptyList(), null)
        val user = root["user_info"] as? Map<*, *> ?: emptyMap<String, Any?>()
        val server = root["server_info"] as? Map<*, *> ?: emptyMap<String, Any?>()
        val formats = (user["allowed_output_formats"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
        return XtreamAccount(
            authenticated = user.int("auth") == 1,
            status = user.str("status"),
            message = user.str("message"),
            expiresAtMs = user.long("exp_date")?.let { it * 1000 },
            maxConnections = user.int("max_connections"),
            activeConnections = user.int("active_cons"),
            allowedOutputFormats = formats,
            serverTimezone = server.str("timezone"),
            // Dispatcharr answers with message "Dispatcharr XC API".
            serverSoftware = XtreamAccount.SOFTWARE_DISPATCHARR.takeIf {
                user.str("message")?.contains(it, ignoreCase = true) == true
            },
        )
    }

    fun parseCategories(reader: Reader, onCategory: (XtreamCategory) -> Unit) {
        forEachObject(reader) { obj ->
            val id = obj.str("category_id") ?: return@forEachObject
            onCategory(
                XtreamCategory(
                    id = id,
                    name = obj.str("category_name") ?: id,
                    parentId = obj.str("parent_id")?.takeIf { it != "0" },
                )
            )
        }
    }

    /** Parses `get_live_streams`, `get_vod_streams` or `get_series`. */
    fun parseItems(reader: Reader, onItem: (XtreamItem) -> Unit) {
        forEachObject(reader) { obj ->
            val id = obj.str("stream_id") ?: obj.str("series_id") ?: return@forEachObject
            val backdrop = when (val b = obj["backdrop_path"]) {
                is List<*> -> b.firstOrNull()?.toString()
                else -> b?.toString()
            }?.ifBlank { null }
            onItem(
                XtreamItem(
                    id = id,
                    name = obj.str("name") ?: obj.str("title") ?: id,
                    icon = obj.str("stream_icon") ?: obj.str("cover"),
                    categoryId = obj.str("category_id"),
                    epgChannelId = obj.str("epg_channel_id"),
                    containerExtension = obj.str("container_extension"),
                    num = obj.int("num"),
                    rating = obj.double("rating_5based")?.let { it * 2 } ?: obj.double("rating"),
                    addedMs = (obj.long("added") ?: obj.long("last_modified"))?.let { it * 1000 },
                    plot = obj.str("plot"),
                    year = obj.str("year") ?: obj.str("releaseDate")?.take(4) ?: obj.str("release_date")?.take(4),
                    genre = obj.str("genre"),
                    catchupDays = if (obj.int("tv_archive") == 1) obj.int("tv_archive_duration") else null,
                    backdrop = backdrop,
                )
            )
        }
    }

    fun parseSeriesInfo(reader: Reader): XtreamSeriesInfo {
        val root = JsonReader(reader).apply { isLenient = true }.use { readValue(it) } as? Map<*, *> ?: emptyMap<String, Any?>()
        val info = root["info"] as? Map<*, *> ?: emptyMap<String, Any?>()

        val seasons = LinkedHashMap<Int, XtreamSeason>()
        (root["seasons"] as? List<*>)?.forEach { s ->
            val m = s as? Map<*, *> ?: return@forEach
            val num = m.int("season_number") ?: return@forEach
            seasons[num] = XtreamSeason(num, m.str("name"), m.str("cover_big") ?: m.str("cover"))
        }

        val episodes = ArrayList<XtreamEpisode>()
        fun addEpisode(raw: Any?, seasonHint: Int?) {
            val m = raw as? Map<*, *> ?: return
            val id = m.str("id") ?: return
            val epInfo = m["info"] as? Map<*, *> ?: emptyMap<String, Any?>()
            val season = m.int("season") ?: seasonHint ?: 1
            episodes += XtreamEpisode(
                id = id,
                season = season,
                episodeNum = m.int("episode_num") ?: (episodes.count { it.season == season } + 1),
                title = m.str("title") ?: "Episode ${m.str("episode_num") ?: ""}".trim(),
                containerExtension = m.str("container_extension"),
                image = epInfo.str("movie_image") ?: epInfo.str("cover_big"),
                plot = epInfo.str("plot"),
                durationSecs = epInfo.int("duration_secs"),
                rating = epInfo.double("rating"),
            )
        }
        when (val eps = root["episodes"]) {
            is Map<*, *> -> eps.forEach { (key, list) ->
                val hint = key?.toString()?.toIntOrNull()
                (list as? List<*>)?.forEach { addEpisode(it, hint) }
            }
            is List<*> -> eps.forEach { item ->
                if (item is List<*>) item.forEach { addEpisode(it, null) } else addEpisode(item, null)
            }
        }
        episodes.sortWith(compareBy({ it.season }, { it.episodeNum }))
        for (ep in episodes) {
            if (ep.season !in seasons) seasons[ep.season] = XtreamSeason(ep.season, null, null)
        }

        return XtreamSeriesInfo(
            name = info.str("name"),
            cover = info.str("cover"),
            backdrop = (info["backdrop_path"] as? List<*>)?.firstOrNull()?.toString()?.ifBlank { null },
            plot = info.str("plot"),
            cast = info.str("cast"),
            director = info.str("director"),
            genre = info.str("genre"),
            releaseDate = info.str("releaseDate") ?: info.str("release_date"),
            rating = info.double("rating"),
            seasons = seasons.toSortedMap(),
            episodes = episodes,
        )
    }

    fun parseVodInfo(reader: Reader): XtreamVodInfo {
        val root = JsonReader(reader).apply { isLenient = true }.use { readValue(it) } as? Map<*, *> ?: emptyMap<String, Any?>()
        val info = root["info"] as? Map<*, *> ?: emptyMap<String, Any?>()
        val data = root["movie_data"] as? Map<*, *> ?: emptyMap<String, Any?>()
        return XtreamVodInfo(
            name = info.str("name") ?: data.str("name"),
            cover = info.str("movie_image") ?: info.str("cover_big"),
            backdrop = (info["backdrop_path"] as? List<*>)?.firstOrNull()?.toString()?.ifBlank { null },
            plot = info.str("plot") ?: info.str("description"),
            cast = info.str("cast") ?: info.str("actors"),
            director = info.str("director"),
            genre = info.str("genre"),
            releaseDate = info.str("releasedate") ?: info.str("release_date"),
            durationSecs = info.int("duration_secs"),
            rating = info.double("rating"),
            containerExtension = data.str("container_extension"),
        )
    }

    /**
     * Iterates the objects of a top level array without materialising the whole array.
     * Also copes with panels that return an object keyed by id instead of an array.
     */
    private fun forEachObject(reader: Reader, block: (Map<*, *>) -> Unit) {
        JsonReader(reader).use { json ->
            json.isLenient = true
            when (json.peek()) {
                JsonToken.BEGIN_ARRAY -> {
                    json.beginArray()
                    while (json.hasNext()) {
                        val value = readValue(json)
                        if (value is Map<*, *>) block(value)
                    }
                    json.endArray()
                }
                JsonToken.BEGIN_OBJECT -> {
                    json.beginObject()
                    while (json.hasNext()) {
                        json.nextName()
                        val value = readValue(json)
                        if (value is Map<*, *>) block(value)
                    }
                    json.endObject()
                }
                else -> json.skipValue()
            }
        }
    }

    private fun readValue(json: JsonReader): Any? = when (json.peek()) {
        JsonToken.BEGIN_OBJECT -> {
            val map = HashMap<String, Any?>()
            json.beginObject()
            while (json.hasNext()) {
                val name = json.nextName()
                map[name] = readValue(json)
            }
            json.endObject()
            map
        }
        JsonToken.BEGIN_ARRAY -> {
            val list = ArrayList<Any?>()
            json.beginArray()
            while (json.hasNext()) list += readValue(json)
            json.endArray()
            list
        }
        JsonToken.STRING, JsonToken.NUMBER -> json.nextString()
        JsonToken.BOOLEAN -> json.nextBoolean()
        JsonToken.NULL -> {
            json.nextNull(); null
        }
        else -> {
            json.skipValue(); null
        }
    }
}

internal fun Map<*, *>.str(key: String): String? {
    val v = this[key] ?: return null
    if (v is Map<*, *> || v is List<*>) return null
    return v.toString().trim().ifEmpty { null }?.takeIf { it != "null" }
}

internal fun Map<*, *>.long(key: String): Long? {
    val s = str(key) ?: return null
    return s.toLongOrNull() ?: s.toDoubleOrNull()?.toLong()
}

internal fun Map<*, *>.int(key: String): Int? {
    val v = this[key]
    if (v is Boolean) return if (v) 1 else 0
    return long(key)?.toInt()
}

internal fun Map<*, *>.double(key: String): Double? = str(key)?.replace(',', '.')?.toDoubleOrNull()
