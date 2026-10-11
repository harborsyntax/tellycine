package app.tellycine.ui.detail

import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import app.tellycine.TellycineApp
import app.tellycine.core.ContentKind
import app.tellycine.data.settings.ThemeMode
import app.tellycine.screenshots.ScreenshotDriver
import app.tellycine.tv.TellycineTvTheme
import app.tellycine.tv.detail.TvDetailScreen
import app.tellycine.ui.theme.TellycineTheme
import coil3.SingletonImageLoader
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val TRAILER = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

private val app: TellycineApp get() = ApplicationProvider.getApplicationContext()

private fun startedActivity(): Intent = shadowOf(app).nextStartedActivity

@OptIn(ExperimentalTestApi::class)
private fun ComposeRuleType.clickWhenShown(matcher: SemanticsMatcher) {
    waitUntilAtLeastOneExists(matcher and hasClickAction(), 15_000)
    onAllNodes(matcher and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    waitForIdle()
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeRuleType.waitForText(text: String) = waitUntilAtLeastOneExists(hasText(text, substring = true), 15_000)

private typealias ComposeRuleType = androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, ComponentActivity>

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class DetailArtworkTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val server = FakeArtworkServer()

    @After
    fun stop() {
        server.close()
        SingletonImageLoader.reset()
    }

    private fun show(kind: ContentKind, id: String) {
        useTestImageLoader(app)
        val pid = server.seed(app.container)
        compose.setContent {
            TellycineTheme(themeMode = ThemeMode.DARK, dynamicColor = false) { DetailScreen(kind, pid, id, onBack = {}) }
        }
    }

    @Test
    fun movieTrailerOpensInYouTube() {
        show(ContentKind.MOVIE, "m1")
        compose.clickWhenShown(hasContentDescription("Trailer"))
        val intent = startedActivity()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(TRAILER, intent.dataString)
        // On a phone, Android picks the app: YouTube handles its own links.
        assertNull(intent.`package`)
    }

    @Test
    fun seasonPostersChooseTheSeason() {
        show(ContentKind.SERIES, "s1")
        compose.waitForText("Low Tide")
        compose.clickWhenShown(hasText("Season 2") and isSelectable())
        compose.waitForText("Undertow")
        compose.onNode(hasText("Season 2") and isSelectable()).assertIsSelected()
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvDetailArtworkTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val server = FakeArtworkServer()

    @After
    fun stop() {
        server.close()
        SingletonImageLoader.reset()
    }

    private fun show(kind: ContentKind, id: String) {
        useTestImageLoader(app)
        val pid = server.seed(app.container)
        compose.setContent {
            TellycineTvTheme {
                // As the TV app's own screen sets it up.
                Surface(colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background)) { TvDetailScreen(kind, pid, id) }
            }
        }
    }

    @Test
    fun movieTrailerOpensInTheTvYouTubeApp() {
        show(ContentKind.MOVIE, "m1")
        compose.clickWhenShown(hasText("Trailer"))
        val intent = startedActivity()
        assertEquals(TRAILER, intent.dataString)
        assertEquals("com.google.android.youtube.tv", intent.`package`)
    }

    @Test
    fun seasonPostersChooseTheSeason() {
        show(ContentKind.SERIES, "s1")
        // The episodes are below the seasons, off the bottom of the screen.
        val page = hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        compose.waitForText("Season 2")
        compose.onNode(page).performScrollToNode(hasText("Low Tide", substring = true))
        compose.clickWhenShown(hasContentDescription("Season 2"))
        compose.onNode(page).performScrollToNode(hasText("Undertow", substring = true))
        compose.onNode(hasContentDescription("Season 2") and hasClickAction()).assertIsSelected()
    }

    @Test
    fun aSeriesPageKeepsItsTitleOnScreenOnATv() {
        // A TV (unlike these tests' usual device) scrolls lists to keep the focused item a third of
        // the way down, which could push the title off the top when Play was focused.
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        show(ContentKind.SERIES, "s1")
        compose.waitForText("Play S1 E1")
        compose.waitForIdle()
        assertTitleOnScreen()

        // Down to the episodes and back up to Play: the page returns to its top.
        val d = ScreenshotDriver(compose)
        val atTop = titleTop()
        d.press(Key.DirectionDown, 3)
        compose.onNode(hasText("Slack Water", substring = true) and hasClickAction()).assertIsFocused()
        assertTrue("title at ${titleTop()}, from $atTop", titleTop() < atTop)
        d.press(Key.DirectionUp, 3)
        compose.onNode(hasText("Play S1 E1") and hasClickAction()).assertIsFocused()
        assertTitleOnScreen()
    }

    /** Where the title is, or far above the screen once it has scrolled away (and left the list). */
    private fun titleTop() = compose.onAllNodes(hasText("Tidewater") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
        .fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top ?: Float.NEGATIVE_INFINITY

    private fun assertTitleOnScreen() = assertTrue("title at ${titleTop()}", titleTop() >= 0f)
}
