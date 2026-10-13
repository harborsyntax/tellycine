package app.tellycine.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tellycine.AppContainer
import app.tellycine.BuildConfig
import app.tellycine.R
import app.tellycine.data.settings.AppSettings
import app.tellycine.data.settings.ThemeMode
import app.tellycine.data.settings.TvTheme
import app.tellycine.data.tmdb.TmdbClient
import app.tellycine.ui.components.ListItem
import app.tellycine.ui.components.TellycineLogo
import app.tellycine.ui.components.tellycineViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.settings
    val settings: StateFlow<AppSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode) }
    fun setDynamicColor(v: Boolean) = viewModelScope.launch { repo.setDynamicColor(v) }
    fun setEpgDays(v: Int) = viewModelScope.launch { repo.setEpgDays(v) }
    fun setTvTheme(theme: TvTheme) = viewModelScope.launch { repo.setTvTheme(theme) }
    fun setRefreshOnLaunch(v: Boolean) = viewModelScope.launch { repo.setRefreshOnLaunch(v) }
    fun setPreferHls(v: Boolean) = viewModelScope.launch { repo.setPreferHls(v) }
    fun setExternalPlayer(v: Boolean) = viewModelScope.launch { repo.setExternalPlayer(v) }
    fun setDownloadWifiOnly(v: Boolean) = viewModelScope.launch { repo.setDownloadWifiOnly(v) }
    fun clearHistory() = viewModelScope.launch { container.content.clearHistory() }
    fun setStartOnLastChannel(v: Boolean) = viewModelScope.launch { repo.setStartOnLastChannel(v) }
    fun setMatchFrameRate(v: Boolean) = viewModelScope.launch { repo.setMatchFrameRate(v) }
    fun setTmdbEnabled(v: Boolean) = viewModelScope.launch { repo.setTmdbEnabled(v) }

    /** Checking a TMDB key before keeping it. */
    sealed interface KeyCheck {
        data object Idle : KeyCheck
        data object Checking : KeyCheck
        data object Saved : KeyCheck
        data class Failed(val message: String) : KeyCheck
    }

    private val _keyCheck = MutableStateFlow<KeyCheck>(KeyCheck.Idle)
    val keyCheck: StateFlow<KeyCheck> = _keyCheck.asStateFlow()

    /** Checks [key] with TMDB and keeps it if TMDB accepts it, turning details from TMDB on if [turnOn]. */
    fun saveTmdbKey(key: String, turnOn: Boolean) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            removeTmdbKey()
            _keyCheck.value = KeyCheck.Saved
            return
        }
        _keyCheck.value = KeyCheck.Checking
        viewModelScope.launch {
            _keyCheck.value = when (container.tmdb.client.checkKey(trimmed)) {
                TmdbClient.KeyCheck.WORKS -> {
                    repo.setTmdbApiKey(trimmed)
                    if (turnOn) repo.setTmdbEnabled(true)
                    KeyCheck.Saved
                }
                TmdbClient.KeyCheck.REJECTED -> KeyCheck.Failed(TmdbText.KEY_REJECTED)
                TmdbClient.KeyCheck.UNREACHABLE -> KeyCheck.Failed(TmdbText.KEY_UNREACHABLE)
            }
        }
    }

    /** Forgets the user's key: then Tellycine's own is used, or without one, details from TMDB turn off. */
    fun removeTmdbKey() = viewModelScope.launch { repo.setTmdbApiKey("") }

    fun resetKeyCheck() {
        _keyCheck.value = KeyCheck.Idle
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenDownloads: () -> Unit) {
    val vm = tellycineViewModel { c, _ -> SettingsViewModel(c) }
    val s by vm.settings.collectAsStateWithLifecycle()
    var themeDialog by remember { mutableStateOf(false) }
    var epgDialog by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    var playerDialog by remember { mutableStateOf(false) }
    /** The TMDB key dialog: open, and whether a saved key turns details from TMDB on. */
    var keyDialog by remember { mutableStateOf<Boolean?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Header("Appearance")
            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = { Text(s.themeMode.label()) },
                modifier = Modifier.clickable { themeDialog = true },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchItem("Dynamic colour", "Match colours to your wallpaper", s.dynamicColor, vm::setDynamicColor)
            }
            HorizontalDivider()

            Header("Playback")
            ListItem(
                headlineContent = { Text("Default player") },
                supportingContent = {
                    Text(
                        if (s.externalPlayer) "External app: Android asks which app to use, and you can pick one to always use. " +
                            "Dispatcharr recordings still play here."
                        else "Built-in player"
                    )
                },
                modifier = Modifier.clickable { playerDialog = true },
            )
            HorizontalDivider()

            Header("Downloads")
            SwitchItem(
                "Download over Wi-Fi only",
                "Wait for Wi-Fi (or another unmetered connection). Applies to new downloads.",
                s.downloadWifiOnly,
                vm::setDownloadWifiOnly,
            )
            ListItem(
                headlineContent = { Text("Manage downloads") },
                supportingContent = { Text("See, play and delete downloaded movies and episodes") },
                modifier = Modifier.clickable(onClick = onOpenDownloads),
            )
            HorizontalDivider()

            Header("Playlists & guide")
            SwitchItem(
                "Refresh on launch",
                "Reload playlists older than a day and guides older than 12 hours",
                s.refreshOnLaunch,
                vm::setRefreshOnLaunch,
            )
            ListItem(
                headlineContent = { Text("Guide days") },
                supportingContent = { Text("Keep ${s.epgDays} day${if (s.epgDays == 1) "" else "s"} of programmes. Fewer days use less storage.") },
                modifier = Modifier.clickable { epgDialog = true },
            )
            SwitchItem(
                "Prefer HLS for Xtream live TV",
                "Use .m3u8 instead of MPEG-TS. Applies after the next refresh.",
                s.preferHls,
                vm::setPreferHls,
            )
            HorizontalDivider()

            Header("Movies & series")
            SwitchItem(TmdbText.TITLE, TmdbText.setting(s.hasBuiltInTmdbKey), s.tmdb) { on ->
                // Turning it on needs a key first, in a build without one.
                if (on && s.tmdbKey.isBlank()) keyDialog = true else vm.setTmdbEnabled(on)
            }
            ListItem(
                headlineContent = { Text(TmdbText.KEY) },
                supportingContent = { Text(TmdbText.keySummary(s.tmdbApiKey, s.hasBuiltInTmdbKey)) },
                modifier = Modifier.clickable { keyDialog = false },
            )
            HorizontalDivider()

            Header("Privacy")
            ListItem(
                headlineContent = { Text("Clear watch history") },
                supportingContent = { Text("Removes resume points and recently watched channels") },
                modifier = Modifier.clickable { clearDialog = true },
            )
            HorizontalDivider()

            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TellycineLogo(height = 32.dp)
                Text(
                    "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Tellycine is a player only and does not provide any content.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                TmdbCredit { context.openLink(TmdbText.SITE) }
            }
        }
    }

    keyDialog?.let { turnOn ->
        val check by vm.keyCheck.collectAsStateWithLifecycle()
        TmdbKeyDialog(
            current = s.tmdbApiKey,
            builtIn = s.hasBuiltInTmdbKey,
            check = check,
            onSave = { vm.saveTmdbKey(it, turnOn) },
            onOpenKeyPage = { context.openLink(TmdbText.KEY_PAGE) },
            onDismiss = {
                keyDialog = null
                vm.resetKeyCheck()
            },
        )
    }
    if (themeDialog) {
        ChoiceDialog(
            title = "Theme",
            options = ThemeMode.entries.map { it to it.label() },
            selected = s.themeMode,
            onSelect = { vm.setTheme(it); themeDialog = false },
            onDismiss = { themeDialog = false },
        )
    }
    if (playerDialog) {
        ChoiceDialog(
            title = "Default player",
            options = listOf(false to "Built-in player", true to "External app"),
            selected = s.externalPlayer,
            onSelect = { vm.setExternalPlayer(it); playerDialog = false },
            onDismiss = { playerDialog = false },
        )
    }
    if (epgDialog) {
        ChoiceDialog(
            title = "Guide days",
            options = listOf(1, 2, 3, 5, 7).map { it to "$it day${if (it == 1) "" else "s"}" },
            selected = s.epgDays,
            onSelect = { vm.setEpgDays(it); epgDialog = false },
            onDismiss = { epgDialog = false },
        )
    }
    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text("Clear watch history?") },
            text = { Text("This can't be undone.") },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); clearDialog = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Cancel") } },
        )
    }
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.SYSTEM -> "System default"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

