package app.tellycine.player.multiview

/**
 * How many channels multi-view may play: up to [MAX_CHANNELS], and no more from one playlist than
 * its connection limit, since each channel is a connection to the provider.
 */
object MultiViewLimits {
    const val MAX_CHANNELS = 4

    /**
     * Why a channel from [playlistId] can't join the channels playing (from the playlists in
     * [playing]), or null if it can. [replacing] is the index of a channel it would take the
     * place of. [connectionsOf] gives a playlist's connection limit, null when unknown.
     */
    fun refusal(playing: List<Long>, playlistId: Long, replacing: Int?, connectionsOf: (Long) -> Int?): String? {
        val others = if (replacing != null) playing.filterIndexed { i, _ -> i != replacing } else playing
        if (others.size >= MAX_CHANNELS) return "Multi-view shows up to $MAX_CHANNELS channels. Remove one to add another."
        val limit = connectionsOf(playlistId) ?: return null
        val used = others.count { it == playlistId }
        if (used < limit) return null
        return "This playlist allows $limit connection${if (limit == 1) "" else "s"} at once, " +
            "and ${if (used == 1) "1 is" else "$used are"} in use. Remove a channel first, or change the limit in the playlist's settings."
    }

    /** How many more channels from [playlistId] could be added. */
    fun spare(playing: List<Long>, playlistId: Long, connections: Int?): Int {
        val bySlots = MAX_CHANNELS - playing.size
        val byConnections = (connections ?: MAX_CHANNELS) - playing.count { it == playlistId }
        return minOf(bySlots, byConnections).coerceAtLeast(0)
    }
}
