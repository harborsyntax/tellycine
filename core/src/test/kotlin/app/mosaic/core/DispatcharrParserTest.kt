package app.mosaic.core

import app.mosaic.core.dispatcharr.DispatcharrParser
import app.mosaic.core.dispatcharr.DvrRecording.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DispatcharrParserTest {

    private val json = """[
      {"id": 7, "channel": 12, "start_time": "2026-10-06T20:00:00Z", "end_time": "2026-10-06T21:00:00+00:00",
       "task_id": "abc", "custom_properties": {"status": "completed", "poster_logo_id": 5,
         "program": {"title": "News", "sub_title": "Evening", "description": "Headlines"}}},
      {"id": 8, "channel": "13", "start_time": "2026-10-07T18:30:00.123456Z", "end_time": "2026-10-07T19:00:00Z",
       "custom_properties": {}},
      {"id": "bad"}
    ]"""

    @Test
    fun parsesRecordings() {
        val list = DispatcharrParser.parseRecordings(json.reader())
        assertEquals(2, list.size)
        val news = list[0]
        assertEquals(7L, news.id)
        assertEquals(12L, news.channelId)
        assertEquals(1791316800000L, news.startMs)
        assertEquals("News", news.title)
        assertEquals("Evening", news.subtitle)
        assertEquals(5L, news.posterLogoId)
        assertEquals(State.COMPLETED, news.state(0))
        val upcoming = list[1]
        assertEquals("Recording", upcoming.title)
        assertEquals(13L, upcoming.channelId)
        assertEquals(State.SCHEDULED, upcoming.state(upcoming.startMs - 1))
        assertEquals(State.RECORDING, upcoming.state(upcoming.startMs + 1))
        assertEquals(State.COMPLETED, upcoming.state(upcoming.endMs + 1))
    }

    @Test
    fun parsesPaginatedAndAuthResponses() {
        assertEquals(2, DispatcharrParser.parseRecordings("""{"count":2,"results":$json}""".reader()).size)
        assertEquals("tok", DispatcharrParser.parseAccessToken("""{"refresh":"r","access":"tok"}""".reader()))
        assertEquals("k1", DispatcharrParser.parseApiKey("""{"key":"k1"}""".reader()))
        assertNull(DispatcharrParser.parseApiKey("""{"key":null}""".reader()))
        assertEquals("Not allowed", DispatcharrParser.parseError("""{"detail":"Not allowed"}"""))
        assertEquals("End time must be in the future.", DispatcharrParser.parseError("""{"non_field_errors":["End time must be in the future."]}"""))
    }

    @Test
    fun buildsRecordingRequest() {
        val body = DispatcharrParser.recordingRequest(12, 1791316800000L, 1791320400000L, "News", null)
        assertEquals(
            """{"channel":"12","start_time":"2026-10-06T20:00:00Z","end_time":"2026-10-06T21:00:00Z",""" +
                """"custom_properties":{"program":{"title":"News","start_time":"2026-10-06T20:00:00Z","end_time":"2026-10-06T21:00:00Z"}}}""",
            body,
        )
    }
}
