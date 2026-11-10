package app.tellycine.tv.sports

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.sports.SportEvent
import app.tellycine.data.sports.SportSide
import app.tellycine.data.sports.SportSource
import app.tellycine.player.play
import app.tellycine.tv.LocalTvMenuOpen
import app.tellycine.tv.TvPalette
import app.tellycine.tv.components.RequestFocusOnStart
import app.tellycine.tv.components.TvActionButton
import app.tellycine.tv.components.TvEmptyState
import app.tellycine.tv.components.TvHeader
import app.tellycine.tv.components.TvSidePanel
import app.tellycine.tv.components.TvSpacing
import app.tellycine.tv.components.okClicks
import app.tellycine.ui.components.Artwork
import app.tellycine.ui.components.formatTime
import app.tellycine.ui.sports.Monogram
import app.tellycine.ui.sports.SportsFilter
import app.tellycine.ui.sports.SportsHubState
import app.tellycine.ui.sports.SportsViewModel
import app.tellycine.ui.sports.TeamCard
import app.tellycine.ui.sports.dayLabel
import app.tellycine.ui.sports.elapsed
import app.tellycine.ui.sports.followedAs
import app.tellycine.ui.sports.icon
import app.tellycine.ui.sports.kindLabel
import app.tellycine.ui.sports.nameToFollow
import app.tellycine.ui.sports.opponentOrTitle
import app.tellycine.ui.sports.progressOf
import app.tellycine.ui.sports.startLabel
import app.tellycine.ui.sports.startsIn
import app.tellycine.ui.sports.whenLabel
import app.tellycine.ui.components.tellycineViewModel
import app.tellycine.ui.settings.SportsText

