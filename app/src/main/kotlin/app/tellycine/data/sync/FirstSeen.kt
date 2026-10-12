package app.tellycine.data.sync

import app.tellycine.data.db.TellycineDatabase

/**
 * Keeps the date a playlist's movies and series were added across a refresh, which replaces them
 * all. Made just before the old ones are deleted, in the same transaction; [restore] then gives
 * each new one without a date from the provider its old date, or now if it's new.
 *
 * Done in the database, as a playlist can hold many thousands of titles.
 */
class FirstSeen(db: TellycineDatabase, private val playlistId: Long) {
    private val sql = db.openHelper.writableDatabase

    init {
        sql.execSQL("DROP TABLE IF EXISTS temp.first_seen")
        sql.execSQL(
            "CREATE TEMP TABLE first_seen AS SELECT kind, streamId, addedAt FROM streams " +
                "WHERE playlistId = ? AND kind != 'LIVE' AND addedAt IS NOT NULL",
            arrayOf<Any>(playlistId),
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
        sql.execSQL("DROP TABLE temp.first_seen")
    }
}
