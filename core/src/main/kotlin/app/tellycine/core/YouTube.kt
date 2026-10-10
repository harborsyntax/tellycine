package app.tellycine.core

/** YouTube addresses, for trailers: panels give a bare video id or one of YouTube's link styles. */
object YouTube {
    private val ID = Regex("[A-Za-z0-9_-]{11}")
    private val LINK = Regex(
        """(?:youtube(?:-nocookie)?\.com/(?:watch\?(?:[^#]*&)?v=|embed/|shorts/|live/|v/)|youtu\.be/)([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])""",
        RegexOption.IGNORE_CASE,
    )

    /** The video id in [value], or null if it isn't a YouTube video. */
    fun videoId(value: String?): String? {
        val v = value?.trim().orEmpty()
        if (v.isEmpty()) return null
        if (ID.matches(v)) return v
        return LINK.find(v)?.groupValues?.get(1)
    }

    fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

    /** The standard watch address for a trailer given as [value], or null if it isn't a YouTube video. */
    fun trailerUrl(value: String?): String? = videoId(value)?.let(::watchUrl)
}
