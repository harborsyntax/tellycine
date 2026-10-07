package app.mosaic.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Documentation screenshots of the tablet (list-detail) layouts. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.MediumTablet)
class TabletScreenshots {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun captureAll() {
        val d = ScreenshotDriver(compose)
        d.launch()

        d.openTab("Live TV", "Mosaic News 24")
        d.shot("tablet-live-tv")

        d.openTab("Movies", "Midnight Harbor")
        d.shot("tablet-movies")
    }
}
