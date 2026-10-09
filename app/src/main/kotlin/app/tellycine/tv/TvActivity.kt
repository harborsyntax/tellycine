package app.tellycine.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import app.tellycine.container
import app.tellycine.core.ContentKind
import app.tellycine.player.play
import app.tellycine.ui.live.liveRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The TV interface, opened from the Android TV / Google TV launcher. */
class TvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TellycineTvTheme { TvApp() }
        }
        if (savedInstanceState == null) resumeLastChannel()
    }

    /** With "Open on the last channel" on, starts the channel watched most recently. */
    private fun resumeLastChannel() {
        val c = container
        lifecycleScope.launch {
            if (!c.settings.current().startOnLastChannel) return@launch
            val playlist = c.playlists.activePlaylist.first() ?: return@launch
            val last = c.content.recentChannels(playlist.id).first().firstOrNull() ?: return@launch
            val stream = c.content.getStream(playlist.id, ContentKind.LIVE, last.history.streamId) ?: return@launch
            play(liveRequest(playlist, stream))
        }
    }
}
