package app.tellycine

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.OrientationEventListener
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.tellycine.data.settings.AppSettings
import app.tellycine.player.PhonePlayback
import app.tellycine.player.PhonePlayerHost
import app.tellycine.player.pipParams
import app.tellycine.tv.TvActivity
import app.tellycine.tv.isTelevision
import app.tellycine.ui.TellycineNavHost
import app.tellycine.ui.theme.TellycineTheme
import kotlinx.coroutines.launch

/**
 * The phone and tablet app, with its player ([PhonePlayback]) docked at the top in portrait, full
 * screen in landscape, and in picture in picture when you leave while something's playing.
 */
class MainActivity : FragmentActivity() {
    private val playback: PhonePlayback get() = container.phonePlayback
    private var phone = false
    private var inPip by mutableStateOf(false)

    /** After full screen is asked for or left: the way the phone has to be turned before it decides again. */
    private var waitingFor: Int? = null
    private val orientationListener by lazy { TurnListener() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Some TV launchers list phone apps too; on a TV, always use the TV interface.
        if (isTelevision()) {
            startActivity(Intent(this, TvActivity::class.java))
            finish()
            return
        }
        phone = true
        val settingsFlow = container.settings.settings
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
            TellycineTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                PhonePlayerHost(
                    playback = playback,
                    inPip = inPip,
                    pipSupported = pipSupported(),
                    onFullScreen = ::setFullScreen,
                    onEnterPip = ::enterPip,
                ) {
                    TellycineNavHost()
                }
            }
        }
        lifecycleScope.launch {
            // Ready to go into picture in picture by itself (Android 12+) only while something plays.
            snapshotFlow { Triple(playback.isPlaying, playback.videoSize, playback.engine?.isCasting()) }.collect { updatePipParams() }
        }
        lifecycleScope.launch {
            // Stopped: the screen turns freely again.
            snapshotFlow { playback.engine == null }.collect { stopped -> if (stopped) letTurn() }
        }
    }

    // ---- Full screen ----

    /**
     * Full screen turns the screen sideways, and leaving it turns it upright. With auto-rotate on,
     * once the phone is turned that way too, the screen follows the phone again, so turning it back
     * leaves (or goes back to) full screen.
     */
    private fun setFullScreen(on: Boolean) {
        requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if (autoRotate()) {
            waitingFor = if (on) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            orientationListener.enable()
        }
    }

    private fun letTurn() {
        waitingFor = null
        orientationListener.disable()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun autoRotate() = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    private inner class TurnListener : OrientationEventListener(this) {
        override fun onOrientationChanged(degrees: Int) {
            val want = waitingFor ?: return
            if (degrees == ORIENTATION_UNKNOWN) return
            val upright = degrees < 30 || degrees > 330 || degrees in 150..210
            val sideways = degrees in 60..120 || degrees in 240..300
            if ((want == Configuration.ORIENTATION_PORTRAIT && upright) || (want == Configuration.ORIENTATION_LANDSCAPE && sideways)) {
                letTurn()
            }
        }
    }

    // ---- Picture in picture ----

    private fun pipSupported() = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun canPip() = pipSupported() && playback.engine?.isCasting() == false

    private fun updatePipParams() {
        if (!pipSupported()) return
        runCatching { setPictureInPictureParams(pipParams(playback.videoSize, autoEnter = playback.isPlaying && canPip())) }
    }

    private fun enterPip() {
        if (!canPip()) return
        runCatching { enterPictureInPictureMode(pipParams(playback.videoSize, autoEnter = playback.isPlaying)) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // From Android 12, the system does this itself (see updatePipParams).
        if (phone && Build.VERSION.SDK_INT < Build.VERSION_CODES.S && playback.isPlaying) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        // The picture-in-picture window was closed rather than opened back up.
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) playback.close()
    }

    override fun onStop() {
        super.onStop()
        if (!phone || isInPictureInPictureMode || isChangingConfigurations) return
        // Out of sight: keep the resume point and pause, unless it's on a Chromecast.
        playback.engine?.saveProgress()
        playback.pause()
    }

    override fun onDestroy() {
        if (phone && isFinishing) {
            playback.close()
            orientationListener.disable()
        }
        super.onDestroy()
    }
}
