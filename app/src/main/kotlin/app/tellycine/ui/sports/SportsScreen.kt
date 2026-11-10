package app.tellycine.ui.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tellycine.cast.CastButton
import app.tellycine.core.sports.AiringKind
import app.tellycine.core.sports.SportCatalogue
import app.tellycine.core.sports.SportNames
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.sports.SportEvent
import app.tellycine.data.sports.SportSide
import app.tellycine.data.sports.SportSource
import app.tellycine.player.play
import app.tellycine.ui.components.Artwork
import app.tellycine.ui.components.EmptyState
import app.tellycine.ui.components.formatTime
import app.tellycine.ui.components.tellycineViewModel
import app.tellycine.ui.settings.SportsText

/** The Sports Hub: what's on now and coming up, of the sports and teams followed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportsScreen(onSearch: () -> Unit, onSetup: () -> Unit) {
    val vm = tellycineViewModel { c, _ -> SportsViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val indexing by vm.indexing.collectAsStateWithLifecycle()
    val opened by vm.opened.collectAsStateWithLifecycle()
    val now = state.now.takeIf { it > 0 } ?: System.currentTimeMillis()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sports", style = MaterialTheme.typography.titleLarge)
                        Text(
                            subtitle(state),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    CastButton()
                    IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Search") }
                    IconButton(onClick = onSetup) { Icon(Icons.Filled.Tune, "Your sports and teams") }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "filters") { FilterRow(state, vm::select) }
            if (state.ask && state.hasPlaylists) item(key = "ask") { AskCard(onChoose = onSetup, onLater = vm::asked) }
            when {
                !state.hasPlaylists -> item(key = "empty") {
                    EmptyState(
                        Icons.Filled.EmojiEvents,
                        "No playlist yet",
                        "Add a playlist with a TV guide from the Playlists tab, and the sport in it shows here.",
                        Modifier.heightIn(min = 360.dp),
                    )
                }
                !state.loaded -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().height(240.dp), Alignment.Center) { CircularProgressIndicator() }
                }
                !state.anyShown -> item(key = "empty") {
                    val status by vm.status.collectAsStateWithLifecycle()
                    NothingOn(state, indexing, SportsText.found(status, indexing))
                }
                else -> hubSections(state, now, vm::open)
            }
        }
    }

    opened?.let { event ->
        val settings by vm.settings.collectAsStateWithLifecycle()
        EventSheet(event, now, vm, followed = settings.sportsTeams, multiplePlaylists = state.multiplePlaylists, onDismiss = vm::close)
    }
}

private fun subtitle(state: SportsHubState): String = buildString {
    append(if (state.multiplePlaylists) "All playlists" else "Your playlist")
    if (state.liveCount > 0) append(" · ${state.liveCount} live now")
}

@Composable
private fun FilterRow(state: SportsHubState, onSelect: (SportsFilter) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.filters, key = { it.label }) { f ->
            FilterChip(
                selected = f == state.filter,
                onClick = { onSelect(f) },
                label = { Text(f.label, maxLines = 1) },
                leadingIcon = when (f) {
                    SportsFilter.ForYou -> { { Icon(Icons.Filled.Star, null, Modifier.size(18.dp)) } }
                    is SportsFilter.Only -> { { Icon(f.sport.icon, null, Modifier.size(18.dp)) } }
                    SportsFilter.All -> null
                },
            )
        }
    }
}

@Composable
private fun AskCard(onChoose: () -> Unit, onLater: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("What do you follow?", style = MaterialTheme.typography.titleMedium)
            Text(
                "Pick your sports and teams, and they come first here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onLater) { Text("Not now") }
                FilledTonalButton(onClick = onChoose, modifier = Modifier.padding(start = 8.dp)) { Text("Choose") }
            }
        }
    }
}

@Composable
private fun NothingOn(state: SportsHubState, indexing: Boolean, found: String) {
    val (title, message) = when {
        indexing && !state.anyEvents -> "Finding sport in your guides…" to "This takes a moment after a playlist or its TV guide is refreshed."
        !state.anyEvents -> "No sport found yet" to
            "Sport shows here from your playlists' TV guides, and from event channels' names. Guides that give programmes categories work best."
        else -> "Nothing on in ${state.filter.label}" to "Try All, or another sport. Events show here as far ahead as your TV guides go."
    }
    // What was found in each playlist, so an empty hub says why.
    EmptyState(Icons.Filled.EmojiEvents, title, if (found.isBlank()) message else "$message\n\n$found", Modifier.heightIn(min = 360.dp))
}

private fun LazyListScope.hubSections(state: SportsHubState, now: Long, onOpen: (SportEvent) -> Unit) {
    if (state.live.isNotEmpty()) {
        item(key = "live-title") { SectionTitle("Live now", live = true) }
        item(key = "live") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.live, key = { it.key }) { e -> LiveCard(e, now, onClick = { onOpen(e) }) }
            }
        }
    }
    if (state.teams.isNotEmpty()) {
        item(key = "teams-title") { SectionTitle("Your teams") }
        item(key = "teams") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.teams, key = { it.name }) { card -> TeamCardView(card, now, onClick = { card.next?.let(onOpen) }) }
            }
        }
    }
    if (state.days.isNotEmpty()) {
        item(key = "coming-title") { SectionTitle("Coming up") }
        for (day in state.days) {
            item(key = "day-${day.dayStartMs}") {
                Text(
                    dayLabel(day.dayStartMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp),
                )
            }
            items(day.events, key = { it.key }) { e -> UpcomingRow(e, onClick = { onOpen(e) }) }
        }
    }
}

@Composable
private fun SectionTitle(text: String, live: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (live) Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** "LIVE", in the error colours, as on the sketch's cards. */
@Composable
fun LivePill(modifier: Modifier = Modifier) {
    Text(
        "LIVE",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** A side's code on its colour, in a circle. */
@Composable
fun Monogram(side: SportSide, size: Dp = 28.dp) {
    val (background, text) = side.colours(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface)
    // Teams in black or navy would vanish on the dark background without an edge.
    val edge = if (side.colour != null && background.luminance() < 0.04f) Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape) else Modifier
    Box(
        Modifier.size(size).clip(CircleShape).background(background).then(edge).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            side.code,
            color = text,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.34f).sp,
            maxLines = 1,
        )
    }
}

