package app.tellycine.ui.sports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tellycine.core.sports.Sport
import app.tellycine.core.sports.SportCatalogue
import app.tellycine.core.sports.SportNames
import app.tellycine.data.sports.SportSide
import app.tellycine.ui.components.ListItem
import app.tellycine.ui.components.tellycineViewModel

/** "What do you follow?": the sports and the teams and players the hub puts first. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SportsSetupScreen(onDone: () -> Unit) {
    val vm = tellycineViewModel { c, _ -> SportsViewModel(c) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val done = {
        vm.asked()
        onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("What do you follow?") },
                navigationIcon = { IconButton(onClick = done) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Text(
                "The Sports Hub finds these in your playlists' guides and channels, and puts them first. Change them any time in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            for (row in Sport.FOLLOWABLE.chunked(2)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (sport in row) {
                        SportTile(sport, sport in settings.sports, Modifier.weight(1f)) { vm.setSport(sport, it) }
                    }
                }
            }

            Text("Teams and players", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            Text(
                "Their events show first, under For you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val typed = query.trim()
            OutlinedTextField(
                value = query,
                onValueChange = vm::search,
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Search teams, drivers and players") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (typed.isNotEmpty()) {
                        vm.follow(suggestions.firstOrNull { SportNames.key(it) == SportNames.key(typed) } ?: typed)
                        vm.search("")
                    }
                }),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            if (typed.isNotEmpty()) {
                Column(Modifier.padding(top = 4.dp)) {
                    for (name in suggestions) {
                        val figure = SportCatalogue.find(name)
                        ListItem(
                            headlineContent = { Text(name) },
                            supportingContent = figure?.let { { Text(it.sport.label) } },
                            leadingContent = { Monogram(sideOf(name), 32.dp) },
                            trailingContent = { Icon(Icons.Filled.Add, "Follow $name") },
                            modifier = Modifier.clickable {
                                vm.follow(name)
                                vm.search("")
                            },
                        )
                    }
                    if (suggestions.none { SportNames.key(it) == SportNames.key(typed) }) {
                        ListItem(
                            headlineContent = { Text("Follow “$typed”") },
                            supportingContent = { Text("Found by name in titles and descriptions") },
                            leadingContent = { Icon(Icons.Filled.Add, null) },
                            modifier = Modifier.clickable {
                                vm.follow(typed)
                                vm.search("")
                            },
                        )
                    }
                }
            }
            if (settings.sportsTeams.isNotEmpty()) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (name in settings.sportsTeams) {
                        InputChip(
                            selected = false,
                            onClick = { vm.unfollow(name) },
                            label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            avatar = { Monogram(sideOf(name), InputChipDefaults.AvatarSize) },
                            trailingIcon = { Icon(Icons.Filled.Close, "Stop following $name", Modifier.size(InputChipDefaults.IconSize)) },
                        )
                    }
                }
            }
            Button(onClick = done, modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp)) { Text("Done") }
        }
    }
}

/** A followed name as a side, with its code and colour if Tellycine knows it. */
fun sideOf(name: String): SportSide {
    val figure = SportCatalogue.find(name)
    return SportSide(figure?.name ?: name, figure?.code ?: SportNames.monogram(name), figure?.colour)
}

@Composable
private fun SportTile(sport: Sport, selected: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(56.dp).toggleable(value = selected, role = Role.Checkbox, onValueChange = onChange),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(sport.icon, null, Modifier.size(22.dp))
            Text(sport.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Filled.CheckCircle, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
