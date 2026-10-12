package app.tellycine.tv.playlists

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import app.tellycine.core.xtream.XtreamUrls
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import app.tellycine.data.net.UserAgents
import app.tellycine.data.repo.PlaylistFiles
import app.tellycine.tv.TvPalette
import app.tellycine.tv.components.RequestFocusOnStart
import app.tellycine.tv.components.TvActionButton
import app.tellycine.tv.components.TvChoiceDialog
import app.tellycine.tv.components.TvDialog
import app.tellycine.tv.components.TvHeader
import app.tellycine.tv.components.TvMessageDialog
import app.tellycine.tv.components.TvSectionLabel
import app.tellycine.tv.components.TvSpacing
import app.tellycine.tv.components.TvSwitchRow
import app.tellycine.tv.components.TvTextInputDialog
import app.tellycine.tv.components.TvValueRow
import app.tellycine.ui.components.tellycineViewModel
import app.tellycine.ui.playlists.DvrConnectState
import app.tellycine.ui.playlists.EditPlaylistViewModel
import app.tellycine.ui.playlists.xtreamHint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Field { NAME, URL, SERVER, USERNAME, PASSWORD, EPG, USER_AGENT, CONNECTIONS }

/**
 * Add or edit a playlist with the remote. Each field is a row; OK opens the keyboard for it, so
 * moving down the form doesn't keep popping the keyboard up.
 */
