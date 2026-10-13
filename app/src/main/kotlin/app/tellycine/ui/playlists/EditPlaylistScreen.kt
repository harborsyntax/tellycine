package app.tellycine.ui.playlists

import android.content.Intent
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.foundation.layout.PaddingValues
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextOverflow
import app.tellycine.data.net.UserAgentPreset
import app.tellycine.data.net.UserAgents
import app.tellycine.AppContainer
import app.tellycine.core.xtream.XtreamUrls
import app.tellycine.data.repo.XtreamOffer
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import app.tellycine.ui.components.ListItem
import app.tellycine.ui.components.tellycineViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DvrConnectState(
    val busy: Boolean = false,
    val error: String? = null,
    val connected: Boolean = false,
)

data class PlaylistForm(
    val loaded: Boolean = false,
    val id: Long = 0,
    val type: PlaylistType = PlaylistType.M3U_URL,
    val name: String = "",
    val url: String = "",
    val fileUri: String = "",
    val fileName: String = "",
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val epgUrl: String = "",
    /** Selected entry in [UserAgents.presets], or null for a custom user agent. */
    val userAgentPreset: UserAgentPreset? = UserAgents.presets.first(),
    /** The custom user agent, used when [userAgentPreset] is null. */
    val userAgent: String = "",
    /** Maximum connections as typed; empty uses what the provider reports. */
    val connectionLimit: String = "",
    val vodEnabled: Boolean = true,
    val saved: Boolean = false,
    val error: String? = null,
) {
    /** Xtream credentials detected inside an M3U `get.php` link. */
    val detectedXtream get() = if (type == PlaylistType.M3U_URL) XtreamUrls.fromM3uUrl(url.trim()) else null

    /** A Dispatcharr `/output/m3u` link: catch-up, movies and series need its Xtream login instead. */
    val looksLikeDispatcharrM3u get() =
        type == PlaylistType.M3U_URL && Regex("(?i)^https?://[^/]+/output/m3u(/|\\?|$)").containsMatchIn(url.trim())
}

/** A link worth asking its server about. */
private val LINK = Regex("""(?i)^https?://[^/?#\s]+.*""")

/** What an M3U link's server offers of Xtream Codes, once [XtreamFinder] has checked the link [url]. */
data class XtreamCheck(val url: String, val offer: XtreamOffer?)

/** A suggestion to use Xtream Codes instead of an M3U link. */
data class XtreamHint(val message: String, val offer: XtreamOffer)

/**
 * The suggestion for the M3U link in [form], if any: from the [check] of its server once there is
 * one, or until then (or if the server couldn't be reached) from the shape of the link.
 */
fun xtreamHint(form: PlaylistForm, check: XtreamCheck?): XtreamHint? {
    if (form.type != PlaylistType.M3U_URL || form.looksLikeDispatcharrM3u) return null
    val checked = check?.takeIf { it.url == form.url.trim() }
    val offer = checked?.offer
    return when {
        offer?.loginWorks == true -> XtreamHint(
            "Your provider also offers Xtream Codes, and this link's login works with it. " +
                "Use it for movie and series details, catch-up and sorting by date.",
            offer,
        )
        offer != null -> XtreamHint(
            "Your provider's server also offers Xtream Codes. Use your Xtream login (often the same as in " +
                "your M3U link) for movie and series details, catch-up and sorting by date.",
            offer,
        )
        // Checked: no Xtream Codes there.
        checked != null -> null
        else -> form.detectedXtream?.let { (server, user, pass) ->
            XtreamHint("This looks like an Xtream Codes link. Its API gives movie and series details, catch-up and more.", XtreamOffer(server, user, pass, loginWorks = false))
        }
    }
}

