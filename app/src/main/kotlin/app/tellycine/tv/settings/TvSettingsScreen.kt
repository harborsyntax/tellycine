package app.tellycine.tv.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.tellycine.BuildConfig
import app.tellycine.R
import app.tellycine.data.settings.TvTheme
import app.tellycine.tv.components.TvChoiceDialog
import app.tellycine.tv.components.TvConfirmDialog
import app.tellycine.tv.components.TvHeader
import app.tellycine.tv.components.TvMessageDialog
import app.tellycine.tv.components.TvSectionLabel
import app.tellycine.tv.components.TvSpacing
import app.tellycine.tv.components.TvSwitchRow
import app.tellycine.tv.components.TvTextInputDialog
import app.tellycine.tv.components.TvValueRow
import app.tellycine.ui.components.TellycineLogo
import app.tellycine.ui.components.tellycineViewModel
import app.tellycine.ui.settings.SettingsViewModel
import app.tellycine.ui.settings.TmdbText

@Composable
fun TvSettingsScreen(onOpenDownloads: () -> Unit) {
    val vm = tellycineViewModel { c, _ -> SettingsViewModel(c) }
    val s by vm.settings.collectAsStateWithLifecycle()
    var playerDialog by remember { mutableStateOf(false) }
    var epgDialog by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    /** Typing a TMDB key: whether a saved key turns details from TMDB on; null when not typing one. */
    var keyInput by remember { mutableStateOf<Boolean?>(null) }
    val keyCheck by vm.keyCheck.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }

    Column(Modifier.fillMaxSize()) {
        TvHeader("Settings")
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 820.dp).fillMaxWidth(),
                contentPadding = PaddingValues(start = TvSpacing.screenStart, end = TvSpacing.screenEnd, bottom = TvSpacing.screenBottom),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item { TvSectionLabel("Appearance") }
                item {
                    TvValueRow(
                        "Theme: ${s.tvTheme.label}",
                        s.tvTheme.description,
                        onClick = { themeDialog = true },
                        modifier = Modifier.focusRequester(first),
                    )
                }

                item { TvSectionLabel("Playback") }
                item {
                    TvValueRow(
                        "Default player",
                        if (s.externalPlayer) "Another app: Android asks which one, and you can pick one to always use. Dispatcharr recordings still play here."
                        else "Built-in player",
                        onClick = { playerDialog = true },
                    )
                }
                item {
                    TvSwitchRow(
                        "Open on the last channel",
                        "Start playing the channel you watched last when Tellycine opens",
                        s.startOnLastChannel,
                        vm::setStartOnLastChannel,
                    )
                }
                item {
                    TvSwitchRow(
                        "Match the TV's refresh rate",
                        "Switch the display to suit each video, so 25 and 50 fps channels play smoothly. The screen may go blank for a moment when it changes.",
                        s.matchFrameRate,
                        vm::setMatchFrameRate,
                    )
                }

                item { TvSectionLabel("Playlists & guide") }
                item {
                    TvSwitchRow(
                        "Refresh on launch",
                        "Reload playlists older than a day and guides older than 12 hours",
                        s.refreshOnLaunch,
                        vm::setRefreshOnLaunch,
                    )
                }
                item {
                    TvValueRow(
                        "Guide days",
                        "Keep ${s.epgDays} day${if (s.epgDays == 1) "" else "s"} of programmes. Fewer days use less storage.",
                        onClick = { epgDialog = true },
                    )
                }
                item {
                    TvSwitchRow(
                        "Prefer HLS for Xtream live TV",
                        "Use .m3u8 instead of MPEG-TS. Applies after the next refresh.",
                        s.preferHls,
                        vm::setPreferHls,
                    )
                }

                item { TvSectionLabel("Movies & series") }
                item {
                    TvSwitchRow(TmdbText.TITLE, TmdbText.setting(s.hasBuiltInTmdbKey), s.tmdb, onChange = { on ->
                        // Turning it on needs a key first, in a build without one.
                        if (on && s.tmdbKey.isBlank()) keyInput = true else vm.setTmdbEnabled(on)
                    })
                }
                item { TvValueRow(TmdbText.KEY, TmdbText.keySummary(s.tmdbApiKey, s.hasBuiltInTmdbKey), onClick = { keyInput = false }) }

                item { TvSectionLabel("Downloads") }
                item {
                    TvSwitchRow(
                        "Download over Wi-Fi only",
                        "Wait for Wi-Fi or Ethernet (an unmetered connection). Applies to new downloads.",
                        s.downloadWifiOnly,
                        vm::setDownloadWifiOnly,
                    )
                }
                item {
                    TvValueRow("Manage downloads", "See, play and delete downloaded movies and episodes", onClick = onOpenDownloads)
                }

                item { TvSectionLabel("Privacy") }
                item {
                    TvValueRow("Clear watch history", "Removes resume points and recently watched channels", onClick = { clearDialog = true })
                }

                item {
                    // Focusable, so Down from the last setting brings the version and credits into view.
                    Column(
                        Modifier.fillMaxWidth().focusable().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TellycineLogo(height = 36.dp)
                        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "Tellycine is a player only and does not provide any content.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        // TMDB's logo (smaller than Tellycine's, as TMDB asks) and the notice its terms ask for.
                        Image(
                            painterResource(R.drawable.tmdb_logo),
                            contentDescription = "TMDB",
                            modifier = Modifier.padding(top = 16.dp).height(18.dp),
                        )
                        Text(
                            "${TmdbText.NOTICE}\n${TmdbText.SITE_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    keyInput?.let { turnOn ->
        TvTextInputDialog(
            title = TmdbText.KEY,
            initial = s.tmdbApiKey,
            hint = TmdbText.keyHelp(s.hasBuiltInTmdbKey) +
                if (s.hasBuiltInTmdbKey) " Leave it empty to use Tellycine's key." else " Leave it empty to remove the key.",
            placeholder = "API key or read access token",
            keyboardType = KeyboardType.Ascii,
            onDone = { key ->
                keyInput = null
                vm.saveTmdbKey(key, turnOn)
            },
            onDismiss = { keyInput = null },
        )
    }
    when (val check = keyCheck) {
        SettingsViewModel.KeyCheck.Checking -> TvMessageDialog(TmdbText.KEY, "Checking the key with TMDB…", onDismiss = vm::resetKeyCheck)
        is SettingsViewModel.KeyCheck.Failed -> TvMessageDialog(TmdbText.KEY, check.message, onDismiss = vm::resetKeyCheck)
        SettingsViewModel.KeyCheck.Saved -> LaunchedEffect(Unit) { vm.resetKeyCheck() }
        SettingsViewModel.KeyCheck.Idle -> Unit
    }
    if (playerDialog) {
        TvChoiceDialog(
            title = "Default player",
            options = listOf(false to "Built-in player", true to "Another app"),
            selected = s.externalPlayer,
            onSelect = {
                vm.setExternalPlayer(it)
                playerDialog = false
            },
            onDismiss = { playerDialog = false },
        )
    }
    if (themeDialog) {
        // Each choice applies at once, so the screen behind shows it.
        TvChoiceDialog(
            title = "Theme",
            options = TvTheme.entries.map { it to it.label },
            selected = s.tvTheme,
            onSelect = {
                vm.setTvTheme(it)
                themeDialog = false
            },
            onDismiss = { themeDialog = false },
        )
    }
    if (epgDialog) {
        TvChoiceDialog(
            title = "Guide days",
            options = listOf(1, 2, 3, 5, 7).map { it to "$it day${if (it == 1) "" else "s"}" },
            selected = s.epgDays,
            onSelect = {
                vm.setEpgDays(it)
                epgDialog = false
            },
            onDismiss = { epgDialog = false },
        )
    }
    if (clearDialog) {
        TvConfirmDialog(
            title = "Clear watch history?",
            message = "This can't be undone.",
            confirmLabel = "Clear",
            onConfirm = {
                vm.clearHistory()
                clearDialog = false
            },
            onDismiss = { clearDialog = false },
        )
    }
}
