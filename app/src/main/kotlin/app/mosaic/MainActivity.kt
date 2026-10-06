package app.mosaic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mosaic.data.settings.AppSettings
import app.mosaic.ui.MosaicNavHost
import app.mosaic.ui.theme.MosaicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settingsFlow = container.settings.settings
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
            MosaicTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                MosaicNavHost()
            }
        }
    }
}
