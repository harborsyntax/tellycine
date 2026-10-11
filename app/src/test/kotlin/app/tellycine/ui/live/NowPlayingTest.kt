package app.tellycine.ui.live

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellycine.core.ContentKind
import app.tellycine.data.db.ChannelRow
import app.tellycine.data.db.NowPlaying
import app.tellycine.data.db.StreamEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NowPlayingTest {
    @get:Rule
    val compose = createComposeRule()

    private val start = 1_000_000_000L
    private val minute = 60_000L

    private fun row(title: String?, from: Long?, to: Long?, guide: String? = "e1") = ChannelRow(
        StreamEntity(1, ContentKind.LIVE, "ch1", "Channel One", 0, null, null, "https://example.com/1.ts", epgChannelId = guide),
        title, from, to, isFavorite = false,
    )

    @Test
    fun aRowKeepsItsProgrammeUntilItEndsThenLooksUpTheNext() {
        var now by mutableLongStateOf(start)
        val lookups = mutableListOf<Long>()
        val loaded = row("Morning News", start - 10 * minute, start + 20 * minute)
        compose.setContent {
            val playing = rememberNowPlaying(loaded, now) {
                lookups += now
                NowPlaying("ch1", "Weather", start + 20 * minute, start + 30 * minute)
            }
            Text(playing?.nowTitle ?: "Nothing on")
        }
        compose.onNodeWithText("Morning News").assertExists()

        // Minutes pass with no lookups while it's still on.
        now = start + 19 * minute
        compose.waitForIdle()
        assertEquals(0, lookups.size)

        // Once it ends, the next programme is looked up, once.
        now = start + 20 * minute
        compose.waitForIdle()
        compose.onNodeWithText("Weather").assertExists()
        now = start + 21 * minute
        compose.waitForIdle()
        assertEquals(1, lookups.size)
    }

    @Test
    fun aChannelWithoutAGuideLooksNothingUp() {
        var now by mutableLongStateOf(start)
        var lookups = 0
        compose.setContent {
            val playing = rememberNowPlaying(row(null, null, null, guide = null), now) { lookups++; null }
            Text(playing?.nowTitle ?: "Nothing on")
        }
        now = start + 5 * minute
        compose.waitForIdle()
        compose.onNodeWithText("Nothing on").assertExists()
        assertEquals(0, lookups)
    }

    @Test
    fun anEndedProgrammeIsNeverShown() {
        val now = start
        compose.setContent {
            // Loaded a while ago, and nothing on now.
            val playing = rememberNowPlaying(row("Old Film", start - 120 * minute, start - minute), now) { null }
            Text(playing?.nowTitle ?: "Nothing on")
        }
        compose.onNodeWithText("Nothing on").assertExists()
    }
}
