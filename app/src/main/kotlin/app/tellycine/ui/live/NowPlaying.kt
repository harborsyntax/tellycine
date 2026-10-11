package app.tellycine.ui.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.tellycine.data.db.ChannelRow
import app.tellycine.data.db.NowPlaying
import kotlinx.coroutines.delay

/** The time, updated at the start of each minute. */
@Composable
fun rememberMinuteClock(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            value = System.currentTimeMillis()
        }
    }
    return now
}

/**
 * What's on [row]'s channel at [now]: what the row was loaded with, until that programme ends, and
 * then the next, from [lookup]. Lists only compose the rows on screen, so only those look up
 * anything; the list itself doesn't reload as time passes.
 */
@Composable
fun rememberNowPlaying(row: ChannelRow, now: Long, lookup: suspend (ChannelRow) -> NowPlaying?): NowPlaying? {
    var playing by remember(row) { mutableStateOf(NowPlaying(row.stream.streamId, row.nowTitle, row.nowStart, row.nowStop)) }
    val stop = playing.nowStop
    // Without a guide there's nothing to look up; with one, look again once the programme ends
    // (or each minute, while the guide has a gap).
    val ended = row.stream.epgChannelId != null && (stop == null || now >= stop)
    LaunchedEffect(row, if (ended) now else null) {
        if (ended) lookup(row)?.let { playing = it }
    }
    return playing.takeIf { it.nowStop != null && now < it.nowStop }
}
