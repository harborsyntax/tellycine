package app.tellycine.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Documentation screenshots of the Sports Hub, on a phone and on TV, with the demo's sport. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SportsScreenshots {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun phone() {
        val d = ScreenshotDriver(compose)
        d.launch { pid -> DemoData.seedSports(d.app.container, pid) }

        d.openTab("Sports", "Live now")
        d.waitForText("Your teams")
        d.shot("sports")

        compose.onAllNodesWithText("Inter", substring = true).onFirst().performClick()
        d.waitForText("Watch on")
        d.shot("sports-event")
        d.back()

        compose.onNodeWithContentDescription("Your sports and teams").performClick()
        d.waitForText("Teams and players")
        d.shot("sports-setup")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun tv() {
        val d = ScreenshotDriver(compose)
        d.launchTv { pid -> DemoData.seedSports(d.app.container, pid) }

        d.tvTab("Sports", "Live now")
        d.waitForText("Your teams")
        d.shot("tv-sports")

        // Down to what's coming up today; OK opens its panel.
        d.press(Key.DirectionDown, 2)
        d.press(Key.DirectionCenter)
        d.waitForText("Watch on")
        d.shot("tv-sports-event")
    }
}
