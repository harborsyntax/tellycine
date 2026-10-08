package app.tellycine.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.core.ContentKind
import app.tellycine.data.db.ProgrammeEntity
import app.tellycine.data.db.StreamEntity
import app.tellycine.player.LiveInfo
import app.tellycine.player.PlayRequest
import app.tellycine.player.TvPlayerScreen
import app.tellycine.tv.TellycineTvTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of the TV player's banner and controls (over a black picture). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvPlayerScreenshots {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun capture() {
        val d = ScreenshotDriver(compose)
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        val now = System.currentTimeMillis()
        fun channel(i: Int, name: String) = StreamEntity(
            playlistId = 1, kind = ContentKind.LIVE, streamId = "ch$i", name = name, sortOrder = i, categoryId = "news",
            logo = null, url = "https://example.com/live/$i.m3u8", number = 101 + i,
        )
        val channels = listOf(channel(0, "Metro News 24"), channel(1, "Global Report"), channel(2, "City Sports 1"), channel(3, "City Sports 2"))
        val live = LiveInfo(
            channel = channels[0],
            now = ProgrammeEntity(playlistId = 1, channelId = "ch0", startMs = now - 20 * 60_000, stopMs = now + 10 * 60_000, title = "World Tonight", description = null, category = null),
            next = ProgrammeEntity(playlistId = 1, channelId = "ch0", startMs = now + 10 * 60_000, stopMs = now + 70 * 60_000, title = "The Morning Edition", description = null, category = null),
            favorite = true,
            previous = channels[2],
        )
        val request = PlayRequest(1, ContentKind.LIVE, "ch0", "LIVE:ch0", channels[0].url, "Metro News 24", subtitle = "World Tonight")
        compose.setContent {
            TellycineTvTheme {
                TvPlayerScreen(
                    player = player, request = request, channels = channels, live = live, error = null, inPip = false,
                    resizeMode = 0, pipSupported = true, onBack = {}, onRetry = {}, onZap = {}, onSelectChannel = {},
                    onChannelNumber = {}, onPreviousChannel = {}, onToggleFavorite = {}, onOpenWith = {}, onCycleResize = {}, onEnterPip = {},
                )
            }
        }
        d.waitForText("World Tonight")
        d.shot("tv-player-banner")

        d.press(Key.DirectionCenter)
        d.waitForText("Audio & subtitles")
        d.shot("tv-player-controls")
        player.release()
    }
}
