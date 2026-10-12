package app.tellycine.core

/**
 * Release dates as providers give them ("2023-05-01", "2023-05-01 20:00:00", "2023", "05/2023"…),
 * reduced to a form that sorts: "2023-05-01" when the day is known, otherwise just "2023".
 */
object ReleaseDates {
    private val FULL = Regex("""^\s*(\d{4})-(\d{2})-(\d{2})""")
    private val YEAR = Regex("""(?<!\d)(\d{4})(?!\d)""")

    /** The sortable date in [raw], or null if it holds no plausible year. */
    fun normalise(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        FULL.find(raw)?.let { m ->
            val (y, mo, d) = m.destructured
            if (plausible(y.toInt()) && mo.toInt() in 1..12 && d.toInt() in 1..31) return "$y-$mo-$d"
        }
        return YEAR.find(raw)?.groupValues?.get(1)?.takeIf { plausible(it.toInt()) }
    }

    /** The first of [values] that holds a date ([normalise]). */
    fun first(vararg values: String?): String? = values.firstNotNullOfOrNull { normalise(it) }

    private val IN_TITLE = Regex("""[(\[]\s*(\d{4})\s*[)\]]""")

    /**
     * A year in brackets in a title, as M3U playlists often name films: "Film Name (2023)" or
     * "Film Name [2023]". Bare numbers aren't taken, as they're often part of the name ("2012").
     */
    fun yearInTitle(title: String): String? =
        IN_TITLE.findAll(title).lastOrNull()?.groupValues?.get(1)?.takeIf { plausible(it.toInt()) }

    private fun plausible(year: Int) = year in 1888..2100
}
