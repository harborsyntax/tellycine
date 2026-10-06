package app.tube.core

/** The three kinds of content an IPTV source can expose. */
enum class ContentKind {
    LIVE,
    MOVIE,
    SERIES;

    companion object {
        private val vodExtensions = setOf("mp4", "mkv", "avi", "mov", "m4v", "wmv", "flv", "webm", "mpg", "mpeg")

        /**
         * Best-effort classification of an M3U entry. Xtream-backed M3U files use
         * `/movie/` and `/series/` path segments, which is by far the most common case.
         */
        fun classify(url: String): ContentKind {
            val lower = url.lowercase()
            if ("/movie/" in lower) return MOVIE
            if ("/series/" in lower) return SERIES
            val path = lower.substringBefore('?').substringBefore('#')
            val ext = path.substringAfterLast('/', "").substringAfterLast('.', "")
            return if (ext in vodExtensions) MOVIE else LIVE
        }
    }
}
