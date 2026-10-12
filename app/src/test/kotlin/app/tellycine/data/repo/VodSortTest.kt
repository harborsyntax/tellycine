package app.tellycine.data.repo

import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.TellycineApp
import app.tellycine.core.ContentKind
import app.tellycine.data.settings.VodSort
import app.tellycine.screenshots.DemoData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Movies and series in each order (demo dates: see DemoData). */
@RunWith(AndroidJUnit4::class)
class VodSortTest {
    private val container = ApplicationProvider.getApplicationContext<TellycineApp>().container
    private val demo = DemoData.seed(container)
    private val second = SecondPlaylist.seed(container)

    private fun names(ids: List<Long>, kind: ContentKind, category: String, sort: VodSort): List<String> = runBlocking {
        val source = container.content.itemPages(ids, kind, category, sort)
        (source.load(PagingSource.LoadParams.Refresh(null, 500, false)) as PagingSource.LoadResult.Page).data.map { it.name }
    }

    private fun movies(sort: VodSort, category: String = Categories.ALL, ids: List<Long> = listOf(demo)) = names(ids, ContentKind.MOVIE, category, sort)

    @Test
    fun moviesInEachOrder() {
        assertEquals("Midnight Harbor", movies(VodSort.PROVIDER).first())
        assertEquals(listOf("Velocity", "Paper Kites", "The Wedding Plan"), movies(VodSort.ADDED).take(3))
        // Two from 2024: the provider's order between them.
        assertEquals(listOf("The Last Signal", "Double Shift"), movies(VodSort.RELEASED).take(2))
        assertEquals(listOf("Double Shift", "Glass Garden", "Iron Valley"), movies(VodSort.NAME).take(3))
    }

    @Test
    fun seriesByTheirLatestEpisode() {
        assertEquals("Office Hours", names(listOf(demo), ContentKind.SERIES, Categories.ALL, VodSort.UPDATED).first())
    }

    @Test
    fun categoriesAndFavouritesSortToo() {
        assertEquals(listOf("Midnight Harbor", "Glass Garden", "Northern Lights"), movies(VodSort.ADDED, category = "Drama"))
        // Favourites: newest favourite first in the provider's order, or the order chosen.
        assertEquals("The Last Signal", movies(VodSort.PROVIDER, category = Categories.FAVORITES).first())
        assertEquals(listOf("Glass Garden", "Orbit", "The Last Signal"), movies(VodSort.NAME, category = Categories.FAVORITES))
    }

    @Test
    fun titlesWithoutTheDateComeLastWhenPlaylistsAreCombined() {
        val both = listOf(demo, second)
        // The second playlist's film has no dates.
        assertEquals("Quiet Tide", movies(VodSort.ADDED, ids = both).last())
        assertEquals("Quiet Tide", movies(VodSort.RELEASED, ids = both).last())
        val drama = movies(VodSort.NAME, category = Categories.named("drama"), ids = both)
        assertEquals(listOf("Glass Garden", "Midnight Harbor", "Northern Lights", "Quiet Tide"), drama)
    }
}
