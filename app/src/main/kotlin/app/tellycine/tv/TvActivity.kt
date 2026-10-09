package app.tellycine.tv

import android.os.Bundle
import android.view.KeyEvent
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

    /** Set while a held Back has opened the mini player's channel, so its release does nothing. */
    private var backHeld = false

    /**
     * Holding Back returns the channel in the guide's mini player to full screen. Caught before
     * the screen sees the key, so a held Back doesn't also go back.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            // A fresh press; the release of a held one may have gone to the player instead.
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) backHeld = false
            if (backHeld) return true
            val mini = container.tvPlayback.mini
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0 && mini != null) {
                backHeld = true
                startActivity(mini.request.toIntent(this))
                return true
            }
        }
        return super.dispatchKeyEvent(event)
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
