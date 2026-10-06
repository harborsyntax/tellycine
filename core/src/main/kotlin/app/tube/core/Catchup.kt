package app.tube.core

import app.tube.core.xtream.XtreamUrls
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

/** Builds catch-up (archive) playback URLs for past or in-progress programmes. */
object Catchup {

    private val xtreamFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH-mm")

    /**
     * Xtream Codes timeshift URL. Classic panels interpret the start time in
     * `server_info.timezone`; Dispatcharr reports (and expects) UTC.
     */
    fun xtream(urls: XtreamUrls, streamId: String, startMs: Long, stopMs: Long, serverZone: ZoneId): String {
        val start = xtreamFormat.format(Instant.ofEpochMilli(startMs).atZone(serverZone))
        return urls.timeshift(streamId, start, durationMinutes(startMs, stopMs))
    }

    fun zoneOrUtc(id: String?): ZoneId =
        id?.takeIf { it.isNotBlank() }?.let { runCatching { ZoneId.of(it.trim()) }.getOrNull() } ?: ZoneOffset.UTC

    /**
     * M3U catch-up following the conventions used by Kodi/TiviMate playlists:
     * `catchup="default"` (template in `catchup-source`), `append`, `shift` and `flussonic`.
     *
     * @return null when the stream doesn't describe a usable catch-up method.
     */
    fun m3u(url: String, type: String?, source: String?, startMs: Long, stopMs: Long, nowMs: Long): String? {
        val t = type?.trim()?.lowercase()
        return when {
            t == "shift" || t == "timeshift" ->
                fill(url + (if ('?' in url) "&" else "?") + "utc={utc}&lutc={lutc}", startMs, stopMs, nowMs)
            t == "flussonic" || t == "flussonic-hls" || t == "flussonic-ts" || t == "fs" -> flussonic(url, startMs, stopMs)
            !source.isNullOrBlank() && (t == "append" || (t != "default" && !source.contains("://"))) ->
                fill(url + source, startMs, stopMs, nowMs)
            !source.isNullOrBlank() -> fill(source, startMs, stopMs, nowMs)
            else -> null
        }
    }

    private fun flussonic(url: String, startMs: Long, stopMs: Long): String? {
        val start = startMs / 1000
        val duration = (stopMs - startMs) / 1000
        val base = url.substringBefore('?')
        val query = url.substringAfter('?', "").let { if (it.isEmpty()) "" else "?$it" }
        val dir = base.substringBeforeLast('/')
        val file = base.substringAfterLast('/')
        return when {
            file == "mpegts" -> "$dir/timeshift_abs-$start.ts$query"
            file.endsWith(".m3u8") -> "$dir/${file.substringBeforeLast('.').let { if (it == "index" || it == "video") it else "index" }}-$start-$duration.m3u8$query"
            else -> null
        }
    }

    /** Replaces the `{...}` / `${...}` placeholders used by catch-up templates. */
    fun fill(template: String, startMs: Long, stopMs: Long, nowMs: Long): String {
        val start = Instant.ofEpochMilli(startMs).atOffset(ZoneOffset.UTC)
        val values = mapOf(
            "utc" to (startMs / 1000).toString(),
            "start" to (startMs / 1000).toString(),
            "timestamp" to (startMs / 1000).toString(),
            "utcend" to (stopMs / 1000).toString(),
            "end" to (stopMs / 1000).toString(),
            "lutc" to (nowMs / 1000).toString(),
            "now" to (nowMs / 1000).toString(),
            "duration" to ((stopMs - startMs) / 1000).toString(),
            "offset" to ((nowMs - startMs) / 1000).toString(),
            "Y" to "%04d".format(start.year),
            "m" to "%02d".format(start.monthValue),
            "d" to "%02d".format(start.dayOfMonth),
            "H" to "%02d".format(start.hour),
            "M" to "%02d".format(start.minute),
            "S" to "%02d".format(start.second),
        )
        return PLACEHOLDER.replace(template) { m ->
            val name = m.groupValues[1]
            values[name] ?: values[name.lowercase()] ?: m.value
        }
    }

    private fun durationMinutes(startMs: Long, stopMs: Long) =
        ceil((stopMs - startMs).coerceAtLeast(60_000) / 60_000.0).toInt()

    private val PLACEHOLDER = Regex("""\$?\{([A-Za-z_]+)}""")
}
