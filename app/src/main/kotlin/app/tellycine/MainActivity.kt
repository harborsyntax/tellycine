package app.tellycine

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tellycine.data.settings.AppSettings
import app.tellycine.ui.TellycineNavHost
import app.tellycine.ui.theme.TellycineTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settingsFlow = container.settings.settings
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
            TellycineTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                TellycineNavHost()
            }
        }
    }
}
