package app.tellycine.core

import java.text.Normalizer
import java.util.Locale

/**
 * Series names as a TV database knows them. IPTV providers dress names up with language or country
 * codes, quality tags and years, as in "EN - Show Name (2019) [4K]"; [clean] takes those off.
 */
object ShowNames {
    /** A cleaned-up name, and the year it carried in brackets, if any. */
    data class Cleaned(val title: String, val year: Int?)

    // A code in brackets or bars at the start: "[EN] ", "|UK| ", "(NF) - ".
    private val BRACKETED_PREFIX = Regex("""^\s*[\[(|]\s*(?=[A-Z0-9+ ]*[A-Z])[A-Z0-9+ ]{2,8}\s*[\])|]\s*(?:[-|]\s*)?""")

    // A bare code and a dash or bar: "EN - ", "4K-", "NF | ". Not a colon, which titles use
    // ("CSI: Vegas"), and not digits alone ("24 - Live Another Day").
    private val BARE_PREFIX = Regex("""^\s*(?=[A-Z0-9+]*[A-Z])[A-Z0-9+]{2,5}\s*[-|]\s*""")
    private val BRACKETS = Regex("""\s*[\[(]([^\])\[(]*)[\])]""")
    private val QUALITY_SUFFIX = Regex(
        """(?i)(?:\s+|\s*[-|]\s*)(?:4K|UHD|FHD|HD|SD|HEVC|H\.?26[45]|X26[45]|2160P|1080P|720P|MULTI(?:[- ]?SUBS?)?)\s*$""",
    )
    private val YEAR = Regex("""^\s*((?:18|19|20)\d\d)\s*$""")
    private val SPACES = Regex("""\s+""")

    fun clean(name: String): Cleaned {
        var title = name
        var year: Int? = null
        repeat(3) {
            title = title.replace(BRACKETED_PREFIX, "").replace(BARE_PREFIX, "")
        }
        title = BRACKETS.replace(title) { match ->
            YEAR.find(match.groupValues[1])?.let { year = year ?: it.groupValues[1].toInt() }
            " "
        }
        repeat(3) { title = title.replace(QUALITY_SUFFIX, "") }
        title = title.replace(SPACES, " ").trim(' ', '-', '|', ':', '_', '.')
        return Cleaned(title.ifEmpty { name.trim() }, year)
    }

    /**
     * [title] for comparing names: without accents, punctuation or a leading "The", in lower case,
     * with "&" as "and".
     */
    fun normalise(title: String): String {
        val plain = Normalizer.normalize(title, Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "")
        return plain.lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace(Regex("""['’`]"""), "")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
            .removePrefix("the ")
    }

    /** Which series count as the same one: the same [normalise]d name and year. */
    fun key(title: String, year: Int?): String = "${normalise(title)}|${year ?: ""}"

    /** The year in a provider's year or release date ("2019", "2019-05-03"), if it looks like one. */
    fun yearOf(value: String?): Int? = value?.trim()?.take(4)?.toIntOrNull()?.takeIf { it in 1888..2100 }
}
