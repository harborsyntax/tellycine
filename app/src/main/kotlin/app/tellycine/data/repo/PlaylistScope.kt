package app.tellycine.data.repo

import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.settings.AppSettings

/** The playlists a section (Live TV, Movies or Series) shows: one of them, or all of them together. */
data class PlaylistScope(
    /** In the order they were added. */
    val playlists: List<PlaylistEntity>,
) {
    val ids: List<Long> = playlists.map { it.id }

    /** More than one playlist, so lists mix them. */
    val combined: Boolean get() = playlists.size > 1

    /** "All playlists", or the playlist's name. */
    val label: String get() = if (combined) ALL_LABEL else playlists.firstOrNull()?.name.orEmpty()

    companion object {
        val EMPTY = PlaylistScope(emptyList())
        const val ALL_LABEL = "All playlists"

        /**
         * The playlists a section shows, from its [choice] (a playlist's id, [AppSettings.ALL_PLAYLISTS],
         * or null) among [all], falling back to the [active] playlist.
         */
        fun of(all: List<PlaylistEntity>, active: PlaylistEntity?, choice: Long?): PlaylistScope = when {
            choice == AppSettings.ALL_PLAYLISTS -> PlaylistScope(all)
            choice != null -> PlaylistScope(listOfNotNull(all.firstOrNull { it.id == choice } ?: active))
            else -> PlaylistScope(listOfNotNull(active))
        }
    }
}

/**
 * A section's playlist filter: "All playlists", then each playlist. [selected] is an option's id;
 * "All playlists" is [AppSettings.ALL_PLAYLISTS]. Only offered with more than one playlist.
 */
data class PlaylistFilter(val options: List<Option>, val selected: Long) {
    data class Option(val id: Long, val name: String)

    companion object {
        fun of(all: List<PlaylistEntity>, scope: PlaylistScope): PlaylistFilter? {
            if (all.size < 2) return null
            val options = listOf(Option(AppSettings.ALL_PLAYLISTS, PlaylistScope.ALL_LABEL)) + all.map { Option(it.id, it.name) }
            val selected = if (scope.combined) AppSettings.ALL_PLAYLISTS else scope.ids.firstOrNull() ?: AppSettings.ALL_PLAYLISTS
            return PlaylistFilter(options, selected)
        }
    }
}
