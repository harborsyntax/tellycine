package app.mosaic.core.dispatcharr

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.Reader
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** A DVR recording on a Dispatcharr server (`/api/channels/recordings/`). */
data class DvrRecording(
    val id: Long,
    /** Dispatcharr channel id; identical to the Xtream `stream_id` of that channel. */
    val channelId: Long,
    val startMs: Long,
    val endMs: Long,
    val title: String,
    val subtitle: String?,
    val description: String?,
    /** Raw `custom_properties.status` (e.g. recording, completed, interrupted), if set. */
    val rawStatus: String?,
    /** Absolute or server-relative poster URL. */
    val posterUrl: String?,
    val posterLogoId: Long?,
    val season: Int?,
    val episode: Int?,
) {
    enum class State { SCHEDULED, RECORDING, COMPLETED, FAILED }

    fun state(nowMs: Long): State = when (rawStatus?.lowercase()) {
        "completed" -> State.COMPLETED
        "interrupted", "stopped", "error", "failed" -> if (endMs > nowMs && startMs <= nowMs) State.RECORDING else State.FAILED
        "recording" -> if (endMs > nowMs) State.RECORDING else State.COMPLETED
        else -> when {
            startMs > nowMs -> State.SCHEDULED
            endMs > nowMs -> State.RECORDING
            else -> State.COMPLETED
        }
    }
}

/** JSON helpers for the parts of the Dispatcharr REST API that Mosaic uses. */
object DispatcharrParser {

    /** `POST /api/accounts/token/` → access token. */
    fun parseAccessToken(reader: Reader): String? =
        (JsonParser.parseReader(reader) as? JsonObject)?.string("access")

    /** `GET /api/accounts/api-keys/` or `POST .../generate/` → the user's API key, if any. */
    fun parseApiKey(reader: Reader): String? =
        (JsonParser.parseReader(reader) as? JsonObject)?.string("key")

    /** `GET /api/channels/recordings/`; accepts a plain array or a paginated `{results: [...]}` page. */
    fun parseRecordings(reader: Reader): List<DvrRecording> {
        val root = JsonParser.parseReader(reader)
        val array: JsonArray = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject && root.asJsonObject.get("results")?.isJsonArray == true -> root.asJsonObject.getAsJsonArray("results")
            else -> return emptyList()
        }
        return array.mapNotNull { (it as? JsonObject)?.let(::parseRecording) }
    }

    private fun parseRecording(o: JsonObject): DvrRecording? {
        val id = o.long("id") ?: return null
        val channel = o.long("channel") ?: return null
        val start = parseTime(o.string("start_time")) ?: return null
        val end = parseTime(o.string("end_time")) ?: return null
        val cp = o.get("custom_properties") as? JsonObject ?: JsonObject()
        val program = cp.get("program") as? JsonObject ?: JsonObject()
        return DvrRecording(
            id = id,
            channelId = channel,
            startMs = start,
            endMs = end,
            title = program.string("title") ?: cp.string("title") ?: "Recording",
            subtitle = program.string("sub_title") ?: program.string("subtitle"),
            description = program.string("description"),
            rawStatus = cp.string("status"),
            posterUrl = cp.string("poster_url"),
            posterLogoId = cp.long("poster_logo_id"),
            season = cp.long("season")?.toInt() ?: program.long("season")?.toInt(),
            episode = cp.long("episode")?.toInt() ?: program.long("episode")?.toInt(),
        )
    }

    /** Body for `POST /api/channels/recordings/`, mirroring Dispatcharr's own guide. */
    fun recordingRequest(channelId: Long, startMs: Long, endMs: Long, title: String, description: String?): String {
        val program = JsonObject().apply {
            addProperty("title", title)
            if (description != null) addProperty("description", description)
            addProperty("start_time", Instant.ofEpochMilli(startMs).toString())
            addProperty("end_time", Instant.ofEpochMilli(endMs).toString())
        }
        return JsonObject().apply {
            addProperty("channel", channelId.toString())
            addProperty("start_time", Instant.ofEpochMilli(startMs).toString())
            addProperty("end_time", Instant.ofEpochMilli(endMs).toString())
            add("custom_properties", JsonObject().apply { add("program", program) })
        }.toString()
    }

    /** Extracts a human-readable message from a DRF error body such as `{"detail": "..."}`. */
    fun parseError(body: String): String? = runCatching {
        when (val root = JsonParser.parseString(body)) {
            is JsonObject -> root.string("detail")
                ?: root.entrySet().firstOrNull()?.value?.let { v -> if (v.isJsonArray) v.asJsonArray.firstOrNull()?.asStringOrNull() else v.asStringOrNull() }
            is JsonArray -> root.firstOrNull()?.asStringOrNull()
            else -> null
        }
    }.getOrNull()

    fun parseTime(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
        }
    }

    private fun JsonObject.string(key: String): String? = get(key)?.asStringOrNull()?.trim()?.ifEmpty { null }
    private fun JsonObject.long(key: String): Long? = string(key)?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }
    private fun JsonElement.asStringOrNull(): String? = if (isJsonPrimitive) asString else null
}
