package app.mosaic.core.xmltv

/**
 * Maps XMLTV channels onto a playlist's guide ids ([wanted], lower-cased) while a guide is
 * imported, so each playlist channel gets one clean schedule:
 *
 * - **One source per channel.** A playlist channel is fed by a single XMLTV channel: one whose
 *   id matches exactly, else the first whose display name matches. Without this, a channel
 *   matched by name (e.g. "BBC One") picks up every regional or HD variant with that name and
 *   shows each programme several times.
 * - **No repeats.** A programme starting at the same time on the same channel is only kept once,
 *   which covers guides that list a channel twice and playlists with several guide files.
 *
 * XMLTV lists a file's channels before its programmes, so call [onChannel] for them first.
 */
class EpgMatcher(private val wanted: Set<String>) {
    /** Playlist guide id -> the XMLTV channel (lower-cased id) that feeds it. */
    private val owner = HashMap<String, String>()

    /** Reverse of [owner]: XMLTV channel -> playlist guide ids it feeds. */
    private val feeds = HashMap<String, MutableList<String>>()

    /** Playlist guide ids owned through an exact id match, which a display-name match can't take. */
    private val exact = HashSet<String>()

    /** Start times already stored, per playlist guide id. */
    private val seen = HashMap<String, HashSet<Long>>()

    fun onChannel(id: String, displayNames: List<String>) {
        val idL = id.lowercase()
        if (idL in wanted) claim(idL, idL, exactMatch = true)
        for (name in displayNames) {
            val target = name.lowercase()
            if (target in wanted && target !in owner) claim(target, idL, exactMatch = false)
        }
    }

    /** Playlist guide ids that programmes of XMLTV channel [channelId] belong to; empty to skip them. */
    fun targetsFor(channelId: String): List<String> {
        val idL = channelId.lowercase()
        // Some guides list programmes for channels they never declared; an exact id still counts.
        if (idL in wanted && idL !in exact) claim(idL, idL, exactMatch = true)
        return feeds[idL].orEmpty()
    }

    /** True the first time a programme starting at [startMs] is seen for [target]. */
    fun isNew(target: String, startMs: Long): Boolean = seen.getOrPut(target) { HashSet() }.add(startMs)

    private fun claim(target: String, source: String, exactMatch: Boolean) {
        if (target in exact) return
        owner.put(target, source)?.let { previous -> feeds[previous]?.remove(target) }
        feeds.getOrPut(source) { ArrayList(1) }.let { if (target !in it) it += target }
        if (exactMatch) exact += target
    }
}
