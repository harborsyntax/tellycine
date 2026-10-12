package app.tellycine.data.repo

import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.TellycineApp
import app.tellycine.core.ContentKind
import app.tellycine.data.settings.AppSettings
import app.tellycine.data.settings.CategorySort
import app.tellycine.data.settings.VodSort
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
        val all = page(container.content.channelPages(ids, Categories.ALL) { now })
        val demoCount = page(dao.pagedChannels(demo)).size
        assertEquals(demoCount + 2, all.size)
        assertEquals("Metro News 24", all.first().stream.name)
        assertEquals(listOf("Second News HD", "Second Football"), all.takeLast(2).map { it.stream.name })
        // What's on now is filled in for each page, from each channel's own playlist.
        assertEquals("Second Headlines", all.first { it.stream.playlistId == second && it.stream.streamId == "ch0" }.nowTitle)
        assertEquals("World Tonight", all.first().nowTitle)
        assertEquals(null, all.first { it.stream.name == "Second Football" }.nowTitle)
    }

    @Test
    fun aPlaylistsPagesSayWhatsOnNowToo() {
        val channels = page(container.content.channelPages(listOf(demo), Categories.ALL) { now })
        assertEquals("World Tonight", channels.first().nowTitle)
        assertTrue(channels.first().nowStop!! > now)
        // Only the page is looked up: the list's own query (which Room also counts) leaves it out.
        assertEquals(null, page(dao.pagedChannels(demo)).first().nowTitle)
    }

    @Test
    fun invalidatingThePagesOrTheirRowsInvalidatesBoth() {
        val rows = dao.pagedChannelsIn(ids)
        val pages = NowPlayingPages(rows) { it }
        pages.invalidate()
        assertTrue(rows.invalid)
        val rows2 = dao.pagedChannelsIn(ids)
        val pages2 = NowPlayingPages(rows2) { it }
        rows2.invalidate()
        assertTrue(pages2.invalid)
    }

    @Test
    fun categoriesWithTheSameNameShowEachPlaylistsChannels() = runBlocking {
        val categories = container.content.categories(ids, ContentKind.LIVE).first()
        val news = categories.first { it.name == "News" }
        assertEquals(Categories.named("news"), news.categoryId)
        assertEquals("Football", categories.last().name)
        assertEquals(
            listOf("Metro News 24", "Global Report", "Second News HD"),
            page(dao.pagedChannelsInNamedCategory(ids, "news")).map { it.stream.name },
        )
        val dramas = page(container.content.itemPages(ids, ContentKind.MOVIE, Categories.named("drama"), VodSort.PROVIDER)).map { it.name }
        assertTrue(dramas.containsAll(listOf("Midnight Harbor", "Northern Lights", "Quiet Tide")))
        assertEquals("Quiet Tide", dramas.last())
    }

    @Test
    fun aChannelsPlaceInEachListMatchesThePages() = runBlocking {
        val content = container.content
        val channel = content.getStream(second, ContentKind.LIVE, "ch0")!!
        content.toggleFavorite(second, ContentKind.LIVE, "ch0")
        for (category in listOf(Categories.ALL, Categories.named("news"), Categories.FAVORITES)) {
            val source = when (category) {
                Categories.ALL -> dao.pagedChannelsIn(ids)
                Categories.FAVORITES -> dao.pagedFavoriteChannelsIn(ids)
                else -> dao.pagedChannelsInNamedCategory(ids, "news")
            }
            val expected = page(source).indexOfFirst { it.stream.playlistId == second && it.stream.streamId == "ch0" }
            assertEquals(category, expected, content.channelPosition(ids, category, channel))
        }
        // One playlist, in one of its own categories.
        val football = content.getStream(second, ContentKind.LIVE, "ch1")!!
        assertEquals(0, content.channelPosition(listOf(second), "8", football))
        assertEquals(1, content.channelPosition(listOf(second), Categories.ALL, football))
    }

    @Test
    fun categoriesSortByNameOrStayInTheProvidersOrder() = runBlocking {
        val merged = container.content.categories(ids, ContentKind.LIVE).first()
        assertEquals(listOf("News", "Sport", "Entertainment", "Kids", "Documentaries", "Music", "Football"), Categories.sorted(merged, CategorySort.PROVIDER).map { it.name })
        assertEquals(listOf("Documentaries", "Entertainment", "Football", "Kids", "Music", "News", "Sport"), Categories.sorted(merged, CategorySort.NAME).map { it.name })
        // Names are compared without their surrounding spaces.
        val own = container.content.categories(second, ContentKind.LIVE).first()
        assertEquals(listOf("Football", " news "), Categories.sorted(own, CategorySort.NAME).map { it.name })
    }

    @Test
    fun hiddenCategoriesStayHiddenWhenCombined() = runBlocking {
        container.content.setCategoriesVisible(second, ContentKind.LIVE, listOf("7"), visible = false)
        assertEquals(listOf("Metro News 24", "Global Report"), page(dao.pagedChannelsInNamedCategory(ids, "news")).map { it.stream.name })
        assertTrue(page(dao.pagedChannelsIn(ids)).none { it.stream.name == "Second News HD" })
    }

    @Test
    fun favouritesFromEveryPlaylist() = runBlocking {
        container.content.toggleFavorite(second, ContentKind.LIVE, "ch1")
        container.content.toggleFavorite(second, ContentKind.MOVIE, "mv0")
        val channels = page(dao.pagedFavoriteChannelsIn(ids)).map { it.stream.name }
        assertEquals("Second Football", channels.first())
        assertTrue(channels.size > 1)
        assertEquals("Quiet Tide", page(container.content.itemPages(ids, ContentKind.MOVIE, Categories.FAVORITES, VodSort.PROVIDER)).first().name)
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
