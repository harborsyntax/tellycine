package app.tellycine.data.sync

import app.tellycine.data.db.TellycineDatabase

/**
 * Keeps what Tellycine worked out about a playlist's movies and series across a refresh, which
 * replaces them all. Made just before the old ones are deleted, in the same transaction; [restore]
 * then gives each new one without a date from the provider its old date, or now if it's new, and
 * each series its change date ([app.tellycine.data.db.StreamEntity.changedAt]) and air date.
 *
 * A series' change date is the provider's date when there is one. Otherwise it moves to now when
 * the series has gained episodes since the last refresh, which [countsEpisodes] playlists can tell
 * (M3U playlists list every episode; Xtream Codes ones load them only when a series is opened).
 *
 * Done in the database, as a playlist can hold many thousands of titles.
 */
class FirstSeen(db: TellycineDatabase, private val playlistId: Long, private val countsEpisodes: Boolean = false) {
    private val sql = db.openHelper.writableDatabase

    init {
        sql.execSQL("DROP TABLE IF EXISTS temp.first_seen")
        sql.execSQL(
            """CREATE TEMP TABLE first_seen AS SELECT kind, streamId, addedAt, changedAt, aired,
                 CASE WHEN ? AND kind = 'SERIES' THEN $EPISODES END AS episodes
               FROM streams WHERE playlistId = ? AND kind != 'LIVE'""",
            arrayOf<Any>(if (countsEpisodes) 1 else 0, playlistId),
        )
        sql.execSQL("CREATE INDEX temp.first_seen_key ON first_seen (kind, streamId)")
    }

    fun restore(now: Long = System.currentTimeMillis()) {
        sql.execSQL(
            """UPDATE streams SET addedAt = COALESCE(
                 (SELECT f.addedAt FROM temp.first_seen f WHERE f.kind = streams.kind AND f.streamId = streams.streamId), ?)
               WHERE playlistId = ? AND kind != 'LIVE' AND addedAt IS NULL""",
            arrayOf<Any>(now, playlistId),
        )
        sql.execSQL(
            """UPDATE streams SET
                 aired = (SELECT f.aired FROM temp.first_seen f WHERE f.kind = streams.kind AND f.streamId = streams.streamId),
                 changedAt = COALESCE(updatedAt,
                   (SELECT CASE WHEN f.episodes IS NOT NULL AND $EPISODES > f.episodes THEN ? ELSE f.changedAt END
                      FROM temp.first_seen f WHERE f.kind = streams.kind AND f.streamId = streams.streamId),
                   addedAt)
               WHERE playlistId = ? AND kind = 'SERIES'""",
            arrayOf<Any>(now, playlistId),
        )
        sql.execSQL("DROP TABLE temp.first_seen")
    }

    private companion object {
        /** How many episodes the series (`streams` row) has. */
        const val EPISODES = "(SELECT COUNT(*) FROM episodes e WHERE e.playlistId = streams.playlistId AND e.seriesId = streams.streamId)"
    }
}
