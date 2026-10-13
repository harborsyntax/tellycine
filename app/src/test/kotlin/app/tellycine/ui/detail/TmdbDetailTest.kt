package app.tellycine.ui.detail

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import app.tellycine.TellycineApp
import app.tellycine.core.ContentKind
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.settings.ThemeMode
import app.tellycine.testing.FakeHttpServer
import app.tellycine.tv.TellycineTvTheme
import app.tellycine.tv.detail.TvDetailScreen
import app.tellycine.ui.theme.TellycineTheme
import coil3.SingletonImageLoader
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val app: TellycineApp get() = ApplicationProvider.getApplicationContext()

/**
 * A pretend TMDB that knows the artwork server's film and series, the film's sequel (its
 * collection) and a film like it, both also in the playlist.
 */
private class FakeTmdb : AutoCloseable {
    val server = FakeHttpServer { path, _ ->
        when (path) {
            "/search/movie" -> """{"results":[{"id":900,"title":"Neon Harbour","release_date":"2025-02-14","vote_average":7.1,"vote_count":420}]}"""
            "/movie/900" -> """{"id":900,"title":"Neon Harbour","original_title":"Port Néon","release_date":"2025-02-14",
                "tagline":"Some ships don't want to be found.","overview":"TMDB's plot.","runtime":105,
                "genres":[{"id":53,"name":"Thriller"}],"vote_average":7.1,"vote_count":420,
                "belongs_to_collection":{"id":77,"name":"Neon Harbour Collection"},
                "credits":{"cast":[{"id":1,"name":"Ren Okafor","character":"Mara Quell","order":0}],
                           "crew":[{"id":2,"name":"Ada Marlow","job":"Director","department":"Directing"},
                                   {"id":3,"name":"Ivo Penn","job":"Screenplay","department":"Writing"}]},
                "release_dates":{"results":[{"iso_3166_1":"US","release_dates":[{"type":3,"certification":"R"}]}]},
                "recommendations":{"results":[{"id":901,"title":"Second Feature","release_date":"2024-01-01","vote_average":6.0,"vote_count":10},
                                              {"id":999,"title":"Not In The Playlist","release_date":"2024-01-01"}]}}"""
            "/collection/77" -> """{"id":77,"name":"Neon Harbour Collection","parts":[
                {"id":900,"title":"Neon Harbour","release_date":"2025-02-14"},{"id":902,"title":"Neon Harbour 2","release_date":"2026-08-01"}]}"""
            "/search/tv" -> """{"results":[{"id":800,"name":"Tidewater","first_air_date":"2023-05-01","vote_average":8.2,"vote_count":90}]}"""
            "/tv/800" -> """{"id":800,"name":"Tidewater","first_air_date":"2023-05-01","status":"Returning Series","vote_average":8.2,"vote_count":90,
                "created_by":[{"id":5,"name":"Mo Brennan"}],"networks":[{"id":6,"name":"Channel Nine"}],
                "next_episode_to_air":{"season_number":3,"episode_number":1,"name":"High Water","air_date":"2026-11-02"},
                "seasons":[{"season_number":1,"overview":"The town's first winter.","episode_count":2},{"season_number":2,"episode_count":1}],
                "aggregate_credits":{"cast":[{"id":7,"name":"Lise Varga","roles":[{"character":"Harbourmaster"}],"order":0}]}}"""
            "/tv/800/season/1" -> """{"season_number":1,"episodes":[
                {"episode_number":1,"name":"Low Tide","overview":"The tide goes out further than anyone remembers.","air_date":"2023-05-01"},
                {"episode_number":2,"name":"Slack Water","air_date":"2023-05-08"}]}"""
            else -> null
        }
    }

    /** Seeds the artwork server's playlist with two more films TMDB knows, and turns TMDB on. */
    fun seed(artwork: FakeArtworkServer): Long = runBlocking(Dispatchers.IO) {
        val container = app.container
        container.tmdb.client.baseUrl = server.url
        val pid = artwork.seed(container)
        fun film(id: String, name: String, tmdbId: Int) =
            StreamEntity(pid, ContentKind.MOVIE, id, name, 1, null, null, "${artwork.url}/movie/u/p/$id.mkv", tmdbId = tmdbId)
        container.database.content().insertStreams(listOf(film("m2", "Second Feature", 901), film("m3", "Neon Harbour 2", 902)))
        container.settings.setTmdbApiKey("0123456789abcdef0123456789abcdef")
        container.settings.setTmdbEnabled(true)
        pid
    }