/** A channel's logo, or its initials, on a small tile. */
@Composable
private fun ChannelBadge(stream: StreamEntity, width: Dp = 40.dp, height: Dp = 28.dp) {
    Artwork(
        url = stream.logo,
        fallbackText = stream.name,
        contentScale = ContentScale.Fit,
        shape = RoundedCornerShape(7.dp),
        modifier = Modifier.width(width).height(height),
    )
}

@Composable
private fun SideLine(side: SportSide) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Monogram(side)
        Text(side.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LiveCard(event: SportEvent, now: Long, onClick: () -> Unit) {
    val sources = event.sourcesAt(now)
    val first = sources.first()
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.width(280.dp).height(176.dp).semantics { contentDescription = "${event.title}, live, ${event.caption}" },
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(event.sport.icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    event.caption,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (event.isFollowed) Icon(Icons.Filled.Star, "Yours", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                LivePill()
            }
            val home = event.home
            val away = event.away
            if (home != null && away != null) {
                SideLine(home)
                SideLine(away)
            } else {
                Text(event.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChannelBadge(first.stream, 36.dp, 24.dp)
                Text(
                    first.stream.name + if (sources.size > 1) " · ${sources.size - 1} more" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(elapsed(event, now), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(
                progress = { progressOf(event, now) },
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                drawStopIndicator = {},
                gapSize = 0.dp,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun TeamCardView(card: TeamCard, now: Long, onClick: () -> Unit) {
    val next = card.next
    Card(
        onClick = onClick,
        enabled = next != null,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.width(196.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Monogram(card.side, 32.dp)
                Text(card.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (next == null) {
                Text("Nothing in your guides yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            } else {
                Text(
                    whenLabel(next, now),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (next.isLive(now)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Text(opponentOrTitle(card, next), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun UpcomingRow(event: SportEvent, onClick: () -> Unit) {
    val first = event.sources.first()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(formatTime(event.startMs), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 48.dp))
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(event.sport.icon, event.sport.label, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    event.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (event.isFollowed) FontWeight.SemiBold else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (event.isFollowed) Icon(Icons.Filled.Star, "Yours", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                listOfNotNull(event.caption, kindLabel(event.kind)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ChannelBadge(first.stream, 36.dp, 24.dp)
    }
}

/** "Replay" or "Highlights", for those shown with replays turned on. */
fun kindLabel(kind: AiringKind): String? = when (kind) {
    AiringKind.REPLAY -> "Replay"
    AiringKind.HIGHLIGHTS -> "Highlights"
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EventSheet(
    event: SportEvent,
    now: Long,
    vm: SportsViewModel,
    followed: List<String>,
    multiplePlaylists: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        // It scrolls, for small phones and landscape, and events on many channels.
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(event.sport.icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    listOfNotNull(event.caption, kindLabel(event.kind)).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (event.isLive(now)) LivePill()
            }
            val home = event.home
            val away = event.away
            if (home != null && away != null) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    BigSide(home, Modifier.weight(1f))
                    Text("v", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BigSide(away, Modifier.weight(1f))
                }
            } else {
                Text(event.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 14.dp))
            }
            Text(
                if (event.isLive(now)) "Started ${formatTime(event.startMs)} · ${elapsed(event, now)}"
                else listOfNotNull(startLabel(event.startMs, now), startsIn(event, now)).joinToString(" · "),
                style = MaterialTheme.typography.titleSmall,
                textAlign = if (home != null) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            if (event.isLive(now)) {
                LinearProgressIndicator(
                    progress = { progressOf(event, now) },
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    drawStopIndicator = {},
                gapSize = 0.dp,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                )
            }

            Text(
                "Watch on",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
            )
            for (source in event.sources) {
                SourceRow(source, now, multiplePlaylists) {
                    context.play(vm.playRequest(source, now))
                    onDismiss()
                }
            }
            val fromStart = event.sources.firstOrNull { vm.canWatchFromStart(it, now) }
            if (fromStart != null) {
                FilledTonalButton(
                    onClick = {
                        vm.fromStartRequest(fromStart)?.let(context::play)
                        onDismiss()
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(Icons.Filled.Replay, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Watch from the start")
                }
            }

            if (home != null && away != null) {
                HorizontalDivider(Modifier.padding(top = 16.dp, bottom = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (side in listOf(home, away)) {
                        val name = nameToFollow(side, event.sport)
                        val following = followedAs(name, followed)
                        FollowButton(name, following != null, onToggle = { if (following != null) vm.unfollow(following) else vm.follow(name) })
                    }
                }
            }
            Text(
                if (event.sources.all { it.airing.isEventChannel }) "From the channel's name. No scores are shown." else "From your TV guide. No scores are shown.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun BigSide(side: SportSide, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Monogram(side, 56.dp)
        Text(side.name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun SourceRow(source: SportSource, now: Long, multiplePlaylists: Boolean, onClick: () -> Unit) {
    val a = source.airing
    val on = source.isOn(now)
    val detail = listOfNotNull(
        when {
            a.isEventChannel -> "Event channel"
            on -> "On now"
            else -> "${formatTime(a.startMs)} – ${formatTime(a.stopMs)}"
        },
        source.playlist.name.takeIf { multiplePlaylists },
    ).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChannelBadge(source.stream, 48.dp, 32.dp)
        Column(Modifier.weight(1f)) {
            Text(source.stream.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Icon(Icons.Filled.PlayArrow, "Watch ${source.stream.name}", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun FollowButton(name: String, following: Boolean, onToggle: () -> Unit) {
    if (following) {
        FilledTonalButton(onClick = onToggle) {
            Icon(Icons.Filled.Star, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Following $name", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(onClick = onToggle) {
            Text("Follow $name", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
