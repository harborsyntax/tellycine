package app.tellycine.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import okhttp3.OkHttpClient

/**
 * On a TV, the player outlives the full-screen player screen, so a live channel left with Back
 * keeps playing in the guide's mini player and goes back to full screen without restarting.
 *
 * One screen owns the player at a time: a full-screen [PlayerActivity], or the mini player
 * ([mini] is set). Whoever owns it last releases it, so a player screen that is closing can't
 * stop a channel that another one, or the mini player, has since taken over.
 */
@OptIn(UnstableApi::class)
class TvPlayback(context: Context, httpClient: OkHttpClient) {
    val factory = PlayerFactory(context.applicationContext, httpClient)

    private var player: ExoPlayer? = null
    private var owner: Any? = null

    /** The live channel playing in the mini player, or null when it isn't showing. */
    var mini by mutableStateOf<MiniPlayback?>(null)
        private set

    /** The player, for [newOwner] to show full screen; the mini player gives it up. */
    fun claim(newOwner: Any): ExoPlayer {
        mini?.let { it.player.removeListener(miniListener) }
        mini = null
        owner = newOwner
        return player ?: factory.create().also { player = it }
    }

    /** [from] hands its channel to the mini player, which keeps it playing. */
    fun toMini(from: Any, request: PlayRequest) {
        val p = player ?: return
        if (owner !== from) return
        owner = null
        p.addListener(miniListener)
        mini = MiniPlayback(p, request)
    }

    /** Stops playback if [from] still owns it. */
    fun release(from: Any) {
        if (owner === from) stopPlayer()
    }

    /** Stops the mini player's channel, if it has one (leaving the guide, or the app). */
    fun stopMini() {
        if (mini != null) stopPlayer()
    }

    private fun stopPlayer() {
        mini?.let { it.player.removeListener(miniListener) }
        mini = null
        owner = null
        player?.release()
        player = null
    }

    // The mini player doesn't retry a dropped stream: it closes, and the channel can be opened again.
    private val miniListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) = stopMini()
    }
}

data class MiniPlayback(val player: Player, val request: PlayRequest)