    override fun close() {
        // Settings outlive a test.
        runBlocking { app.container.settings.setTmdbApiKey("") }
        server.close()
    }
}

@OptIn(ExperimentalTestApi::class)
private fun AndroidComposeTestRule<*, ComponentActivity>.waitFor(matcher: SemanticsMatcher) = waitUntilAtLeastOneExists(matcher, 15_000)

private fun AndroidComposeTestRule<*, ComponentActivity>.waitForText(text: String) = waitFor(hasText(text, substring = true))

/** The page, not the rows across it. */
private val page = hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

/** Waits for [text], scrolling the page down to it. */
private fun AndroidComposeTestRule<*, ComponentActivity>.scrollTo(text: String) {
    waitUntil(15_000) {
        runCatching { onNode(page).performScrollToNode(hasText(text, substring = true)) }.isSuccess
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class TmdbDetailTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val artwork = FakeArtworkServer()
    private val tmdb = FakeTmdb()
    private val opened = mutableListOf<StreamEntity>()

    @After
    fun stop() {
        tmdb.close()
        artwork.close()
        SingletonImageLoader.reset()
    }

    private fun show(kind: ContentKind, id: String) {
        useTestImageLoader(app)
        val pid = tmdb.seed(artwork)
        compose.setContent {
            TellycineTheme(themeMode = ThemeMode.DARK, dynamicColor = false) { DetailScreen(kind, pid, id, onBack = {}, onOpen = { opened += it }) }
        }
    }

    @Test
    fun aFilmGainsTmdbsDetailsAndOpensOthersInThePlaylist() {
        show(ContentKind.MOVIE, "m1")
        compose.waitForText("Some ships don't want to be found.")
        compose.waitForText("Port Néon")
        // TMDB's score over the playlist's 7.6, and its age rating.
        compose.waitFor(hasText("7.1"))
        compose.waitFor(hasText("R"))
        // The playlist's plot stays.
        compose.waitForText("A night-shift harbour pilot")
        compose.scrollTo("Ivo Penn")
        compose.scrollTo("Mara Quell")
        // The film's collection and those like it, as far as the playlist has them.
        compose.scrollTo("Neon Harbour Collection")
        compose.scrollTo("Neon Harbour 2")
        compose.scrollTo("More like this")
        assertTrue(compose.onAllNodes(hasText("Not In The Playlist")).fetchSemanticsNodes().isEmpty())
        compose.onAllNodes(hasText("Second Feature")).onFirst().performClick()
        assertEquals(listOf("m2"), opened.map { it.streamId })
        compose.scrollTo("Details from TMDB")
    }

    @Test
    fun aSeriesGainsWhoMadeItWhatsNextAndEpisodeDetails() {
        show(ContentKind.SERIES, "s1")
        compose.waitForText("Returning series")
        compose.scrollTo("Mo Brennan")
        compose.scrollTo("Channel Nine")
        compose.scrollTo("S3 E1 · High Water")
        compose.scrollTo("Harbourmaster")
        compose.scrollTo("The town's first winter.")
        // Season 1's episodes: plots and air dates the playlist doesn't have.
        compose.scrollTo("The tide goes out further than anyone remembers.")
        compose.scrollTo("May 8, 2023")
        // Season 2 has no overview on TMDB.
        compose.onNode(page).performScrollToNode(hasText("Season 2") and isSelectable())
        compose.onNode(hasText("Season 2") and isSelectable()).performClick()
        compose.waitForText("Undertow")
        assertTrue(compose.onAllNodes(hasText("The town's first winter.")).fetchSemanticsNodes().isEmpty())
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvTmdbDetailTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val artwork = FakeArtworkServer()
    private val tmdb = FakeTmdb()

    @After
    fun stop() {
        tmdb.close()
        artwork.close()
        SingletonImageLoader.reset()
    }

    @Test
    fun aFilmGainsTmdbsDetails() {
        useTestImageLoader(app)
        val pid = tmdb.seed(artwork)
        compose.setContent {
            TellycineTvTheme {
                Surface(colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background)) {
                    TvDetailScreen(ContentKind.MOVIE, pid, "m1")
                }
            }
        }
        compose.waitForText("Some ships don't want to be found.")
        compose.waitForText("Details from TMDB")
        compose.waitForText("Ivo Penn")
        compose.waitForText("Ren Okafor")
    }
}
