package app.tellycine.player.multiview

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.tellycine.container
import app.tellycine.player.PlayRequest
import app.tellycine.player.PlayerFactory
import app.tellycine.tv.TellycineTvTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One channel in multi-view, with its own player and sound. */
@OptIn(UnstableApi::class)
class MultiViewTile(val key: Int, request: PlayRequest, val factory: PlayerFactory, val player: ExoPlayer) {
    var request by mutableStateOf(request)
    var soundOn by mutableStateOf(false)
    var volume by mutableFloatStateOf(1f)
    /** Why the channel stopped, after a few retries. */
    var error by mutableStateOf<String?>(null)
    internal var retries = 0

    internal fun applyVolume() {
        player.volume = if (soundOn) volume else 0f
    }
}

/**
 * Up to four live channels at once on a TV, each with its own sound on or off and volume. Opens
 * with one channel and the guide over the screen to pick the next.
 */
@OptIn(UnstableApi::class)
class MultiViewActivity : ComponentActivity() {

    private val tiles = mutableStateListOf<MultiViewTile>()
    private var nextKey = 0
    /** Connection limits by playlist, loaded as channels from each are added. */
    private val limits = mutableMapOf<Long, Int?>()
    private var activePlaylistId by mutableStateOf<Long?>(null)
    /** The guide over the screen, to add a channel (null target) or change one. */
    private var picker by mutableStateOf<PickerTarget?>(null)

    class PickerTarget(val replacing: MultiViewTile?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())

        lifecycleScope.launch {
            container.playlists.activePlaylist.collect { playlist ->
                if (playlist != null) limits[playlist.id] = playlist.effectiveMaxConnections
                activePlaylistId = playlist?.id
            }
        }

        setContent {
            TellycineTvTheme {
                MultiViewScreen(
                    tiles = tiles,
                    spare = activePlaylistId?.let { MultiViewLimits.spare(tiles.map { t -> t.request.playlistId }, it, limits[it]) } ?: 0,
                    picker = picker,
                    actions = actions,
                )
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** A channel to add, from the player or a channel's options; then the guide to add another. */
    private fun handleIntent(intent: Intent) {
        val req = PlayRequest.fromIntent(intent)
        if (req == null) {
            if (tiles.isEmpty()) finish()
            return
        }
        lifecycleScope.launch {
            if (add(req, replacing = null)) {
                val active = container.playlists.activePlaylist.first()?.id ?: req.playlistId
                if (MultiViewLimits.spare(tiles.map { it.request.playlistId }, active, limitOf(active)) > 0) {
                    picker = PickerTarget(replacing = null)
                }
            } else if (tiles.isEmpty()) {
                finish()
            }
        }
    }

    private suspend fun limitOf(playlistId: Long): Int? {
        if (!limits.containsKey(playlistId)) limits[playlistId] = container.playlists.get(playlistId)?.effectiveMaxConnections
        return limits[playlistId]
    }

    /** Adds [req], or puts it in [replacing]'s place; false (with a message) if it can't be played now. */
    private suspend fun add(req: PlayRequest, replacing: MultiViewTile?): Boolean {
        limitOf(req.playlistId)
        val index = replacing?.let { tiles.indexOf(it) }?.takeIf { it >= 0 }
        MultiViewLimits.refusal(tiles.map { it.request.playlistId }, req.playlistId, index, limits::get)?.let {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            return false
        }
        if (replacing != null && index != null) {
            replacing.request = req
            start(replacing)
        } else {
            val factory = PlayerFactory(this, container.httpClient)
            val tile = MultiViewTile(nextKey++, req, factory, factory.create(multiView = true))
            // The first channel is heard; the ones added beside it start muted.
            tile.soundOn = tiles.none { it.soundOn }
            tile.player.addListener(retrier(tile))
            tiles += tile
            start(tile)
        }
        return true
    }

    private fun start(tile: MultiViewTile) {
        tile.error = null
        tile.retries = 0
        tile.factory.applyHeaders(tile.request)
        tile.player.setMediaItem(tile.factory.mediaItem(tile.request))
        tile.player.prepare()
        tile.player.playWhenReady = true
        tile.applyVolume()
    }

    /** Live streams drop now and then: retried a few times before the tile says it stopped. */
    private fun retrier(tile: MultiViewTile) = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                tile.player.seekToDefaultPosition()
                tile.player.prepare()
                return
            }
            if (tile.retries < 3) {
                tile.retries++
                val request = tile.request
                lifecycleScope.launch {
                    delay(1500L * tile.retries)
                    if (tile in tiles && tile.request == request) tile.player.prepare()
                }
                return
            }
            tile.error = "This channel stopped playing."
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                tile.retries = 0
                tile.error = null
            }
        }
    }

    private fun remove(tile: MultiViewTile) {
        tile.player.release()
        tiles.remove(tile)
        if (tiles.isEmpty()) return finish()
        // Something should still be heard if the channel with the sound went.
        if (tile.soundOn && tiles.none { it.soundOn }) tiles.first().apply { soundOn = true; applyVolume() }
    }

    private val actions = object : MultiViewActions {
        override fun add() {
            picker = PickerTarget(replacing = null)
        }

        override fun change(tile: MultiViewTile) {
            picker = PickerTarget(replacing = tile)
        }

        override fun pick(request: PlayRequest) {
            val target = picker ?: return
            lifecycleScope.launch {
                if (add(request, target.replacing)) picker = null
            }
        }

        override fun closePicker() {
            picker = null
            if (tiles.isEmpty()) finish()
        }

        override fun toggleSound(tile: MultiViewTile) {
            tile.soundOn = !tile.soundOn
            tile.applyVolume()
        }

        override fun soloSound(tile: MultiViewTile) {
            tiles.forEach {
                it.soundOn = it === tile
                it.applyVolume()
            }
        }

        override fun changeVolume(tile: MultiViewTile, delta: Float) {
            tile.volume = (tile.volume + delta).coerceIn(0.1f, 1f)
            tile.soundOn = true
            tile.applyVolume()
        }

        override fun retry(tile: MultiViewTile) = start(tile)

        override fun remove(tile: MultiViewTile) = this@MultiViewActivity.remove(tile)

        override fun fullScreen(tile: MultiViewTile) {
            val request = tile.request
            // Every connection is let go before the player opens its own.
            releaseAll()
            startActivity(request.toIntent(this@MultiViewActivity))
            finish()
        }

        override fun exit() = finish()
    }

    private fun releaseAll() {
        tiles.forEach { it.player.release() }
        tiles.clear()
    }

    override fun onStop() {
        super.onStop()
        // Multi-view ends when it leaves the screen, rather than playing on unseen.
        if (!isChangingConfigurations) finish()
    }

    override fun onDestroy() {
        releaseAll()
        super.onDestroy()
    }

    companion object {
        /** Opens multi-view with [request]'s channel, or adds it to the multi-view already open. */
        fun intent(context: Context, request: PlayRequest): Intent =
            request.putInto(Intent(context, MultiViewActivity::class.java))
    }
}

/** What the multi-view screen can ask for. */
interface MultiViewActions {
    fun add()
    fun change(tile: MultiViewTile)
    fun pick(request: PlayRequest)
    fun closePicker()
    fun toggleSound(tile: MultiViewTile)
    fun soloSound(tile: MultiViewTile)
    fun changeVolume(tile: MultiViewTile, delta: Float)
    fun retry(tile: MultiViewTile)
    fun remove(tile: MultiViewTile)
    fun fullScreen(tile: MultiViewTile)
    fun exit()
}
