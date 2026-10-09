package app.tellycine.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.R
import app.tellycine.data.settings.TvTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * The Google Play listing's screenshots and graphics, at the sizes Play asks for, saved to
 * fastlane/metadata/android/en-GB/images. Rendered from the real UI and the fictional demo playlist,
 * so the listing never shows real channels or programmes.
 * Run: ./gradlew :app:testDebugUnitTest --tests '*Store*'
 */

/** Phone screenshots: 1080 × 1920 (9:16). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-port-xxhdpi")
class StorePhoneScreenshots {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun capture() {
        val d = ScreenshotDriver(compose)
        d.launch()

        d.openTab("Live TV", "Metro News 24")
        d.storeShot("phoneScreenshots/1_live-tv")

        compose.onAllNodesWithText("City Sports 1", substring = true).onFirst().performTouchInput { longClick() }
        d.waitForText("Watch live")
        d.storeShot("phoneScreenshots/2_guide")
        d.back()

        d.openTab("Movies", "Midnight Harbor")
        d.storeShot("phoneScreenshots/3_movies")
        d.click("The Last Signal")
        d.waitForText("Resume")
        d.storeShot("phoneScreenshots/4_movie")
        d.back()

        d.openTab("Series", "The Long Night")
        d.click("The Long Night")
        d.waitForText("Season 2")
        d.storeShot("phoneScreenshots/5_series")
        d.back()

        d.openTab("Library", "Continue watching")
        d.storeShot("phoneScreenshots/6_library")

        d.openTab("Playlists", "Demo IPTV")
        d.click("Add playlist")
        d.waitForText("Playlist URL")
        d.storeShot("phoneScreenshots/7_add-playlist")
    }
}

/** 10-inch tablet screenshots: 2560 × 1440 (16:9), in landscape. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h720dp-land-xhdpi")
class StoreTabletScreenshots {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun capture() {
        val d = ScreenshotDriver(compose)
        d.launch()

        d.openTab("Live TV", "Metro News 24")
        compose.onNodeWithContentDescription("TV guide view").performClick()
        // The choice is saved first; the toggle flips once the grid is showing.
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("List view").fetchSemanticsNodes().isNotEmpty() }
        d.waitForText("World Tonight")
        d.storeShot("tenInchScreenshots/1_tv-guide")

        d.openTab("Movies", "Midnight Harbor")
        d.storeShot("tenInchScreenshots/2_movies")

        compose.onNodeWithContentDescription("Search").performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("night")
        d.waitForText("Tonight")
        d.storeShot("tenInchScreenshots/3_search")
        d.back()

        d.openTab("Library", "Continue watching")
        d.storeShot("tenInchScreenshots/4_library")
    }
}

/** Android TV screenshots: 1920 × 1080. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class StoreTvScreenshots {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun capture() {
        val d = ScreenshotDriver(compose)
        d.launchTv()

        d.press(Key.DirectionDown, 2)
        d.press(Key.DirectionRight)
        d.storeShot("tvScreenshots/1_guide")

        d.press(Key.Menu)
        d.waitForText("Watch live")
        d.storeShot("tvScreenshots/2_channel")
        d.back()

        d.tvTab("Movies", "Midnight Harbor")
        d.storeShot("tvScreenshots/3_movies")
        d.tvClick("The Last Signal")
        d.waitForText("Resume")
        d.storeShot("tvScreenshots/4_movie")
        d.back()

        d.tvTab("Series", "The Long Night")
        d.tvClick("The Long Night")
        d.waitForText("Season 2")
        d.storeShot("tvScreenshots/5_series")
        d.back()

        d.tvTab("Library", "Continue watching")
        d.storeShot("tvScreenshots/6_library")

        // One of the other colour themes.
        runBlocking(Dispatchers.IO) { d.app.container.settings.setTvTheme(TvTheme.MIDNIGHT) }
        d.tvTab("Live TV", "Metro News 24")
        d.storeShot("tvScreenshots/7_guide-midnight")
    }
}

/** The listing's graphics: the feature graphic, the Android TV banner and the app icon. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreGraphics {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** 1024 × 500, shown at the top of the listing. */
    @Test
    @Config(qualifiers = "w512dp-h250dp-land-xhdpi")
    fun featureGraphic() = render("featureGraphic") {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Logo(mark = 88.dp)
            Text(
                "Your IPTV playlists on phone, tablet and TV",
                color = Color(0xFFFFB951),
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }

    /** 1280 × 720, shown in the Google TV and Android TV store. */
    @Test
    @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun tvBanner() = render("tvBanner") { Logo(mark = 128.dp) }

    /** 512 × 512, the launcher icon as the home screen shows it. Play rounds the corners itself. */
    @Test
    @Config(qualifiers = "w256dp-h256dp-xhdpi")
    fun icon() {
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF2C2C2A))) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
            }
        }
        ScreenshotDriver(compose).storeShot("icon")
    }

    private fun render(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0xFF2C241C), Color(0xFF110D09)), start = Offset.Zero, end = Offset.Infinite)),
                contentAlignment = Alignment.Center,
            ) { content() }
        }
        ScreenshotDriver(compose).storeShot(name)
    }

    /** The mark and the wordmark side by side, as on the TV banner. */
    @Composable
    private fun Logo(mark: Dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.tellycine_mark), null, Modifier.size(mark))
            // The wordmark's viewport is 84.3 × 24.
            val height = mark * 0.62f
            Image(
                painterResource(R.drawable.tellycine_wordmark),
                null,
                Modifier.size(width = height * (84.3f / 24f), height = height),
                colorFilter = ColorFilter.tint(Color(0xFFEBE1D9)),
            )
        }
    }
}