/**
 * The Sports Hub on TV: rows of what's live and coming up. OK on a live event plays it; on one
 * still to come, or held on a live one, it opens the event's panel. Back goes to the filters
 * first, then (as everywhere) the menu.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSportsScreen(onAddPlaylist: () -> Unit) {
    val vm = tellycineViewModel { c, _ -> SportsViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val indexing by vm.indexing.collectAsStateWithLifecycle()
    val opened by vm.opened.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val now = state.now.takeIf { it > 0 } ?: System.currentTimeMillis()
    var follow by remember { mutableStateOf(false) }

    val chips = remember { FocusRequester() }
    val first = remember { FocusRequester() }
    var inChips by remember { mutableStateOf(false) }
    // Back from the rows goes to the filters; from the filters, to the menu.
    BackHandler(enabled = !inChips && opened == null && !follow && !LocalTvMenuOpen.current) { runCatching { chips.requestFocus() } }

    val open = { e: SportEvent -> vm.open(e) }
    val play = { e: SportEvent ->
        val request = vm.quickPlay(e, System.currentTimeMillis())
        if (request != null) context.play(request) else vm.open(e)
    }

    Column(Modifier.fillMaxSize()) {
        TvHeader(
            title = "Sports",
            subtitle = (if (state.multiplePlaylists) "All playlists" else "Your playlist") + if (state.liveCount > 0) " · ${state.liveCount} live now" else "",
            actions = { TvActionButton("Your sports", onClick = { follow = true }, icon = Icons.Filled.Tune) },
        )
        LazyRow(
            contentPadding = PaddingValues(start = TvSpacing.screenStart, end = TvSpacing.screenEnd, top = 2.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.focusRestorer().onFocusChanged { inChips = it.hasFocus },
        ) {
            itemsIndexed(state.filters, key = { _, f -> f.label }) { _, f ->
                FilterChip(
                    selected = f == state.filter,
                    onClick = { vm.select(f) },
                    leadingIcon = when (f) {
                        SportsFilter.ForYou -> { { Icon(Icons.Filled.Star, null, Modifier.size(18.dp)) } }
                        is SportsFilter.Only -> { { Icon(f.sport.icon, null, Modifier.size(18.dp)) } }
                        SportsFilter.All -> null
                    },
                    modifier = if (f == state.filter) Modifier.focusRequester(chips) else Modifier,
                ) { Text(f.label) }
            }
        }
        when {
            !state.hasPlaylists -> TvEmptyState(
                Icons.Filled.EmojiEvents,
                "No playlist yet",
                "Add a playlist with a TV guide, and the sport in it shows here.",
                actionLabel = "Add a playlist",
                onAction = onAddPlaylist,
            )
            !state.loaded -> {
                val status by vm.status.collectAsStateWithLifecycle()
                TvEmptyState(Icons.Filled.EmojiEvents, "Loading the Sports Hub…", SportsText.found(status, indexing))
            }
            !state.anyShown -> {
                val status by vm.status.collectAsStateWithLifecycle()
                val found = SportsText.found(status, indexing)
                val (title, message) = when {
                    indexing && !state.anyEvents -> "Finding sport in your guides…" to "This takes a moment after a playlist or its TV guide is refreshed."
                    !state.anyEvents -> "No sport found yet" to
                        "Sport shows here from your playlists' TV guides, and from event channels' names. Guides that give programmes categories work best."
                    else -> "Nothing on in ${state.filter.label}" to "Try All, or another sport. Events show here as far ahead as your TV guides go."
                }
                TvEmptyState(Icons.Filled.EmojiEvents, title, if (found.isBlank()) message else "$message\n\n$found")
                RequestFocusOnStart(chips)
            }
            else -> {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = TvSpacing.screenBottom),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rows(state, now, first, play, open)
                }
                RequestFocusOnStart(first, key = state.filter)
            }
        }
    }

    opened?.let { event ->
        TvEventPanel(event, now, vm, followed = settings.sportsTeams, multiplePlaylists = state.multiplePlaylists, onDismiss = vm::close)
    }
    if (follow) TvFollowPanel(vm, onDismiss = { follow = false })
}

private fun LazyListScope.rows(
    state: SportsHubState,
    now: Long,
    first: FocusRequester,
    play: (SportEvent) -> Unit,
    open: (SportEvent) -> Unit,
) {
    var firstTaken = false
    fun firstModifier(): Modifier = if (firstTaken) Modifier else Modifier.focusRequester(first).also { firstTaken = true }

    if (state.live.isNotEmpty()) {
        val focus = firstModifier()
        item(key = "live") {
            RowTitle("Live now", live = true)
            CardRow {
                itemsIndexed(state.live, key = { _, e -> e.key }) { i, e ->
                    TvLiveCard(e, now, onClick = { play(e) }, onLongClick = { open(e) }, modifier = if (i == 0) focus else Modifier)
                }
            }
        }
    }
    if (state.teams.isNotEmpty()) {
        val focus = firstModifier()
        item(key = "teams") {
            RowTitle("Your teams")
            CardRow {
                itemsIndexed(state.teams, key = { _, t -> t.name }) { i, t ->
                    TvTeamCard(t, now, onClick = { t.next?.let(open) }, modifier = if (i == 0) focus else Modifier)
                }
            }
        }
    }
    for (day in state.days) {
        val focus = firstModifier()
        item(key = "day-${day.dayStartMs}") {
            RowTitle(if (day == state.days.first()) "Coming up · ${dayLabel(day.dayStartMs)}" else dayLabel(day.dayStartMs))
            CardRow {
                itemsIndexed(day.events, key = { _, e -> e.key }) { i, e ->
                    TvUpcomingCard(e, onClick = { open(e) }, modifier = if (i == 0) focus else Modifier)
                }
            }
        }
    }
}

@Composable
private fun RowTitle(text: String, live: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(start = TvSpacing.screenStart, end = TvSpacing.screenEnd, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (live) Box(Modifier.size(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun CardRow(content: LazyListScope.() -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(start = TvSpacing.screenStart, end = TvSpacing.screenEnd, top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.focusRestorer(),
        content = content,
    )
}

@Composable
private fun cardColors(container: androidx.compose.ui.graphics.Color = TvPalette.surfaceHigh) = CardDefaults.colors(
    containerColor = container,
    contentColor = TvPalette.onSurface,
    focusedContainerColor = TvPalette.focus,
    focusedContentColor = TvPalette.onFocus,
)

private val CardShape = RoundedCornerShape(16.dp)

@Composable
private fun LivePill() {
    Text(
        "LIVE",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun ChannelTile(stream: StreamEntity, width: Dp = 40.dp, height: Dp = 28.dp) {
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
        Monogram(side, 32.dp)
        Text(side.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TvLiveCard(event: SportEvent, now: Long, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val sources = event.sourcesAt(now)
    val first = sources.first()
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(CardShape),
        colors = cardColors(),
        scale = CardDefaults.scale(focusedScale = 1.04f),
        border = CardDefaults.border(focusedBorder = Border.None),
        modifier = modifier.width(340.dp).height(188.dp)
            .semantics { contentDescription = "${event.title}, live on ${first.stream.name}" }
            .okClicks(onClick, onLongClick),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(event.sport.icon, null, Modifier.size(18.dp))
                Text(event.caption, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (event.isFollowed) Icon(Icons.Filled.Star, "Yours", Modifier.size(16.dp))
                LivePill()
            }
            val home = event.home
            val away = event.away
            if (home != null && away != null) {
                SideLine(home)
                SideLine(away)
            } else {
                Text(event.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChannelTile(first.stream)
                Text(
                    first.stream.name + if (sources.size > 1) " · ${sources.size - 1} more" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(elapsed(event, now), style = MaterialTheme.typography.labelMedium)
            }
            LinearProgressIndicator(
                progress = { progressOf(event, now) },
                color = TvPalette.primary,
                trackColor = TvPalette.surfaceHighest,
                drawStopIndicator = {},
                gapSize = 0.dp,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun TvUpcomingCard(event: SportEvent, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val first = event.sources.first()
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(CardShape),
        colors = cardColors(TvPalette.surface),
        scale = CardDefaults.scale(focusedScale = 1.04f),
        border = CardDefaults.border(focusedBorder = Border.None),
        modifier = modifier.width(258.dp).height(118.dp).semantics { contentDescription = "${formatTime(event.startMs)}, ${event.title}" },
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(formatTime(event.startMs), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Icon(event.sport.icon, event.sport.label, Modifier.size(20.dp))
                Spacer(Modifier.weight(1f))
                if (event.isFollowed) Icon(Icons.Filled.Star, "Yours", Modifier.size(16.dp))
            }
            Text(event.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(event.caption, kindLabel(event.kind), first.stream.name).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TvTeamCard(card: TeamCard, now: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val next = card.next
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(CardShape),
        colors = cardColors(TvPalette.surface),
        scale = CardDefaults.scale(focusedScale = 1.04f),
        border = CardDefaults.border(focusedBorder = Border.None),
        modifier = modifier.width(258.dp).height(118.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Monogram(card.side, 32.dp)
                Text(card.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (next == null) {
                Text("Nothing in your guides yet", style = MaterialTheme.typography.labelMedium, maxLines = 2)
            } else {
                Text(whenLabel(next, now), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(opponentOrTitle(card, next), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** An event in full, over the right of the hub: where to watch it, from the start, and following its sides. */
