package app.tube.core

import app.tube.core.m3u.M3uEntry
import app.tube.core.m3u.M3uParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uParserTest {

    private fun parse(text: String): Pair<List<String>, List<M3uEntry>> {
        val entries = ArrayList<M3uEntry>()
        var epg = emptyList<String>()
        M3uParser().parse(text.reader().buffered(), onHeader = { epg = it.epgUrls }) { entries += it }
        return epg to entries
    }

    @Test
    fun parsesExtendedAttributesAndHeader() {
        val (epg, entries) = parse(
            """
            ﻿#EXTM3U url-tvg="http://epg.example/a.xml.gz,http://epg.example/b.xml" x-tvg-url="http://epg.example/a.xml.gz"
            #EXTINF:-1 tvg-id="bbc1.uk" catchup="default" catchup-source="?utc={utc}" catchup-days="5" tvg-name="BBC One" tvg-logo="http://logo/bbc1.png" group-title="UK, General",BBC One HD
            http://host/live/u/p/1.ts

            #EXTINF:0,Plain Channel
            #EXTGRP:Misc
            #EXTVLCOPT:http-user-agent=Kodi/20
            http://host/plain.m3u8
            #EXTINF:-1 tvg-id="" group-title="Movies",The Movie (2020)
            http://host/movie/u/p/55.mkv|User-Agent=VLC&Referer=http://ref
            """.trimIndent()
        )
        assertEquals(listOf("http://epg.example/a.xml.gz", "http://epg.example/b.xml"), epg)
        assertEquals(3, entries.size)

        val bbc = entries[0]
        assertEquals("BBC One HD", bbc.name)
        assertEquals("bbc1.uk", bbc.tvgId)
        assertEquals("BBC One", bbc.tvgName)
        assertEquals("http://logo/bbc1.png", bbc.logo)
        assertEquals("UK, General", bbc.group)
        assertEquals(ContentKind.LIVE, ContentKind.classify(bbc.url))
        assertEquals("default", bbc.catchupType)
        assertEquals("?utc={utc}", bbc.catchupSource)
        assertEquals(5, bbc.catchupDays)

        val plain = entries[1]
        assertEquals("Plain Channel", plain.name)
        assertEquals("Misc", plain.group)
        assertEquals("Kodi/20", plain.userAgent)

        val movie = entries[2]
        assertNull(movie.tvgId)
        assertEquals("http://host/movie/u/p/55.mkv", movie.url)
        assertEquals("VLC", movie.userAgent)
        assertEquals("http://ref", movie.referrer)
        assertEquals(ContentKind.MOVIE, ContentKind.classify(movie.url))
    }

    @Test
    fun handlesBareUrlsAndCrlf() {
        val (_, entries) = parse("#EXTM3U\r\nhttp://a/b/c.mp4\r\n#EXTINF:-1,Name, with comma\r\nhttp://x/y\r\n")
        assertEquals(2, entries.size)
        assertEquals("c.mp4", entries[0].name)
        assertEquals(ContentKind.MOVIE, ContentKind.classify(entries[0].url))
        assertEquals("Name, with comma", entries[1].name)
    }

    @Test
    fun classifiesSeries() {
        assertEquals(ContentKind.SERIES, ContentKind.classify("http://h:80/series/u/p/123.mkv"))
        assertEquals(ContentKind.LIVE, ContentKind.classify("http://h:80/u/p/123"))
    }
}
