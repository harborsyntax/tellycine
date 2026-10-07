package app.mosaic.cast

import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import app.mosaic.container
import com.google.android.gms.cast.framework.CastButtonFactory

/**
 * The standard Cast button. It hides itself when no Cast devices are on the network,
 * and renders nothing at all when Cast isn't available on this device.
 *
 * @param onDark use light icons, for dark surfaces such as the player.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier, onDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
    val context = LocalContext.current
    if (context.container.cast.castContext == null) return
    AndroidView(
        factory = { ctx ->
            // MediaRouteButton needs an AppCompat theme for its icon and device picker.
            val themed = ContextThemeWrapper(
                ctx,
                if (onDark) androidx.appcompat.R.style.Theme_AppCompat_NoActionBar
                else androidx.appcompat.R.style.Theme_AppCompat_Light_NoActionBar,
            )
            MediaRouteButton(themed).also { CastButtonFactory.setUpMediaRouteButton(themed, it) }
        },
        modifier = modifier.size(48.dp),
    )
}
