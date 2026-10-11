package app.tellycine.data.repo

import app.tellycine.core.ContentKind
import app.tellycine.data.db.CategoryEntity
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import app.tellycine.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistScopeTest {
    private fun playlist(id: Long, name: String) = PlaylistEntity(id = id, name = name, type = PlaylistType.M3U_URL, url = "https://example.com/$id.m3u")
    private val one = playlist(1, "One")
    private val two = playlist(2, "Two")

    @Test
    fun aSectionShowsItsChoiceAllOrTheActivePlaylist() {
        assertEquals(listOf(1L, 2L), PlaylistScope.of(listOf(one, two), active = two, choice = AppSettings.ALL_PLAYLISTS).ids)
        assertEquals(listOf(1L), PlaylistScope.of(listOf(one, two), active = two, choice = 1).ids)
        assertEquals(listOf(2L), PlaylistScope.of(listOf(one, two), active = two, choice = null).ids)
        // A deleted choice falls back to the active playlist.
        assertEquals(listOf(2L), PlaylistScope.of(listOf(one, two), active = two, choice = 9).ids)
        // "All" with a single playlist is just that playlist.
        assertFalse(PlaylistScope.of(listOf(one), active = one, choice = AppSettings.ALL_PLAYLISTS).combined)
        assertEquals("All playlists", PlaylistScope.of(listOf(one, two), two, AppSettings.ALL_PLAYLISTS).label)
        assertEquals("Two", PlaylistScope.of(listOf(one, two), two, null).label)
    }

    @Test
    fun theFilterIsOfferedWithMoreThanOnePlaylist() {
        assertNull(PlaylistFilter.of(listOf(one), PlaylistScope(listOf(one))))
        val all = PlaylistFilter.of(listOf(one, two), PlaylistScope(listOf(one, two)))!!
        assertEquals(listOf("All playlists", "One", "Two"), all.options.map { it.name })
        assertEquals(AppSettings.ALL_PLAYLISTS, all.selected)
        assertEquals(2L, PlaylistFilter.of(listOf(one, two), PlaylistScope(listOf(two)))!!.selected)
    }

    @Test
    fun categoriesWithTheSameNameAreMergedInPlaylistOrder() {
        fun category(playlist: Long, id: String, name: String, order: Int, count: Int) =
            CategoryEntity(playlist, ContentKind.LIVE, id, name, order, count)
        val merged = Categories.merge(
            listOf(
                category(2, "7", " news ", 0, 3),
                category(2, "8", "Football", 1, 4),
                category(1, "news", "News", 0, 10),
                category(1, "sport", "Sport", 1, 5),
            ),
            playlistIds = listOf(1, 2),
        )
        assertEquals(listOf("News", "Sport", "Football"), merged.map { it.name })
        assertEquals(listOf(13, 5, 4), merged.map { it.itemCount })
        assertEquals(Categories.named("news"), merged[0].categoryId)
        assertEquals("news", Categories.nameKeyOf(merged[0].categoryId))
        assertNull(Categories.nameKeyOf("news"))
    }

    @Test
    fun theMergeKeyLowersOnlyWhatTheDatabaseLowers() {
        // SQLite's LOWER and TRIM only change A–Z and spaces, so the key must too, or the query wouldn't match.
        assertEquals("ñews hd", Categories.nameKey("  ñEWS HD "))
        assertEquals("ÉtÉ", Categories.nameKey("ÉTÉ"))
    }
}
