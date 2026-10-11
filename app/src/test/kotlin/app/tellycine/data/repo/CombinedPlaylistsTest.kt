package app.tellycine.data.repo

import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.TellycineApp
import app.tellycine.core.ContentKind
import app.tellycine.data.settings.AppSettings
import app.tellycine.screenshots.DemoData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CombinedPlaylistsTest {
    private val container = ApplicationProvider.getApplicationContext<TellycineApp>().container
    private val demo = DemoData.seed(container)
    private val second = SecondPlaylist.seed(container)
    private val ids = listOf(demo, second)
    private val dao = container.database.content()
    private val now = System.currentTimeMillis()

    private fun <T : Any> page(source: PagingSource<Int, T>): List<T> = runBlocking {
        (source.load(PagingSource.LoadParams.Refresh(null, 500, false)) as PagingSource.LoadResult.Page).data
    }

    @Test
    fun channelsComeOnePlaylistAfterAnother() {
        val all = page(dao.pagedChannelsIn(ids, now))
        val demoCount = page(dao.pagedChannels(demo, now)).size
        assertEquals(demoCount + 2, all.size)
        assertEquals("Metro News 24", all.first().stream.name)
        assertEquals(listOf("Second News HD", "Second Football"), all.takeLast(2).map { it.stream.name })
        assertEquals("Second Headlines", all.first { it.stream.playlistId == second && it.stream.streamId == "ch0" }.nowTitle)
    }

    @Test
    fun categoriesWithTheSameNameShowEachPlaylistsChannels() = runBlocking {
        val categories = container.content.categories(ids, ContentKind.LIVE).first()
        val news = categories.first { it.name == "News" }
        assertEquals(Categories.named("news"), news.categoryId)
        assertEquals("Football", categories.last().name)
        assertEquals(
            listOf("Metro News 24", "Global Report", "Second News HD"),
            page(dao.pagedChannelsInNamedCategory(ids, "news", now)).map { it.stream.name },
        )
        val dramas = page(dao.pagedItemsInNamedCategory(ids, ContentKind.MOVIE, "drama")).map { it.name }
        assertTrue(dramas.containsAll(listOf("Midnight Harbor", "Northern Lights", "Quiet Tide")))
        assertEquals("Quiet Tide", dramas.last())
    }

    @Test
    fun hiddenCategoriesStayHiddenWhenCombined() = runBlocking {
        container.content.setCategoriesVisible(second, ContentKind.LIVE, listOf("7"), visible = false)
        assertEquals(listOf("Metro News 24", "Global Report"), page(dao.pagedChannelsInNamedCategory(ids, "news", now)).map { it.stream.name })
        assertTrue(page(dao.pagedChannelsIn(ids, now)).none { it.stream.name == "Second News HD" })
    }

    @Test
    fun favouritesFromEveryPlaylist() = runBlocking {
        container.content.toggleFavorite(second, ContentKind.LIVE, "ch1")
        container.content.toggleFavorite(second, ContentKind.MOVIE, "mv0")
        val channels = page(dao.pagedFavoriteChannelsIn(ids, now)).map { it.stream.name }
        assertEquals("Second Football", channels.first())
        assertTrue(channels.size > 1)
        assertEquals("Quiet Tide", page(dao.pagedFavoriteItemsIn(ids, ContentKind.MOVIE)).first().name)
    }

    @Test
    fun eachSectionHasItsOwnChoiceUntilAPlaylistIsUsedEverywhere() = runBlocking {
        val playlists = container.playlists
        playlists.setSectionPlaylists(ContentKind.LIVE, AppSettings.ALL_PLAYLISTS)
        playlists.setSectionPlaylists(ContentKind.SERIES, second)
        assertEquals(ids, playlists.scope(ContentKind.LIVE).first().ids)
        assertEquals(listOf(demo), playlists.scope(ContentKind.MOVIE).first().ids)
        assertEquals(listOf(second), playlists.scope(ContentKind.SERIES).first().ids)

        playlists.setActive(second)
        ContentKind.entries.forEach { assertEquals(listOf(second), playlists.scope(it).first().ids) }
    }
}
