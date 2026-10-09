package app.tellycine.player

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.core.ContentKind
import app.tellycine.data.db.StreamEntity
import app.tellycine.screenshots.ScreenshotDriver
import app.tellycine.tv.TellycineTvTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The TV player's remote keys: arrows change channel, but move between controls once they're open. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvPlayerNavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
    private val zaps = mutableListOf<Int>()
    private val selected = mutableListOf<String>()

    @After
    fun tearDown() = player.release()

    private fun launch(): ScreenshotDriver {
        fun channel(i: Int, name: String) = StreamEntity(
            playlistId = 1, kind = ContentKind.LIVE, streamId = "ch$i", name = name, sortOrder = i, categoryId = null,
            logo = null, url = "https://example.com/$i.m3u8", number = 101 + i,
        )
        val channels = listOf(channel(0, "Metro News 24"), channel(1, "Global Report"), channel(2, "City Sports 1"))
        compose.setContent {
            TellycineTvTheme {
                TvPlayerScreen(
                    player = player,
                    request = PlayRequest(1, ContentKind.LIVE, "ch0", "LIVE:ch0", channels[0].url, "Metro News 24"),
                    channels = channels,
                    live = LiveInfo(channel = channels[0], previous = channels[2]),
                    error = null, inPip = false, resizeMode = 0, pipSupported = false,
                    onBack = {}, onRetry = {}, onZap = { zaps += it }, onSelectChannel = { selected += it.streamId },
                    onChannelNumber = {}, onPreviousChannel = {}, onToggleFavorite = {}, onOpenWith = {}, onCycleResize = {}, onEnterPip = {},
                    onPlay = {}, stats = { emptyList() },
                )
            }
        }
        return ScreenshotDriver(compose).also { it.waitForText("Metro News 24") }
    }

    @Test
    fun arrowsChangeChannelWhenNothingIsOpen() {
        val d = launch()
        d.press(Key.DirectionUp)
        d.press(Key.DirectionDown)
        assertEquals(listOf(1, -1), zaps)
    }

    @Test
    fun arrowsMoveBetweenControlsOnceTheyAreOpen() {
        val d = launch()
        d.press(Key.DirectionCenter)
        d.waitForText("Audio & subtitles")
        compose.onNode(hasText("Channels") and hasClickAction()).assertIsFocused()

        d.press(Key.DirectionRight)
        compose.onNode(hasText("City Sports 1") and hasClickAction()).assertIsFocused()
        d.press(Key.DirectionDown)
        d.press(Key.DirectionUp)
        assertEquals(emptyList<Int>(), zaps)
    }

    @Test
    fun theChannelListCanBeScrolledAndChosenFrom() {
        val d = launch()
        d.press(Key.DirectionLeft)
        d.waitForText("Channels")
        d.press(Key.DirectionDown)
        d.press(Key.DirectionCenter)
        assertEquals(emptyList<Int>(), zaps)
        assertEquals(listOf("ch1"), selected)
    }
}