@Composable
fun TvEditPlaylistScreen(editId: Long?, onDone: () -> Unit, onManageCategories: (Long) -> Unit) {
    val vm = tellycineViewModel(key = "edit-$editId") { c, _ -> EditPlaylistViewModel(c, editId) }
    val form by vm.form.collectAsStateWithLifecycle()
    val xtream by vm.xtream.collectAsStateWithLifecycle()
    val saved by vm.existing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable { mutableStateOf<Field?>(null) }
    var choosingType by remember { mutableStateOf(false) }
    var choosingAgent by remember { mutableStateOf(false) }
    var noPicker by remember { mutableStateOf(false) }
    var dvrDialog by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }

    LaunchedEffect(form.saved) { if (form.saved) onDone() }

    // TVs often lack a file picker that grants lasting access, so the file is copied into app storage.
    fun importFile(uri: Uri) {
        scope.launch {
            val copied = withContext(Dispatchers.IO) { PlaylistFiles.copy(context, uri) }
            if (copied == null) {
                Toast.makeText(context, "Couldn't read that file", Toast.LENGTH_LONG).show()
            } else {
                vm.update { it.copy(fileUri = copied.first, fileName = copied.second) }
            }
        }
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::importFile) }
    val getContent = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(::importFile) }
    fun pickFile() {
        try {
            openDocument.launch(arrayOf("*/*"))
        } catch (_: ActivityNotFoundException) {
            try {
                getContent.launch("*/*")
            } catch (_: ActivityNotFoundException) {
                noPicker = true
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TvHeader(
            title = if (editId == null) "Add playlist" else "Edit playlist",
            subtitle = saved?.name,
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 820.dp).fillMaxWidth(),
                contentPadding = PaddingValues(start = TvSpacing.screenStart, end = TvSpacing.screenEnd, bottom = TvSpacing.screenBottom),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "type") {
                    TvValueRow(
                        "Type",
                        typeLabel(form.type),
                        onClick = { choosingType = true },
                        icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                        modifier = Modifier.focusRequester(first),
                    )
                }
                item(key = "name") {
                    TvValueRow("Name (optional)", form.name.ifEmpty { "Named after the server when left empty" }, onClick = { editing = Field.NAME }, icon = Icons.AutoMirrored.Filled.Label)
                }
                when (form.type) {
                    PlaylistType.M3U_URL -> {
                        item(key = "url") {
                            TvValueRow("Playlist URL", form.url.ifEmpty { "Not set" }, onClick = { editing = Field.URL }, icon = Icons.Filled.Link)
                        }
                        if (form.looksLikeDispatcharrM3u) {
                            item(key = "dispatcharr-hint") {
                                Hint(
                                    "This is a Dispatcharr playlist. Use your Dispatcharr Xtream login instead to get catch-up, movies and series.",
                                    "Use Xtream login",
                                ) {
                                    vm.update { it.copy(type = PlaylistType.XTREAM, server = XtreamUrls.normaliseServer(it.url.substringBefore("/output"))) }
                                }
                            }
                        }
                        xtreamHint(form, xtream)?.let { hint ->
                            item(key = "xtream-hint") {
                                Hint(hint.message, "Use Xtream Codes") { vm.useXtream(hint.offer) }
                            }
                        }
                    }
                    PlaylistType.M3U_FILE -> item(key = "file") {
                        TvValueRow(
                            "Playlist file",
                            form.fileName.ifEmpty { "Choose an .m3u or .m3u8 file. No file picker on your TV? Send the file from your phone instead." },
                            onClick = ::pickFile,
                            icon = Icons.Filled.FolderOpen,
                        )
                    }
                    PlaylistType.XTREAM -> {
                        item(key = "server") {
                            TvValueRow("Server", form.server.ifEmpty { "e.g. http://example.com:8080" }, onClick = { editing = Field.SERVER }, icon = Icons.Filled.Dns)
                        }
                        item(key = "username") {
                            TvValueRow("Username", form.username.ifEmpty { "Not set" }, onClick = { editing = Field.USERNAME }, icon = Icons.Filled.Person)
                        }
                        item(key = "password") {
                            TvValueRow("Password", if (form.password.isEmpty()) "Not set" else "•".repeat(form.password.length.coerceAtMost(12)), onClick = { editing = Field.PASSWORD }, icon = Icons.Filled.Key)
                        }
                    }
                }
                item(key = "vod") {
                    TvSwitchRow(
                        "Movies & series",
                        "Turn off to skip VOD entirely: faster refreshes, and only Live TV is shown for this playlist.",
                        form.vodEnabled,
                        onChange = { v -> vm.update { it.copy(vodEnabled = v) } },
                    )
                }
                if (editId != null) {
                    item(key = "categories") {
                        TvValueRow("Categories", "Choose which categories to show", onClick = { onManageCategories(editId) }, icon = Icons.Filled.Category)
                    }
                }
                saved?.takeIf { it.isDispatcharr }?.let { playlist ->
                    item(key = "dvr") {
                        TvValueRow(
                            "Dispatcharr recordings",
                            if (playlist.hasDvr) "Connected${playlist.dvrUsername?.let { " as $it" } ?: ""}. Recordings appear in Library." else "Sign in with your Dispatcharr web login to watch and schedule recordings",
                            onClick = { vm.clearDvrResult(); dvrDialog = true },
                            icon = Icons.Filled.FiberManualRecord,
                        )
                    }
                }
                item(key = "connections") {
                    val reported = saved?.maxConnections
                    TvValueRow(
                        "Maximum connections",
                        when {
                            form.connectionLimit.isNotEmpty() -> form.connectionLimit
                            reported != null -> "$reported (reported by your provider)"
                            else -> "Not set"
                        },
                        onClick = { editing = Field.CONNECTIONS },
                        icon = Icons.Filled.Devices,
                    )
                }
                item(key = "advanced") { TvSectionLabel("Advanced") }
                item(key = "epg") {
                    TvValueRow(
                        "XMLTV guide URL",
                        form.epgUrl.ifEmpty { if (form.type == PlaylistType.XTREAM) "The provider's guide" else "The guide linked in the playlist" },
                        onClick = { editing = Field.EPG },
                        icon = Icons.Filled.Schedule,
                    )
                }
                item(key = "agent") {
                    TvValueRow(
                        "User agent",
                        form.userAgentPreset?.name ?: "Custom: ${form.userAgent.ifEmpty { "default" }}",
                        onClick = { choosingAgent = true },
                        icon = Icons.Filled.Smartphone,
                    )
                }
                form.error?.let { error ->
                    item(key = "error") {
                        Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    }
                }
                item(key = "save") {
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                        TvActionButton("Cancel", onClick = onDone)
                        TvActionButton(if (editId == null) "Add and load" else "Save and reload", onClick = vm::save, primary = true, enabled = form.loaded)
                    }
                }
            }
        }
    }
    RequestFocusOnStart(first, form.loaded)

    when (editing) {
        Field.NAME -> TextField("Name", form.name, onDismiss = { editing = null }) { v -> vm.update { it.copy(name = v) } }
        Field.URL -> TextField("Playlist URL", form.url, placeholder = "http://example.com/playlist.m3u", keyboard = KeyboardType.Uri, onDismiss = { editing = null }) { v ->
            vm.update { it.copy(url = v.trim()) }
        }
        Field.SERVER -> TextField("Server", form.server, placeholder = "http://example.com:8080", keyboard = KeyboardType.Uri, onDismiss = { editing = null }) { v ->
            vm.update { it.copy(server = v.trim()) }
        }
        Field.USERNAME -> TextField("Username", form.username, onDismiss = { editing = null }) { v -> vm.update { it.copy(username = v.trim()) } }
        Field.PASSWORD -> TextField("Password", form.password, keyboard = KeyboardType.Password, password = true, onDismiss = { editing = null }) { v ->
            vm.update { it.copy(password = v) }
        }
        Field.EPG -> TextField(
            "XMLTV guide URL",
            form.epgUrl,
            hint = if (form.type == PlaylistType.XTREAM) "Leave empty to use the provider's guide." else "Leave empty to use the guide linked in the playlist. Separate several with commas.",
            keyboard = KeyboardType.Uri,
            onDismiss = { editing = null },
        ) { v -> vm.update { it.copy(epgUrl = v.trim()) } }
        Field.USER_AGENT -> TextField("Custom user agent", form.userAgent, hint = "Sent with every request for this playlist. Leave empty for the default.", onDismiss = { editing = null }) { v ->
            vm.update { it.copy(userAgentPreset = null, userAgent = v.trim()) }
        }
        Field.CONNECTIONS -> TextField(
            "Maximum connections",
            form.connectionLimit,
            hint = "How many streams your account can play at once. Leave empty to use what your provider reports.",
            keyboard = KeyboardType.Number,
            onDismiss = { editing = null },
        ) { v -> vm.update { it.copy(connectionLimit = v.filter(Char::isDigit).take(2)) } }
        null -> Unit
    }

    if (choosingType) {
        TvChoiceDialog(
            title = "Playlist type",
            options = listOf(PlaylistType.M3U_URL, PlaylistType.XTREAM, PlaylistType.M3U_FILE).map { it to typeLabel(it) },
            selected = form.type,
            supporting = {
                when (it) {
                    PlaylistType.M3U_URL -> "A link to an .m3u or .m3u8 playlist"
                    PlaylistType.XTREAM -> "Server, username and password (Xtream Codes or Dispatcharr)"
                    PlaylistType.M3U_FILE -> "A playlist file on this TV or a USB drive"
                }
            },
            onSelect = { type ->
                vm.update { it.copy(type = type) }
                choosingType = false
            },
            onDismiss = { choosingType = false },
        )
    }
    if (choosingAgent) {
        val custom = "custom"
        TvChoiceDialog(
            title = "User agent",
            options = UserAgents.presets.map { it.name to it.name } + (custom to "Custom…"),
            selected = form.userAgentPreset?.name ?: custom,
            supporting = { name -> UserAgents.presets.firstOrNull { it.name == name }?.value?.ifEmpty { null } },
            onSelect = { name ->
                choosingAgent = false
                val preset = UserAgents.presets.firstOrNull { it.name == name }
                if (preset != null) {
                    vm.update { it.copy(userAgentPreset = preset) }
                } else {
                    // Start from the previous preset's value so it can be tweaked.
                    vm.update { it.copy(userAgentPreset = null, userAgent = it.userAgentPreset?.value ?: it.userAgent) }
                    editing = Field.USER_AGENT
                }
            },
            onDismiss = { choosingAgent = false },
        )
    }
    if (noPicker) {
        TvMessageDialog(
            "No file picker",
            "This TV doesn't have an app for choosing files. Use a playlist URL instead, or choose Add from phone on the Playlists screen to send the file from your phone.",
            onDismiss = { noPicker = false },
        )
    }
    if (dvrDialog) {
        saved?.let { playlist ->
            val dvr by vm.dvr.collectAsStateWithLifecycle()
            DvrDialog(playlist, dvr, onConnect = vm::connectDvr, onDisconnect = {
                vm.disconnectDvr()
                dvrDialog = false
            }, onDismiss = { dvrDialog = false })
            LaunchedEffect(dvr.connected) { if (dvr.connected) dvrDialog = false }
        }
    }
}