/** TMDB's logo and the notice its terms ask for, with a link to its site. */
@Composable
private fun TmdbCredit(onOpenSite: () -> Unit) {
    Column(
        Modifier.padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Smaller than Tellycine's own logo, as TMDB asks.
        Image(painterResource(R.drawable.tmdb_logo), contentDescription = "TMDB", modifier = Modifier.height(14.dp))
        Text(
            TmdbText.NOTICE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onOpenSite) { Text(TmdbText.SITE_NAME) }
    }
}

/** Typing a TMDB key, which is checked with TMDB before it's kept. Empty forgets the key. */
@Composable
private fun TmdbKeyDialog(
    current: String,
    builtIn: Boolean,
    check: SettingsViewModel.KeyCheck,
    onSave: (String) -> Unit,
    onOpenKeyPage: () -> Unit,
    onDismiss: () -> Unit,
) {
    var key by remember { mutableStateOf(current) }
    LaunchedEffect(check) { if (check == SettingsViewModel.KeyCheck.Saved) onDismiss() }
    val checking = check == SettingsViewModel.KeyCheck.Checking
    AlertDialog(
        onDismissRequest = onDismiss,
        // Its own margins rather than the platform's dialog width: with that, the focused text
        // field kept the dialog laying itself out again (seen in Robolectric tests).
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 24.dp),
        title = { Text(TmdbText.KEY) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(TmdbText.keyHelp(builtIn), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onOpenKeyPage, contentPadding = PaddingValues(0.dp)) { Text("Open TMDB's API settings") }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API key or read access token") },
                    singleLine = true,
                    enabled = !checking,
                    isError = check is SettingsViewModel.KeyCheck.Failed,
                    supportingText = (check as? SettingsViewModel.KeyCheck.Failed)?.let { failed -> { Text(failed.message) } },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (checking) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(key) }, enabled = !checking && (key.isNotBlank() || current.isNotBlank())) {
                Text(if (key.isBlank() && current.isNotBlank()) "Remove" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Opens [url] in a browser, if there is one. */
private fun Context.openLink(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun SwitchItem(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.selectable(selected = checked, role = Role.Switch, onClick = { onChange(!checked) }),
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = { RadioButton(selected = value == selected, onClick = null) },
                        modifier = Modifier.selectable(selected = value == selected, role = Role.RadioButton) { onSelect(value) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
