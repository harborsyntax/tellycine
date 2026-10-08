package app.tellycine.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tellycine.cast.CastButton
import app.tellycine.ui.components.TellycineLogo

/**
 * Top app bar for the main tabs: the Tellycine logo over the active playlist's name, which doubles
 * as a playlist switcher when more than one playlist exists. The tab itself is named by the
 * navigation bar. A thin progress bar shows background refreshes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistTopBar(
    session: SessionState,
    onSelectPlaylist: (Long) -> Unit,
    onSearch: (() -> Unit)?,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column {
        TopAppBar(
            title = {
                Column {
                    TellycineLogo(height = 26.dp)
                    val active = session.active
                    if (active != null) {
                        var open by remember { mutableStateOf(false) }
                        val switchable = session.playlists.size > 1
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .then(if (switchable) Modifier.clickable { open = true } else Modifier),
                        ) {
                            Text(
                                active.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                            if (switchable) {
                                Icon(Icons.Filled.ArrowDropDown, "Switch playlist", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            session.playlists.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.name) },
                                    trailingIcon = { if (p.id == active.id) Icon(Icons.Filled.Check, null) },
                                    onClick = {
                                        open = false
                                        onSelectPlaylist(p.id)
                                    },
                                )
                            }
                        }
                    }
                }
            },
            actions = {
                CastButton()
                if (onSearch != null) {
                    IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Search") }
                }
                actions()
            },
            scrollBehavior = scrollBehavior,
        )
        val progress = session.activeProgress
        if (progress != null) {
            val fraction = progress.fraction
            if (fraction != null) {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