private fun typeLabel(type: PlaylistType) = when (type) {
    PlaylistType.M3U_URL -> "M3U link"
    PlaylistType.M3U_FILE -> "Playlist file"
    PlaylistType.XTREAM -> "Xtream Codes or Dispatcharr login"
}

@Composable
private fun TextField(
    title: String,
    value: String,
    onDismiss: () -> Unit,
    placeholder: String? = null,
    hint: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    onDone: (String) -> Unit,
) {
    TvTextInputDialog(
        title = title,
        initial = value,
        placeholder = placeholder,
        hint = hint,
        keyboardType = keyboard,
        password = password,
        onDone = {
            onDone(it)
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun Hint(text: String, action: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TvPalette.primary, modifier = Modifier.weight(1f))
        TvActionButton(action, onClick = onAction)
    }
}

/** Connect (or disconnect) a Dispatcharr web login for recordings. */
@Composable
private fun DvrDialog(
    playlist: PlaylistEntity,
    state: DvrConnectState,
    onConnect: (username: String, password: String, apiKey: String) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf(playlist.dvrUsername ?: playlist.username) }
    var password by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var useKey by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    TvDialog(
        title = "Dispatcharr recordings",
        onDismiss = { if (!state.busy) onDismiss() },
        width = 640.dp,
        buttons = {
            if (playlist.hasDvr) OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
            OutlinedButton(onClick = onDismiss, enabled = !state.busy) { Text("Cancel") }
            Button(
                onClick = { onConnect(username, password, if (useKey) apiKey else "") },
                enabled = !state.busy && (if (useKey) apiKey.isNotBlank() else username.isNotBlank() && password.isNotEmpty()),
            ) { Text("Connect") }
        },
    ) {
        Text(
            "Sign in with your Dispatcharr web login (not the Xtream password). Your user needs DVR access in Dispatcharr. " +
                "Tellycine keeps only an API key, never the password.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (useKey) {
            TvValueRow("API key", if (apiKey.isEmpty()) "Not set" else "•".repeat(12), onClick = { editing = "key" }, modifier = Modifier.focusRequester(first))
        } else {
            TvValueRow("Username", username.ifEmpty { "Not set" }, onClick = { editing = "user" }, modifier = Modifier.focusRequester(first))
            TvValueRow("Password", if (password.isEmpty()) "Not set" else "•".repeat(password.length.coerceAtMost(12)), onClick = { editing = "password" })
        }
        TvValueRow(
            if (useKey) "Sign in with username and password instead" else "Use an API key instead",
            null,
            onClick = { useKey = !useKey },
        )
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    RequestFocusOnStart(first, useKey)
    when (editing) {
        "user" -> TextField("Username", username, onDismiss = { editing = null }) { username = it.trim() }
        "password" -> TextField("Password", password, keyboard = KeyboardType.Password, password = true, onDismiss = { editing = null }) { password = it }
        "key" -> TextField("API key", apiKey, onDismiss = { editing = null }) { apiKey = it.trim() }
    }
}