@OptIn(FlowPreview::class)
class EditPlaylistViewModel(private val container: AppContainer, private val editId: Long?) : ViewModel() {
    /** The saved playlist being edited (live, so DVR connection changes show immediately). */
    val existing: StateFlow<PlaylistEntity?> = container.playlists.playlists
        .map { list -> list.firstOrNull { it.id == editId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _dvr = MutableStateFlow(DvrConnectState())
    val dvr: StateFlow<DvrConnectState> = _dvr.asStateFlow()

    fun connectDvr(username: String, password: String, apiKey: String) {
        val playlist = existing.value ?: return
        _dvr.value = DvrConnectState(busy = true)
        viewModelScope.launch {
            _dvr.value = try {
                if (apiKey.isNotBlank()) container.dvr.connectWithKey(playlist, apiKey)
                else container.dvr.connect(playlist, username.trim(), password)
                DvrConnectState(connected = true)
            } catch (e: Exception) {
                DvrConnectState(error = e.message ?: "Couldn't connect to Dispatcharr")
            }
        }
    }

    fun disconnectDvr() {
        val id = existing.value?.id ?: return
        viewModelScope.launch { container.dvr.disconnect(id) }
        _dvr.value = DvrConnectState()
    }

    fun clearDvrResult() {
        _dvr.value = DvrConnectState()
    }
    private val _form = MutableStateFlow(PlaylistForm())
    val form: StateFlow<PlaylistForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = editId?.let { container.playlists.get(it) }
            _form.value = if (existing == null) PlaylistForm(loaded = true) else PlaylistForm(
                loaded = true,
                id = existing.id,
                type = existing.type,
                name = existing.name,
                url = if (existing.type == PlaylistType.M3U_URL) existing.url else "",
                fileUri = if (existing.type == PlaylistType.M3U_FILE) existing.url else "",
                fileName = if (existing.type == PlaylistType.M3U_FILE) Uri.parse(existing.url).lastPathSegment.orEmpty() else "",
                server = if (existing.type == PlaylistType.XTREAM) existing.url else "",
                username = existing.username,
                password = existing.password,
                epgUrl = existing.epgUrl,
                userAgentPreset = UserAgents.presetFor(existing.userAgent),
                userAgent = existing.userAgent,
                connectionLimit = existing.connectionLimit?.toString().orEmpty(),
                vodEnabled = existing.vodEnabled,
            )
        }
    }

    fun update(transform: (PlaylistForm) -> PlaylistForm) = _form.update { transform(it).copy(error = null) }

    private val _xtream = MutableStateFlow<XtreamCheck?>(null)

    /** The latest check of the M3U link's server for Xtream Codes (see [xtreamHint]). */
    val xtream: StateFlow<XtreamCheck?> = _xtream.asStateFlow()

    init {
        // Once the link has been typed or pasted, ask its server about Xtream Codes.
        viewModelScope.launch {
            _form.map { f -> f.url.trim().takeIf { f.type == PlaylistType.M3U_URL && !f.looksLikeDispatcharrM3u && LINK.matches(it) } }
                .distinctUntilChanged()
                .debounce(700)
                .collectLatest { url ->
                    if (url == null) return@collectLatest
                    val f = _form.value
                    val offer = try {
                        container.xtreamFinder.check(url, f.userAgentPreset?.value ?: f.userAgent.trim())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Unreachable for now: the link's shape is all there is to go on.
                        return@collectLatest
                    }
                    _xtream.value = XtreamCheck(url, offer)
                }
        }
    }

    /** Switches to Xtream Codes with [offer]'s server and login (the user fills in a missing login). */
    fun useXtream(offer: XtreamOffer) {
        update { it.copy(type = PlaylistType.XTREAM, server = offer.server, username = offer.username, password = offer.password) }
    }

