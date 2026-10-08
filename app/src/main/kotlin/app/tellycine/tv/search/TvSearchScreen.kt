package app.tellycine.tv.search

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.foundation.BorderStroke
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.repo.ProgrammeHit
import app.tellycine.player.play
import app.tellycine.tv.TvPalette
import app.tellycine.tv.components.RequestFocusOnStart
import app.tellycine.tv.components.TvActionButton
import app.tellycine.tv.components.TvChannelTile
import app.tellycine.tv.components.TvClock
import app.tellycine.tv.components.TvEmptyState
import app.tellycine.tv.components.TvPosterCard
import app.tellycine.tv.components.TvSpacing
import app.tellycine.tv.components.TvTextInputDialog
import app.tellycine.ui.components.Artwork
import app.tellycine.ui.components.formatDay
import app.tellycine.ui.components.formatTime
import app.tellycine.ui.components.tellycineViewModel
import app.tellycine.ui.search.SearchFilter
import app.tellycine.ui.search.SearchViewModel

/** Search channels, the TV guide, movies and series; type with the on-screen keyboard or speak. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSearchScreen(onOpen: (StreamEntity) -> Unit) {
    val vm = tellycineViewModel { c, _ -> SearchViewModel(c) }
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val playlist by vm.playlist.collectAsStateWithLifecycle()
    val vodEnabled = playlist?.vodEnabled != false
    val context = LocalContext.current
    var typing by rememberSaveable { mutableStateOf(false) }
    val box = remember { FocusRequester() }

    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Search channels, programmes, movies and series")
    }
    val canSpeak = remember { speechIntent.resolveActivity(context.packageManager) != null }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(vm::setQuery)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = TvSpacing.screenStart, end = TvSpacing.screenEnd, top = TvSpacing.screenTop, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // The search box: OK opens the keyboard.
            Surface(
                onClick = { typing = true },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(28.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = TvPalette.surfaceHigh,
                    focusedContainerColor = TvPalette.surfaceHighest,
                ),
                border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, TvPalette.focus), shape = RoundedCornerShape(28.dp))),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                modifier = Modifier.weight(1f).focusRequester(box),
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.Search, null)
                    Text(
                        query.ifEmpty { if (vodEnabled) "Search channels, TV guide, movies and series" else "Search channels and TV guide" },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (query.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (canSpeak) {
                TvActionButton("Voice", onClick = {
                    try {
                        voice.launch(speechIntent)
                    } catch (_: ActivityNotFoundException) {
                    }
                }, icon = Icons.Filled.Mic)
            }
            TvClock()
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = TvSpacing.screenStart),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.focusRestorer(),
        ) {
            items(SearchFilter.entries.filter { vodEnabled || !it.needsVod }, key = { it.name }) { option ->
                FilterChip(selected = filter == option, onClick = { vm.setFilter(option) }) { Text(option.label) }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            // Until the (debounced) search catches up with the query, it counts as searching.
            val pending = results.searching || results.query != query.trim()
            when {
                query.trim().length < 2 -> TvEmptyState(Icons.Filled.Search, "Search your playlist", "Press OK on the search box to type, or use Voice.")
                pending && results.isEmpty -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                !results.searching && results.isEmpty -> TvEmptyState(Icons.Filled.Search, "No results", "Nothing matches \"${results.query}\".")
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 16.dp, bottom = TvSpacing.screenBottom),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    resultRow("Live TV", results.live) { s ->
                        TvChannelTile(name = s.name, logo = s.logo, onClick = { context.play(vm.channelRequest(s)) })
                    }
                    resultRow("TV guide", results.programmes) { hit ->
                        ProgrammeCard(hit, replayable = vm.canReplay(hit), onClick = { context.play(vm.programmeRequest(hit)) })
                    }
                    resultRow("Movies", results.movies) { s ->
                        TvPosterCard(title = s.name, poster = s.logo, meta = s.year, rating = s.rating, onClick = { onOpen(s) }, modifier = Modifier.width(130.dp))
                    }
                    resultRow("Series", results.series) { s ->
                        TvPosterCard(title = s.name, poster = s.logo, meta = s.year, rating = s.rating, onClick = { onOpen(s) }, modifier = Modifier.width(130.dp))
                    }
                }
            }
        }
    }
    RequestFocusOnStart(box)

    if (typing) {
        TvTextInputDialog(
            title = "Search",
            initial = query,
            placeholder = "Channel, programme, movie or series",
            onDone = {
                vm.setQuery(it)
                typing = false
            },
            onDismiss = { typing = false },
        )
    }
}

private fun <T> LazyListScope.resultRow(title: String, list: List<T>, card: @Composable (T) -> Unit) {
    if (list.isEmpty()) return
    item(key = "row-$title") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = TvSpacing.screenStart))
            LazyRow(
                contentPadding = PaddingValues(start = TvSpacing.screenStart, end = TvSpacing.screenEnd, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.focusRestorer(),
            ) {
                items(list.size) { i -> card(list[i]) }
            }
        }
    }
}

/** A programme from the guide: its channel, time and title. */
@Composable
private fun ProgrammeCard(hit: ProgrammeHit, replayable: Boolean, onClick: () -> Unit) {
    val p = hit.programme
    val now = remember(hit) { System.currentTimeMillis() }
    val onNow = p.startMs <= now && p.stopMs > now
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (onNow) androidx.compose.ui.graphics.Color(0xFF56442A) else TvPalette.surfaceHigh,
            focusedContainerColor = TvPalette.focus,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = Modifier.width(300.dp),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(
                url = hit.channel.logo,
                fallbackText = hit.channel.name,
                contentScale = ContentScale.Fit,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.size(width = 56.dp, height = 40.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    if (onNow) "On now  ·  ${hit.channel.name}" else "${formatDay(p.startMs)} ${formatTime(p.startMs)}  ·  ${hit.channel.name}",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(p.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (replayable && !onNow && p.stopMs <= now) {
                    Text("Catch-up available", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}