@Composable
private fun TvEventPanel(
    event: SportEvent,
    now: Long,
    vm: SportsViewModel,
    followed: List<String>,
    multiplePlaylists: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    TvSidePanel(onDismiss = onDismiss, width = 520.dp) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(event.sport.icon, null, Modifier.size(20.dp), tint = TvPalette.primary)
                    Text(
                        listOfNotNull(event.caption, kindLabel(event.kind)).joinToString(" · "),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (event.isLive(now)) LivePill()
                }
            }
            item {
                val home = event.home
                val away = event.away
                if (home != null && away != null) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        BigSide(home, Modifier.weight(1f))
                        Text("v", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        BigSide(away, Modifier.weight(1f))
                    }
                } else {
                    Text(event.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 14.dp))
                }
            }
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (event.isLive(now)) {
                        Text("Started ${formatTime(event.startMs)} · ${elapsed(event, now)}", style = MaterialTheme.typography.titleMedium)
                    } else {
                        Text(startLabel(event.startMs, now), style = MaterialTheme.typography.titleLarge)
                        startsIn(event, now)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            item {
                Text(
                    "Watch on",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
                )
            }
            itemsIndexed(event.sources, key = { _, s -> "${s.playlist.id}:${s.stream.streamId}:${s.airing.startMs}" }) { i, source ->
                SourceItem(source, now, multiplePlaylists, if (i == 0) Modifier.focusRequester(focus) else Modifier) {
                    context.play(vm.playRequest(source, now))
                    onDismiss()
                }
            }
            event.sources.firstOrNull { vm.canWatchFromStart(it, now) }?.let { source ->
                item {
                    ListItem(
                        selected = false,
                        onClick = {
                            vm.fromStartRequest(source)?.let(context::play)
                            onDismiss()
                        },
                        headlineContent = { Text("Watch from the start") },
                        supportingContent = { Text("Catch-up on ${source.stream.name}") },
                        leadingContent = { Icon(Icons.Filled.Replay, null) },
                    )
                }
            }
            val home = event.home
            val away = event.away
            if (home != null && away != null) {
                for (side in listOf(home, away)) {
                    item(key = "follow-${side.name}") {
                        val name = nameToFollow(side, event.sport)
                        val following = followedAs(name, followed)
                        ListItem(
                            selected = false,
                            onClick = { if (following != null) vm.unfollow(following) else vm.follow(name) },
                            headlineContent = { Text("Follow $name") },
                            supportingContent = { Text("Their events come first, under For you") },
                            leadingContent = { Monogram(side, 28.dp) },
                            trailingContent = { Switch(checked = following != null, onCheckedChange = null) },
                        )
                    }
                }
            }
        }
        Text(
            "Back returns to the hub · " + if (event.sources.all { it.airing.isEventChannel }) "From the channel's name" else "From your TV guide",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
    RequestFocusOnStart(focus, key = event.key)
}

@Composable
private fun BigSide(side: SportSide, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Monogram(side, 72.dp)
        Text(side.name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun SourceItem(source: SportSource, now: Long, multiplePlaylists: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val a = source.airing
    val detail = listOfNotNull(
        when {
            a.isEventChannel -> "Event channel"
            source.isOn(now) -> "On now"
            else -> "${formatTime(a.startMs)} – ${formatTime(a.stopMs)}"
        },
        source.playlist.name.takeIf { multiplePlaylists },
    ).joinToString(" · ")
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text("Watch on ${source.stream.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(detail, maxLines = 1) },
        leadingContent = { ChannelTile(source.stream, 48.dp, 32.dp) },
        trailingContent = { Icon(Icons.Filled.PlayArrow, null) },
        modifier = modifier,
    )
}
