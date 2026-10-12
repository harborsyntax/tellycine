package app.tellycine.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Updating the app keeps what's in the database: a version 1 database (Tellycine 1.0 to 1.9.2). */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    private fun createVersion1() {
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            javaClass.getResource("/tellycine-db-v1.sql")!!.readText()
                .lines().filterNot { it.startsWith("--") }.joinToString("\n")
                .split(";\n").map { it.trim() }.filter { it.isNotEmpty() }
                .forEach { db.execSQL(it) }
            db.execSQL(
                """INSERT INTO playlists (id, name, type, url, username, password, epgUrl, detectedEpgUrl, userAgent, createdAt,
                     syncState, liveCount, movieCount, seriesCount, programmeCount)
                   VALUES (1, 'Mine', 'XTREAM', 'http://example.com', 'u', 'p', '', '', '', 1, 'IDLE', 0, 1, 1, 0)"""
            )
            db.execSQL(
                """INSERT INTO streams (playlistId, kind, streamId, name, sortOrder, url, year, addedAt)
                   VALUES (1, 'MOVIE', 'm1', 'A Film', 0, 'http://example.com/m1.mkv', '2021', 1000),
                          (1, 'SERIES', 's1', 'A Series', 0, '', '2019-05-01', 2000)"""
            )
            db.execSQL("INSERT INTO favorites (playlistId, kind, streamId, addedAt) VALUES (1, 'MOVIE', 'm1', 5)")
            db.version = 1
        }
    }

    @Test
    fun version1KeepsEverythingAndGainsReleaseAndUpdateDates() {
        createVersion1()
        // No destructive fallback here: a migration that doesn't match the schema fails the test.
        val room = Room.databaseBuilder(context, TellycineDatabase::class.java, name)
            .addMigrations(TellycineDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val db = room.openHelper.readableDatabase
            fun row(id: String) = db.query("SELECT addedAt, updatedAt, released FROM streams WHERE streamId = ?", arrayOf(id)).use { c ->
                c.moveToFirst()
                Triple(c.getLong(0).takeUnless { c.isNull(0) }, c.getLong(1).takeUnless { c.isNull(1) }, c.getString(2))
            }
            // The movie keeps its added date; a series' old "added" date was its last update.
            assertEquals(Triple(1000L, null, "2021"), row("m1"))
            val series = row("s1")
            assertNull(series.first)
            assertEquals(2000L, series.second)
            assertEquals("2019", series.third)
            db.query("SELECT sortName FROM streams WHERE streamId = 'm1'").use { it.moveToFirst(); assertEquals("a film", it.getString(0)) }
            db.query("SELECT COUNT(*) FROM favorites").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            db.query("SELECT name FROM playlists").use { it.moveToFirst(); assertEquals("Mine", it.getString(0)) }
        } finally {
            room.close()
        }
    }
}
