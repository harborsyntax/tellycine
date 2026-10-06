package app.tube.ui.playlists

import android.content.Intent
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
import androidx.compose.material3.ListItem
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
import app.tube.AppContainer
import app.tube.core.xtream.XtreamUrls
import app.tube.data.db.PlaylistEntity
import app.tube.data.db.PlaylistType
import app.tube.ui.components.tubeViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val userAgent: String = "",
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

class EditPlaylistViewModel(private val container: AppContainer, private val editId: Long?) : ViewModel() {
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
                userAgent = existing.userAgent,
                vodEnabled = existing.vodEnabled,
            )
        }
    }

    fun update(transform: (PlaylistForm) -> PlaylistForm) = _form.update { transform(it).copy(error = null) }

    fun convertToXtream() {
        val (server, user, pass) = _form.value.detectedXtream ?: return
        update { it.copy(type = PlaylistType.XTREAM, server = server, username = user, password = pass) }
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
        if (error != null) {
            _form.update { it.copy(error = error) }
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
                    userAgent = f.userAgent.trim(),
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
    val vm = tubeViewModel(key = "edit-$editId") { c, _ -> EditPlaylistViewModel(c, editId) }
    val form by vm.form.collectAsStateWithLifecycle()
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
                        if (form.detectedXtream != null) {
                            AssistChip(
                                onClick = vm::convertToXtream,
                                label = { Text("This looks like an Xtream Codes link – use the API for movies & series details") },
                            )
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
                    OutlinedTextField(
                        value = form.userAgent,
                        onValueChange = { v -> vm.update { it.copy(userAgent = v) } },
                        label = { Text("User agent") },
                        supportingText = { Text("Only needed if your provider requires a specific one.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
