package app.tellycine.tv.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.tellycine.container
import app.tellycine.data.remote.PhoneSetupServer
import app.tellycine.data.remote.addPlaylistFromPhone
import app.tellycine.tv.TvPalette
import app.tellycine.tv.components.QrCode
import app.tellycine.tv.components.RequestFocusOnStart
import app.tellycine.tv.components.TvActionButton
import app.tellycine.tv.components.TvEmptyState
import app.tellycine.tv.components.TvHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Add a playlist by typing it on a phone: the TV shows a QR code for a page it serves on the
 * local network, and whatever is submitted there is added straight away.
 */
@Composable
fun TvPhoneSetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf<String?>(null) }
    var started by remember { mutableStateOf(false) }
    var added by remember { mutableStateOf<List<String>>(emptyList()) }
    val done = remember { FocusRequester() }

    DisposableEffect(Unit) {
        val server = PhoneSetupServer { submission ->
            addPlaylistFromPhone(context, context.container, submission).onSuccess { name ->
                withContext(Dispatchers.Main) { added = added + name }
            }
        }
        // Opening a socket isn't allowed on the main thread.
        val job = scope.launch(Dispatchers.IO) {
            val url = runCatching { server.start(scope) }.getOrNull()
            withContext(Dispatchers.Main) {
                address = url
                started = true
            }
        }
        onDispose {
            job.cancel()
            server.stop()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TvHeader("Add a playlist from your phone")
        val url = address
        when {
            !started -> Unit
            url == null -> TvEmptyState(
                Icons.Filled.WifiOff,
                "Not connected to a network",
                "Connect this TV to Wi-Fi or Ethernet, on the same network as your phone, then try again.",
                actionLabel = "Back",
                onAction = onDone,
            )
            else -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    QrCode(url, size = 260.dp)
                    Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Step(1, "Connect your phone to the same Wi-Fi as this TV.")
                        Step(2, "Scan the code with your phone's camera, or open this address in its browser:")
                        Text(url, style = MaterialTheme.typography.titleMedium, color = TvPalette.primary)
                        Step(3, "Enter your playlist or login, or pick a playlist file, and tap Add to TV.")
                        added.forEach { name ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Filled.CheckCircle, null, tint = TvPalette.primary)
                                Text("Added $name. It's loading now.", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        Text(
                            "The page only works while this screen is open.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TvActionButton("Done", onClick = onDone, primary = true, modifier = Modifier.padding(top = 8.dp).focusRequester(done))
                    }
                }
            }
        }
    }
    RequestFocusOnStart(done, address)
}

@Composable
private fun Step(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Text("$number", style = MaterialTheme.typography.titleMedium, color = TvPalette.primary)
        }
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
