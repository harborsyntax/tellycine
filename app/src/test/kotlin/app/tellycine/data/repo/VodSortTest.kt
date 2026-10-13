package app.tellycine.data.repo

import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.TellycineApp
import app.tellycine.core.ContentKind
import app.tellycine.data.settings.VodOrder
import app.tellycine.data.settings.VodSort
import app.tellycine.screenshots.DemoData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Movies and series in each order, both ways round (demo dates: see DemoData). */
@RunWith(AndroidJUnit4::class)
class VodSortTest {
    private val container = ApplicationProvider.getApplicationContext<TellycineApp>().container
    private val demo = DemoData.seed(container)
    private val second = SecondPlaylist.seed(container)

    private fun names(ids: List<Long>, kind: ContentKind, category: String, order: VodOrder): List<String> = runBlocking {
        val source = container.content.itemPages(ids, kind, category, order)
        (source.load(PagingSource.LoadParams.Refresh(null, 500, false)) as PagingSource.LoadResult.Page).data.map { it.name }
    }

    private fun movies(order: VodOrder, category: String = Categories.ALL, ids: List<Long> = listOf(demo)) =
        names(ids, ContentKind.MOVIE, category, order)

    private fun movies(sort: VodSort, descending: Boolean = sort.descendingFirst, category: String = Categories.ALL, ids: List<Long> = listOf(demo)) =
        movies(VodOrder(sort, descending), category, ids)

    @Test
    fun moviesInEachOrder() {
        assertEquals("Midnight Harbor", movies(VodSort.PROVIDER).first())
        // The default: newest additions first.
        assertEquals(listOf("Velocity", "Paper Kites", "The Wedding Plan"), movies(VodOrder.DEFAULT).take(3))
        // Two from 2024: the provider's order between them.
        assertEquals(listOf("The Last Signal", "Double Shift"), movies(VodSort.RELEASED).take(2))
        assertEquals(listOf("Double Shift", "Glass Garden", "Iron Valley"), movies(VodSort.NAME).take(3))
    }

    @Test
    fun eachOrderTurnsRound() {
        for (sort in listOf(VodSort.ADDED, VodSort.NAME, VodSort.PROVIDER)) {
            assertEquals(sort.name, movies(sort, descending = false).reversed(), movies(sort, descending = true))
        }
        assertEquals("Northern Lights", movies(VodSort.ADDED, descending = false).first())
        assertEquals(listOf("Velocity", "The Wedding Plan", "The Last Signal"), movies(VodSort.NAME, descending = true).take(3))
        // Oldest releases first, two from 2020 still in the provider's order.
        assertEquals(listOf("Northern Lights", "Velocity"), movies(VodSort.RELEASED, descending = false).take(2))
    }

    @Test
    fun seriesByWhenTheyLastAired() = runBlocking(Dispatchers.IO) {
        val tvmaze = container.database.tvmaze()
        tvmaze.setAired(demo, "sr3", "2025-06-01")
        tvmaze.setAired(demo, "sr1", "2024-01-15")
        tvmaze.setAired(demo, "sr5", "2025-06-01")
        val aired = names(listOf(demo), ContentKind.SERIES, Categories.ALL, VodOrder(VodSort.AIRED))
        // Ties in the provider's order; those TVmaze didn't date come last, either way round.
        assertEquals(listOf("Cold Case Unit", "Flatmates", "Harbour Lane"), aired.take(3))
        val oldest = names(listOf(demo), ContentKind.SERIES, Categories.ALL, VodOrder(VodSort.AIRED, descending = false))
        assertEquals(listOf("Harbour Lane", "Cold Case Unit", "Flatmates"), oldest.take(3))
        assertEquals(aired.drop(3), oldest.drop(3))
    }

    @Test
    fun seriesByTheirLatestEpisode() {
        val newest = names(listOf(demo), ContentKind.SERIES, Categories.ALL, VodOrder(VodSort.UPDATED))
        assertEquals("Office Hours", newest.first())
        assertEquals(newest.reversed(), names(listOf(demo), ContentKind.SERIES, Categories.ALL, VodOrder(VodSort.UPDATED, descending = false)))
    }

    @Test
    fun categoriesAndFavouritesSortToo() {
        assertEquals(listOf("Midnight Harbor", "Glass Garden", "Northern Lights"), movies(VodSort.ADDED, category = "Drama"))
        assertEquals(listOf("Northern Lights", "Glass Garden", "Midnight Harbor"), movies(VodSort.ADDED, descending = false, category = "Drama"))
        // Favourites: newest favourite first in the provider's order (oldest when turned round), or the order chosen.
        assertEquals("The Last Signal", movies(VodSort.PROVIDER, category = Categories.FAVORITES).first())
        assertEquals("The Last Signal", movies(VodSort.PROVIDER, descending = true, category = Categories.FAVORITES).last())
        assertEquals(listOf("Glass Garden", "Orbit", "The Last Signal"), movies(VodSort.NAME, category = Categories.FAVORITES))
        assertEquals(listOf("The Last Signal", "Orbit", "Glass Garden"), movies(VodSort.NAME, descending = true, category = Categories.FAVORITES))
    }

    @Test
    fun titlesWithoutTheDateComeLastEitherWayRound() {
        val both = listOf(demo, second)
        // The second playlist's film has no dates.
        for (descending in listOf(true, false)) {
            assertEquals("Quiet Tide", movies(VodSort.ADDED, descending, ids = both).last())
            assertEquals("Quiet Tide", movies(VodSort.RELEASED, descending, ids = both).last())
        }
        val drama = movies(VodSort.NAME, category = Categories.named("drama"), ids = both)
        assertEquals(listOf("Glass Garden", "Midnight Harbor", "Northern Lights", "Quiet Tide"), drama)
    }

    @Test
    fun smallPagesAddUpToTheWholeList() = runBlocking {
        // Oldest first reads titles with the date, then those without: pages that span both.
        val both = listOf(demo, second)
        val order = VodOrder(VodSort.ADDED, descending = false)
        val source = container.content.itemPages(both, ContentKind.MOVIE, Categories.ALL, order)
        var page = source.load(PagingSource.LoadParams.Refresh(null, 5, false)) as PagingSource.LoadResult.Page
        val paged = page.data.map { it.name }.toMutableList()
        while (page.nextKey != null) {
            page = source.load(PagingSource.LoadParams.Append(page.nextKey!!, 5, false)) as PagingSource.LoadResult.Page
            paged += page.data.map { it.name }
        }
        val whole = movies(order, ids = both)
        assertEquals(whole, paged)
        assertEquals("Quiet Tide", whole.last())
        assertEquals(13, whole.size)
    }
}
