package app.mosaic.core

import app.mosaic.core.xmltv.XmltvChannel
import app.mosaic.core.xmltv.XmltvParser
import app.mosaic.core.xmltv.XmltvProgramme
import org.junit.Assert.assertEquals
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class XmltvParserTest {

    private val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE tv SYSTEM "xmltv.dtd">
        <tv generator-info-name="test">
          <channel id="bbc1.uk">
            <display-name lang="en">BBC One</display-name>
            <icon src="http://logo/bbc1.png"/>
          </channel>
          <programme start="20240131200000 +0000" stop="20240131210000 +0000" channel="bbc1.uk">
            <title lang="en">News &amp; Weather</title>
            <desc lang="en">The latest.</desc>
            <category>News</category>
            <credits><actor>Someone</actor></credits>
          </programme>
          <programme start="20240131210000 +0100" stop="20240131220000 +0100" channel="other">
            <title>Skipped</title>
          </programme>
          <programme start="20240131210000" stop="20240131213000" channel="bbc1.uk">
            <title><![CDATA[Film]]></title>
          </programme>
        </tv>
    """.trimIndent()

    @Test
    fun parsesChannelsAndFilteredProgrammes() {
        val channels = ArrayList<XmltvChannel>()
        val programmes = ArrayList<XmltvProgramme>()
        val gz = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(xml.toByteArray()) } }

        XmltvParser { KXmlParser() }.parse(
            ByteArrayInputStream(gz.toByteArray()).gunzipIfNeeded(),
            onChannel = { channels += it },
            acceptProgramme = { id, _, _ -> id == "bbc1.uk" },
        ) { programmes += it }

        assertEquals(1, channels.size)
        assertEquals(listOf("BBC One"), channels[0].displayNames)
        assertEquals("http://logo/bbc1.png", channels[0].icon)

        assertEquals(2, programmes.size)
        assertEquals("News & Weather", programmes[0].title)
        assertEquals("The latest.", programmes[0].description)
        assertEquals("News", programmes[0].category)
        assertEquals(1706731200000L, programmes[0].startMs)
        assertEquals("Film", programmes[1].title)
    }

    @Test
    fun parsesTimeVariants() {
        assertEquals(1706731200000L, XmltvParser.parseTime("20240131200000 +0000"))
        assertEquals(1706727600000L, XmltvParser.parseTime("20240131200000 +0100"))
        assertEquals(1706731200000L, XmltvParser.parseTime("202401312000"))
        assertEquals(1706749200000L, XmltvParser.parseTime("20240131200000-0500"))
        assertEquals(null, XmltvParser.parseTime("garbage"))
    }
}
