package app.tellycine

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import app.tellycine.data.settings.AppSettings
import app.tellycine.tv.TvActivity
import app.tellycine.tv.isTelevision
import app.tellycine.ui.TellycineNavHost
import app.tellycine.ui.theme.TellycineTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Some TV launchers list phone apps too; on a TV, always use the TV interface.
        if (isTelevision()) {
            startActivity(Intent(this, TvActivity::class.java))
            finish()
            return
        }
        val settingsFlow = container.settings.settings
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
            TellycineTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                TellycineNavHost()
            }
        }
    }
}