    fun save() {
        val f = _form.value
        val error = when (f.type) {
            PlaylistType.M3U_URL -> if (!f.url.trim().matches(Regex("(?i)^https?://.+"))) "Enter a valid http(s) playlist URL" else null
            PlaylistType.M3U_FILE -> if (f.fileUri.isBlank()) "Choose a playlist file" else null
            PlaylistType.XTREAM -> when {
                f.server.isBlank() -> "Enter the server address"
                f.username.isBlank() -> "Enter your username"
                f.password.isBlank() -> "Enter your password"
                else -> null
            }
        }
        val limit = f.connectionLimit.trim()
        val connectionLimit = limit.toIntOrNull()
        val limitError = if (limit.isNotEmpty() && (connectionLimit == null || connectionLimit !in 1..99)) {
            "Enter a number of connections from 1 to 99, or leave it empty"
        } else {
            null
        }
        if (error != null || limitError != null) {
            _form.update { it.copy(error = error ?: limitError) }
            return
        }
        val url = when (f.type) {
            PlaylistType.M3U_URL -> f.url.trim()
            PlaylistType.M3U_FILE -> f.fileUri
            PlaylistType.XTREAM -> XtreamUrls.normaliseServer(f.server)
        }
        val name = f.name.trim().ifEmpty {
            when (f.type) {
                PlaylistType.XTREAM -> Uri.parse(url).host ?: "Xtream"
                PlaylistType.M3U_URL -> Uri.parse(url).host ?: "Playlist"
                PlaylistType.M3U_FILE -> f.fileName.substringBeforeLast('.').ifEmpty { "Playlist" }
            }
        }
        viewModelScope.launch {
            container.playlists.save(
                PlaylistEntity(
                    id = f.id,
                    name = name,
                    type = f.type,
                    url = url,
                    username = if (f.type == PlaylistType.XTREAM) f.username.trim() else "",
                    password = if (f.type == PlaylistType.XTREAM) f.password else "",
                    epgUrl = f.epgUrl.trim(),
                    userAgent = f.userAgentPreset?.value ?: f.userAgent.trim(),
                    connectionLimit = connectionLimit,
                    vodEnabled = f.vodEnabled,
                )
            )
            _form.update { it.copy(saved = true) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlaylistScreen(editId: Long?, onDone: () -> Unit, onManageCategories: (Long) -> Unit) {
    val vm = tellycineViewModel(key = "edit-$editId") { c, _ -> EditPlaylistViewModel(c, editId) }
    val form by vm.form.collectAsStateWithLifecycle()
    val xtream by vm.xtream.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(form.saved) { if (form.saved) onDone() }
    LaunchedEffect(form.loaded) {
        if (form.loaded && (form.epgUrl.isNotBlank() || form.userAgent.isNotBlank())) advanced = true
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            vm.update { it.copy(fileUri = uri.toString(), fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "playlist.m3u") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editId == null) "Add playlist" else "Edit playlist") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { TextButton(onClick = vm::save, enabled = form.loaded) { Text("Save") } },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val types = listOf(PlaylistType.M3U_URL to "M3U link", PlaylistType.M3U_FILE to "File", PlaylistType.XTREAM to "Xtream Codes")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    types.forEachIndexed { index, (type, label) ->
                        SegmentedButton(
                            selected = form.type == type,
                            onClick = { vm.update { it.copy(type = type) } },
                            shape = SegmentedButtonDefaults.itemShape(index, types.size),
                        ) { Text(label, maxLines = 1) }
                    }
                }

                OutlinedTextField(
                    value = form.name,
                    onValueChange = { v -> vm.update { it.copy(name = v) } },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                when (form.type) {
                    PlaylistType.M3U_URL -> {
                        OutlinedTextField(
                            value = form.url,
                            onValueChange = { v -> vm.update { it.copy(url = v) } },
                            label = { Text("Playlist URL") },
                            placeholder = { Text("http://example.com/playlist.m3u") },
                            leadingIcon = { Icon(Icons.Filled.Link, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (form.looksLikeDispatcharrM3u) {
                            AssistChip(
                                onClick = {
                                    vm.update {
                                        it.copy(type = PlaylistType.XTREAM, server = XtreamUrls.normaliseServer(it.url.substringBefore("/output")))
                                    }
                                },
                                label = {
                                    Text("This is a Dispatcharr playlist. Use your Dispatcharr Xtream login instead to get catch-up, movies and series.")
                                },
                            )
                        }
                        xtreamHint(form, xtream)?.let { hint ->
                            AssistChip(onClick = { vm.useXtream(hint.offer) }, label = { Text(hint.message) })
                        }
                    }
                    PlaylistType.M3U_FILE -> {
                        OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.FolderOpen, null)
                            Spacer(Modifier.width(8.dp))
                            Text(form.fileName.ifEmpty { "Choose .m3u / .m3u8 file" }, maxLines = 1)
                        }
                    }
                    PlaylistType.XTREAM -> {
                        OutlinedTextField(
                            value = form.server,
                            onValueChange = { v -> vm.update { it.copy(server = v) } },
                            label = { Text("Server") },
                            placeholder = { Text("http://example.com:8080") },
                            leadingIcon = { Icon(Icons.Filled.Dns, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = form.username,
                            onValueChange = { v -> vm.update { it.copy(username = v) } },
                            label = { Text("Username") },
                            leadingIcon = { Icon(Icons.Filled.Person, null) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = form.password,
                            onValueChange = { v -> vm.update { it.copy(password = v) } },
                            label = { Text("Password") },
                            leadingIcon = { Icon(Icons.Filled.Key, null) },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        if (showPassword) "Hide password" else "Show password",
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                ListItem(
                    headlineContent = { Text("Movies & series") },
                    supportingContent = {
                        Text("Turn off to skip VOD entirely: faster refreshes, and only Live TV is shown for this playlist.")
                    },
                    trailingContent = { Switch(checked = form.vodEnabled, onCheckedChange = null) },
                    modifier = Modifier.toggleable(
                        value = form.vodEnabled,
                        role = Role.Switch,
                        onValueChange = { v -> vm.update { it.copy(vodEnabled = v) } },
                    ),
                )

                if (editId != null) {
                    OutlinedButton(onClick = { onManageCategories(editId) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Category, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Choose categories to show")
                    }
                }

                val saved by vm.existing.collectAsStateWithLifecycle()
                saved?.takeIf { it.isDispatcharr }?.let { playlist ->
                    DvrSection(playlist, vm)
                }

                val reported = saved?.maxConnections
                OutlinedTextField(
                    value = form.connectionLimit,
                    onValueChange = { v -> vm.update { it.copy(connectionLimit = v.filter(Char::isDigit).take(2)) } },
                    label = { Text("Maximum connections") },
                    placeholder = { reported?.let { Text("$it") } },
                    supportingText = {
                        Text(
                            when {
                                reported != null -> "Your provider reports $reported. Leave empty to use that."
                                form.type == PlaylistType.XTREAM -> "Leave empty to use the number your provider reports."
                                else -> "How many streams your account can play at once. Shown on the playlist card."
                            }
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                TextButton(onClick = { advanced = !advanced }) {
                    Text("Advanced options")
                    Icon(if (advanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                }
                if (advanced) {
                    OutlinedTextField(
                        value = form.epgUrl,
                        onValueChange = { v -> vm.update { it.copy(epgUrl = v) } },
                        label = { Text("XMLTV guide URL") },
                        supportingText = {
                            Text(
                                if (form.type == PlaylistType.XTREAM) "Leave empty to use the provider's guide."
                                else "Leave empty to use the guide linked in the playlist. Separate several with commas."
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    UserAgentPicker(form, vm)
                }

                form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = vm::save, enabled = form.loaded) {
                        Text(if (editId == null) "Add and load" else "Save and reload")
                    }
                }
            }
        }
    }
}

@Composable
private fun DvrSection(playlist: PlaylistEntity, vm: EditPlaylistViewModel) {
    val dvr by vm.dvr.collectAsStateWithLifecycle()
    var showDialog by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.FiberManualRecord, null, tint = MaterialTheme.colorScheme.error)
                Text("Dispatcharr recordings", style = MaterialTheme.typography.titleMedium)
            }
            if (playlist.hasDvr) {
                Text(
                    "Connected${playlist.dvrUsername?.let { " as $it" } ?: ""}. Recordings appear in Library, " +
                        "and you can record from a channel's guide.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = vm::disconnectDvr) { Text("Disconnect") }
            } else {
                Text(
                    "Sign in with your Dispatcharr web login (not the Xtream password) to watch and schedule recordings. " +
                        "Your user needs DVR access in Dispatcharr.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = { vm.clearDvrResult(); showDialog = true }) { Text("Connect") }
            }
        }
    }
    if (showDialog) {
        DvrLoginDialog(
            defaultUsername = playlist.dvrUsername ?: playlist.username,
            state = dvr,
            onConnect = vm::connectDvr,
            onDismiss = { showDialog = false },
        )
    }
    LaunchedEffect(dvr.connected) { if (dvr.connected) showDialog = false }
}

@Composable
private fun DvrLoginDialog(
    defaultUsername: String,
    state: DvrConnectState,
    onConnect: (username: String, password: String, apiKey: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf(defaultUsername) }
    var password by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var useKey by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        title = { Text("Connect to Dispatcharr") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (useKey) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Tellycine keeps only your API key, never the password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { useKey = !useKey }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (useKey) "Sign in with username and password instead" else "Use an API key instead")
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConnect(username, password, if (useKey) apiKey else "") },
                enabled = !state.busy && (if (useKey) apiKey.isNotBlank() else username.isNotBlank() && password.isNotEmpty()),
            ) { Text("Connect") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.busy) { Text("Cancel") } },
    )
}

/** User agent presets in a drop-down, plus a "Custom" entry that reveals a text field. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserAgentPicker(form: PlaylistForm, vm: EditPlaylistViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val preset = form.userAgentPreset
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = preset?.name ?: "Custom",
            onValueChange = {},
            readOnly = true,
            label = { Text("User agent") },
            supportingText = {
                Text(
                    if (preset == null) "Enter the user agent below."
                    else "Some providers only allow certain apps. Pick the app your provider expects, or Custom."
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            UserAgents.presets.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.name)
                            if (option.value.isNotEmpty()) {
                                Text(
                                    option.value,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        vm.update { it.copy(userAgentPreset = option) }
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Custom…") },
                onClick = {
                    expanded = false
                    // Start from the previous preset's value so it can be tweaked.
                    vm.update { it.copy(userAgentPreset = null, userAgent = it.userAgentPreset?.value ?: it.userAgent) }
                },
            )
        }
    }
    if (preset == null) {
        OutlinedTextField(
            value = form.userAgent,
            onValueChange = { v -> vm.update { it.copy(userAgent = v) } },
            label = { Text("Custom user agent") },
            supportingText = { Text("Sent with every request for this playlist. Leave empty for the default.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
