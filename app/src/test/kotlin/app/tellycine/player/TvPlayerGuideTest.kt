package app.tellycine.player

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.core.ContentKind
import app.tellycine.data.repo.SecondPlaylist
import app.tellycine.data.settings.AppSettings
import app.tellycine.data.settings.CategorySort
import app.tellycine.screenshots.DemoData
import app.tellycine.screenshots.ScreenshotDriver
import app.tellycine.tv.TellycineTvTheme
import app.tellycine.ui.live.liveRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The guide over the TV player: Live TV's playlists and order, as in the main guide. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvPlayerGuideTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
    private val played = mutableListOf<PlayRequest>()
    private val selected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)

    @After
    fun tearDown() = player.release()

    @Test
    fun choosesPlaylistsAndSortsLikeTheMainGuide() {
        val d = ScreenshotDriver(compose)
        DemoData.seed(d.app.container)
        val second = SecondPlaylist.seed(d.app.container)
        val channel = runBlocking(Dispatchers.IO) { d.app.container.content.getStream(second, ContentKind.LIVE, "ch1")!! }
        // Playing the second playlist's football channel while Live TV shows the demo playlist.
        val request = liveRequest(null, channel, subtitle = null, categoryId = "8")
        compose.setContent {
            TellycineTvTheme {
                TvPlayerScreen(
                    player = player, request = request, channels = listOf(channel), live = LiveInfo(channel = channel),
                    error = null, inPip = false, resizeMode = 0, pipSupported = false,
                    onBack = {}, onRetry = {}, onZap = {}, onSelectChannel = {},
                    onChannelNumber = {}, onPreviousChannel = {}, onToggleFavorite = {}, onOpenWith = {}, onCycleResize = {}, onEnterPip = {},
                    onPlay = { played += it }, stats = { emptyList() },
                )
            }
        }
        d.waitForText("Second Football")
        d.press(Key.DirectionCenter)
        d.waitForText("Audio & subtitles")
        compose.onNode(hasText("TV guide") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        // It opens in the channel's own playlist and category; the playlists are above them.
        pressUntilFocused(Key.DirectionLeft, hasText("Football"))
        compose.onNode(hasText(SecondPlaylist.NAME) and selected).assertExists()

        // The Sort button switches the categories to A–Z: Football before news.
        d.press(Key.DirectionUp, 4)
        compose.onNode(hasContentDescription("Sort: Provider order") and isFocused()).assertExists()
        d.press(Key.DirectionCenter)
        // Once the choice is saved.
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Sort: A–Z") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        d.press(Key.DirectionDown, 3)
        compose.onNode(hasText("Football") and isFocused()).assertExists()

        // All playlists: the categories of both, merged by name, still on the channel playing.
        d.press(Key.DirectionUp, 6)
        compose.onNode(hasText("All playlists") and isFocused()).assertExists()
        d.press(Key.DirectionCenter)
        pressUntilFocused(Key.DirectionLeft, hasText("Football"))
        // A–Z: Documentaries, Entertainment, Football, Kids, Music, News, Sport.
        compose.onNode(hasText("Documentaries")).assertExists()
        d.press(Key.DirectionDown, 3)
        compose.onNode(hasText("News") and isFocused()).assertExists()
        d.press(Key.DirectionCenter)
        d.waitForText("Metro News 24")

        // The second playlist's news channel, in the merged News: the header says where it's from.
        assertTrue(inHeader("Demo IPTV"))
        d.press(Key.DirectionDown, 2)
        d.waitForText("Second Headlines")
        compose.waitUntil(5_000) { inHeader(SecondPlaylist.NAME) }
        d.press(Key.DirectionCenter)
        compose.waitUntil(5_000) { played.isNotEmpty() }
        assertEquals(second, played.last().playlistId)
        assertEquals("ch0", played.last().streamId)
        assertEquals("7", played.last().categoryId)

        // Both choices are Live TV's, so the main guide shows the same.
        val settings = runBlocking { d.app.container.settings.settings.first() }
        assertEquals(AppSettings.ALL_PLAYLISTS, settings.sectionPlaylists[ContentKind.LIVE])
        assertEquals(CategorySort.NAME, settings.liveCategorySort)
    }

    /** Whether [text] is in the header line, above the column of playlists and categories. */
    private fun inHeader(text: String): Boolean {
        val columnTop = compose.onNodeWithText("Playlists").fetchSemanticsNode().boundsInRoot.top
        return compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().any { it.boundsInRoot.bottom <= columnTop }
    }

    /** Presses [key] until the node matching [matcher] has the focus. */
    private fun pressUntilFocused(key: Key, matcher: SemanticsMatcher, limit: Int = 10) {
        val d = ScreenshotDriver(compose)
        repeat(limit) {
            if (compose.onAllNodes(matcher and isFocused()).fetchSemanticsNodes().isNotEmpty()) return
            d.press(key)
        }
        compose.onNode(matcher and isFocused()).assertExists()
    }
}
