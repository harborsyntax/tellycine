package app.tellycine.tv.sports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.tellycine.core.sports.Sport
import app.tellycine.core.sports.SportCatalogue
import app.tellycine.tv.components.RequestFocusOnStart
import app.tellycine.tv.components.TvCheckboxRow
import app.tellycine.tv.components.TvSectionLabel
import app.tellycine.tv.components.TvSidePanel
import app.tellycine.tv.components.TvTextInputDialog
import app.tellycine.ui.sports.Monogram
import app.tellycine.ui.sports.SportsViewModel
import app.tellycine.ui.sports.sideOf

/** "What do you follow?" on TV: the sports, and the teams and players, the hub puts first. */
@Composable
fun TvFollowPanel(vm: SportsViewModel, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val done = {
        vm.asked()
        onDismiss()
    }

    TvSidePanel(onDismiss = done, width = 520.dp) {
        Text("What do you follow?", style = MaterialTheme.typography.headlineSmall)
        Text(
            "They come first in the Sports Hub, under For you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item { TvSectionLabel("Sports") }
            items(Sport.FOLLOWABLE, key = { it.name }) { sport ->
                TvCheckboxRow(
                    title = sport.label,
                    summary = null,
                    checked = sport in settings.sports,
                    onChange = { vm.setSport(sport, it) },
                    modifier = if (sport == Sport.FOLLOWABLE.first()) Modifier.focusRequester(first) else Modifier,
                )
            }
            item { TvSectionLabel("Teams and players") }
            item {
                ListItem(
                    selected = false,
                    onClick = { adding = true },
                    headlineContent = { Text("Add a team or player") },
                    leadingContent = { Icon(Icons.Filled.Add, null) },
                )
            }
            items(settings.sportsTeams, key = { "team-$it" }) { name ->
                ListItem(
                    selected = false,
                    onClick = { vm.unfollow(name) },
                    headlineContent = { Text(name) },
                    supportingContent = { Text(SportCatalogue.find(name)?.sport?.label ?: "Found by name") },
                    leadingContent = { Monogram(sideOf(name), 28.dp) },
                    trailingContent = { Icon(Icons.Filled.Close, "Stop following $name") },
                )
            }
        }
    }
    RequestFocusOnStart(first)

    if (adding) {
        TvTextInputDialog(
            title = "Add a team or player",
            initial = "",
            hint = "Such as Arsenal, McLaren or Ronnie O'Sullivan. Their events come first, under For you.",
            placeholder = "Team, driver or player",
            onDone = { typed ->
                adding = false
                val name = typed.trim()
                // By the name Tellycine knows it by, when it knows it ("Spurs" is Tottenham Hotspur).
                if (name.isNotEmpty()) vm.follow(SportCatalogue.find(name)?.name ?: name)
            },
            onDismiss = { adding = false },
        )
    }
}
