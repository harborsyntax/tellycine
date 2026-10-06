package app.mosaic.core.xmltv

import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset

data class XmltvChannel(
    val id: String,
    val displayNames: List<String>,
    val icon: String?,
)

data class XmltvProgramme(
    val channelId: String,
    val startMs: Long,
    val stopMs: Long,
    val title: String,
    val description: String?,
    val category: String?,
)

/**
 * Streaming XMLTV parser built on [XmlPullParser] so that EPG files of hundreds of
 * megabytes can be processed without loading them into memory.
 *
 * @param parserFactory creates a fresh pull parser (Android: `Xml.newPullParser()`).
 */
class XmltvParser(private val parserFactory: () -> XmlPullParser) {

    /**
     * @param acceptProgramme cheap pre-filter evaluated before the programme body is read,
     *   so programmes for unknown channels or outside the wanted time window are skipped.
     */
    fun parse(
        input: InputStream,
        onChannel: (XmltvChannel) -> Unit = {},
        acceptProgramme: (channelId: String, startMs: Long, stopMs: Long) -> Boolean = { _, _, _ -> true },
        onProgramme: (XmltvProgramme) -> Unit,
    ) {
        val parser = parserFactory()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        // Tolerate undeclared entities such as &nbsp; that are common in real-world guides.
        runCatching { parser.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
        parser.setInput(input, null)

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> readChannel(parser)?.let(onChannel)
                    "programme" -> readProgramme(parser, acceptProgramme)?.let(onProgramme)
                }
            }
            event = parser.next()
        }
    }

    private fun readChannel(parser: XmlPullParser): XmltvChannel? {
        val id = parser.getAttributeValue(null, "id")
        val names = ArrayList<String>(2)
        var icon: String? = null
        val depth = parser.depth
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG && parser.depth == depth) break
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "display-name" -> readText(parser).takeIf { it.isNotBlank() }?.let { names += it.trim() }
                    "icon" -> icon = parser.getAttributeValue(null, "src")
                }
            }
        }
        if (id.isNullOrBlank()) return null
        return XmltvChannel(id, names, icon)
    }

    private fun readProgramme(
        parser: XmlPullParser,
        accept: (String, Long, Long) -> Boolean,
    ): XmltvProgramme? {
        val channel = parser.getAttributeValue(null, "channel")
        val start = parseTime(parser.getAttributeValue(null, "start"))
        val stop = parseTime(parser.getAttributeValue(null, "stop"))
        val depth = parser.depth

        if (channel.isNullOrBlank() || start == null || stop == null || !accept(channel, start, stop)) {
            skipToEnd(parser, depth)
            return null
        }

        var title: String? = null
        var desc: String? = null
        var category: String? = null
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG && parser.depth == depth) break
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "title" -> if (title == null) title = readText(parser)
                    "desc" -> if (desc == null) desc = readText(parser)
                    "category" -> if (category == null) category = readText(parser)
                }
            }
        }
        return XmltvProgramme(
            channelId = channel,
            startMs = start,
            stopMs = stop,
            title = title?.trim().orEmpty().ifEmpty { "No title" },
            description = desc?.trim()?.ifEmpty { null },
            category = category?.trim()?.ifEmpty { null },
        )
    }

    /** Reads the text content of the current element, tolerating nested markup. */
    private fun readText(parser: XmlPullParser): String {
        val depth = parser.depth
        val sb = StringBuilder()
        while (true) {
            val event = parser.next()
            when (event) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> sb.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.depth == depth) return sb.toString()
                XmlPullParser.END_DOCUMENT -> return sb.toString()
            }
        }
    }

    private fun skipToEnd(parser: XmlPullParser, depth: Int) {
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) return
            if (event == XmlPullParser.END_TAG && parser.depth == depth) return
        }
    }

    companion object {
        /**
         * Parses XMLTV timestamps such as `20240131203000 +0100`, `20240131203000`,
         * `202401312030 -0500` or `20240131203000+0000`. Missing offsets are treated as UTC.
         */
        fun parseTime(value: String?): Long? {
            if (value == null) return null
            val s = value.trim()
            var i = 0
            while (i < s.length && s[i].isDigit()) i++
            val digits = s.substring(0, i)
            if (digits.length < 12) return null
            return try {
                val year = digits.substring(0, 4).toInt()
                val month = digits.substring(4, 6).toInt()
                val day = digits.substring(6, 8).toInt()
                val hour = digits.substring(8, 10).toInt()
                val minute = digits.substring(10, 12).toInt()
                val second = if (digits.length >= 14) digits.substring(12, 14).toInt() else 0
                val rest = s.substring(i).trim()
                val offset = parseOffset(rest)
                LocalDateTime.of(year, month, day, hour, minute, second)
                    .toInstant(offset)
                    .toEpochMilli()
            } catch (e: Exception) {
                null
            }
        }

        private fun parseOffset(s: String): ZoneOffset {
            if (s.length < 5 || (s[0] != '+' && s[0] != '-')) return ZoneOffset.UTC
            val sign = if (s[0] == '-') -1 else 1
            val clean = s.substring(1).replace(":", "")
            if (clean.length < 4) return ZoneOffset.UTC
            val h = clean.substring(0, 2).toIntOrNull() ?: return ZoneOffset.UTC
            val m = clean.substring(2, 4).toIntOrNull() ?: return ZoneOffset.UTC
            return try {
                ZoneOffset.ofHoursMinutes(sign * h, sign * m)
            } catch (e: Exception) {
                ZoneOffset.UTC
            }
        }
    }
}
