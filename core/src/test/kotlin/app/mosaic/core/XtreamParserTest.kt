package app.mosaic.core

import app.mosaic.core.xtream.XtreamCategory
import app.mosaic.core.xtream.XtreamItem
import app.mosaic.core.xtream.XtreamParser
import app.mosaic.core.xtream.XtreamUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamParserTest {

    private val parser = XtreamParser()

    @Test
    fun parsesAccount() {
        val account = parser.parseAccount(
            """{"user_info":{"username":"u","auth":1,"status":"Active","exp_date":"1767225600",
               "max_connections":"2","active_cons":0,"allowed_output_formats":["m3u8","ts"]},
               "server_info":{"timezone":"Europe/London"}}""".reader()
        )
        assertTrue(account.authenticated)
        assertEquals(1767225600000L, account.expiresAtMs)
        assertEquals(2, account.maxConnections)
        assertEquals(listOf("m3u8", "ts"), account.allowedOutputFormats)
    }

    @Test
    fun parsesLooselyTypedStreams() {
        val items = ArrayList<XtreamItem>()
        parser.parseItems(
            """[
              {"num":1,"name":"Chan 1","stream_type":"live","stream_id":101,"stream_icon":"","epg_channel_id":"c1",
               "added":"1700000000","category_id":"5","tv_archive":1,"tv_archive_duration":"7"},
              {"num":"2","name":"Movie","stream_id":"202","stream_icon":"http://i","rating_5based":4.5,
               "container_extension":"mkv","category_id":null,"tv_archive":"0"},
              {"name":"Series","series_id":303,"cover":"http://c","plot":"p","releaseDate":"2019-02-02",
               "backdrop_path":["http://b"]}
            ]""".reader()
        ) { items += it }

        assertEquals(3, items.size)
        assertEquals("101", items[0].id)
        assertNull(items[0].icon)
        assertEquals(7, items[0].catchupDays)
        assertEquals(1700000000000L, items[0].addedMs)
        assertEquals(9.0, items[1].rating!!, 0.001)
        assertNull(items[1].categoryId)
        assertNull(items[1].catchupDays)
        assertEquals("303", items[2].id)
        assertEquals("2019", items[2].year)
        assertEquals("http://b", items[2].backdrop)
    }

    @Test
    fun parsesCategoriesFromObjectForm() {
        val cats = ArrayList<XtreamCategory>()
        parser.parseCategories("""{"a":{"category_id":"1","category_name":"News","parent_id":0}}""".reader()) { cats += it }
        assertEquals(listOf(XtreamCategory("1", "News", null)), cats)
    }

    @Test
    fun parsesSeriesInfo() {
        val info = parser.parseSeriesInfo(
            """{"seasons":[{"season_number":1,"name":"Season 1","cover":"http://s1"}],
                "info":{"name":"Show","plot":"Plot","rating":"8,5"},
                "episodes":{"1":[{"id":"9001","episode_num":2,"title":"Two","container_extension":"mp4","info":{"duration_secs":1500}},
                                  {"id":"9000","episode_num":1,"title":"One","container_extension":"mp4","info":[]}],
                            "2":[{"id":"9100","episode_num":"1","title":"S2E1","season":2}]}}""".reader()
        )
        assertEquals("Show", info.name)
        assertEquals(8.5, info.rating!!, 0.001)
        assertEquals(listOf("9000", "9001", "9100"), info.episodes.map { it.id })
        assertEquals(1500, info.episodes[1].durationSecs)
        assertEquals(setOf(1, 2), info.seasons.keys)
        assertEquals("Season 1", info.seasons[1]!!.name)
    }

    @Test
    fun buildsUrls() {
        val urls = XtreamUrls("example.com:8080/player_api.php?x=1", "us er", "p@ss")
        assertEquals("http://example.com:8080", urls.baseUrl)
        assertEquals("http://example.com:8080/live/us%20er/p%40ss/1.m3u8", urls.live("1", "m3u8"))
        assertEquals(
            Triple("http://h:80", "a", "b"),
            XtreamUrls.fromM3uUrl("http://h:80/get.php?username=a&password=b&type=m3u_plus&output=ts"),
        )
        assertEquals("https://h", XtreamUrls.normaliseServer("https://h/"))
    }
}
