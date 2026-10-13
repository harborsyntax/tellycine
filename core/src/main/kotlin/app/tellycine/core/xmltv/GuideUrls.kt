package app.tellycine.core.xmltv

/**
 * A playlist's XMLTV guide URLs. The ones you add are kept one per line; older settings, and the
 * guides an M3U header names, may be separated by commas or spaces instead.
 */
object GuideUrls {
    fun split(text: String): List<String> =
        text.split(',', ' ', '\t', '\n', '\r').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /** [urls] as stored: one per line, without blanks or repeats. */
    fun join(urls: List<String>): String = split(urls.joinToString("\n")).joinToString("\n")

    /**
     * The guides to load: the ones you added ([own]) in place of the playlist's or provider's own
     * ([supplied]), or after them when [combine] is on. With none added, the playlist's own.
     */
    fun toLoad(own: String, supplied: String, combine: Boolean): List<String> {
        val added = split(own)
        val theirs = split(supplied)
        return when {
            added.isEmpty() -> theirs
            combine -> (theirs + added).distinct()
            else -> added
        }
    }
}
