package app.tellycine.data.sports

import app.tellycine.core.ContentKind
import app.tellycine.core.sports.AiringKind
import app.tellycine.core.sports.Sport
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import app.tellycine.data.db.SportAiringEntity
import app.tellycine.data.db.StreamEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/** Coverage UK sports broadcasters don't say is live, where they say it of their live coverage. */
class UnmarkedReplaysTest {
    private val playlist = PlaylistEntity(id = 1, name = "P", type = PlaylistType.M3U_URL, url = "")
    private var start = 0L

    private fun source(channel: String, title: String, kind: AiringKind, sport: Sport = Sport.FOOTBALL): SportSource {
        start += 3_600_000L
        val stream = StreamEntity(1, ContentKind.LIVE, channel, channel, 0, null, null, "https://e/$channel.ts", epgChannelId = channel)
        val airing = SportAiringEntity(1, channel, start, start + 3_600_000L, sport, kind, title, null, null, null, null, title, null, null)
        return SportSource(playlist, stream, airing)
    }

    private val tntLive = listOf(
        source("UK: TNT Sports 1 HD", "Live Emirates FA Cup", AiringKind.LIVE),
        source("UK: TNT Sports 2 HD", "Live: Cyclocross", AiringKind.LIVE, Sport.OTHER),
        source("UK: TNT Sports 1 HD", "Live: One-Day International Cricket", AiringKind.LIVE, Sport.OTHER),
    )

    @Test
    fun aBroadcastersUnmarkedCoverageIsShownAgain() {
        val replay = source("UK: TNT Sports 4 HD", "UEFA Champions League", AiringKind.EVENT)
        val itv = source("UK: ITV4 HD", "Snooker: Champion of Champions", AiringKind.EVENT, Sport.SNOOKER)
        val premier = source("UK: Premier Sports 1 HD", "LaLiga", AiringKind.EVENT)
        val sources = tntLive + listOf(replay, itv, premier)

        // Left out; or with replays, shown as one.
        assertEquals(tntLive + listOf(itv, premier), SportsRepository.unmarkedAsReplays(sources, replays = false))
        val withReplays = SportsRepository.unmarkedAsReplays(sources, replays = true)
        assertEquals(AiringKind.REPLAY, withReplays.single { it.stream.name == "UK: TNT Sports 4 HD" }.airing.kind)
        // ITV4 isn't a sports channel, and Premier Sports doesn't say "Live" here: as they were.
        assertEquals(AiringKind.EVENT, withReplays.single { it.stream.name == "UK: ITV4 HD" }.airing.kind)
        assertEquals(AiringKind.EVENT, withReplays.single { it.stream.name == "UK: Premier Sports 1 HD" }.airing.kind)
    }

    @Test
    fun aGuideThatNeverSaysLiveIsAsItWas() {
        val sources = listOf(
            source("TNT Sports 1", "Premier League", AiringKind.EVENT),
            source("TNT Sports 2", "Live: Cyclocross", AiringKind.LIVE, Sport.OTHER),
        )
        assertEquals(sources, SportsRepository.unmarkedAsReplays(sources, replays = false))
    }

    @Test
    fun broadcasters() {
        assertEquals("tnt", SportsRepository.broadcasterOf("UK: TNT Sports 4 HD"))
        assertEquals("tnt", SportsRepository.broadcasterOf("TNT Sports 1"))
        assertEquals("sky", SportsRepository.broadcasterOf("|UK| Sky Sports Main Event FHD"))
        assertEquals("premier", SportsRepository.broadcasterOf("[UK] Premier Sports 2"))
    }
}
