package com.subtracks.data.source.subsonic

import com.subtracks.data.lyrics.Lyrics
import com.subtracks.data.lyrics.LyricsLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.FilterInputStream

class SubsonicLyricsXmlTest {
    private fun read(body: String): List<Lyrics> =
        SubsonicXml.lyrics(
            ("<subsonic-response status=\"ok\">" + body + "</subsonic-response>").byteInputStream(),
        )

    @Test
    fun structuredLyricsPreserveLanguagesOffsetAndStableTimingOrder() {
        val parsed =
            read(
                """
                <lyricsList>
                  <structuredLyrics lang="eng" synced="true" offset="-125">
                    <line start="2100">Later</line>
                    <line start="1000">First &amp; second</line>
                    <line start="1000"><![CDATA[Also <here>]]></line>
                  </structuredLyrics>
                  <structuredLyrics lang="NOR" synced="false" offset="250">
                    <line start="200">Blåbær</line><line>Igjen</line>
                  </structuredLyrics>
                </lyricsList>
                """.trimIndent(),
            )
        assertEquals(
            listOf(
                Lyrics(
                    listOf(LyricsLine("First & second", 1000), LyricsLine("Also <here>", 1000), LyricsLine("Later", 2100)),
                    synced = true,
                    language = "eng",
                    offsetMs = -125,
                ),
                Lyrics(listOf(LyricsLine("Blåbær"), LyricsLine("Igjen")), synced = false, language = "nor", offsetMs = 250),
            ),
            parsed,
        )
    }

    @Test
    fun rejectsInvalidTimedLinesAndNormalizesUnknownLanguage() {
        val parsed =
            read(
                """
                <lyricsList>
                  <structuredLyrics lang="xxx" synced="1" offset="bad">
                    <line start="-1">Negative</line><line start="oops">Bad</line>
                    <line>Missing</line><line start="9223372036854775808">Overflow</line>
                    <line start="1"> </line><line start="0">Valid</line>
                  </structuredLyrics>
                  <structuredLyrics synced="true"><line>No time</line></structuredLyrics>
                  <structuredLyrics synced="false"><line> </line></structuredLyrics>
                </lyricsList>
                """.trimIndent(),
            )
        assertEquals(listOf(Lyrics(listOf(LyricsLine("Valid", 0)), synced = true)), parsed)
        assertEquals(emptyList<Lyrics>(), read("<lyricsList/>"))
        assertEquals(emptyList<Lyrics>(), read("<unexpected><line>Ignored</line></unexpected>"))
    }

    @Test
    fun classicLyricsCanBePlainOrTimed() {
        assertEquals(
            listOf(Lyrics(listOf(LyricsLine("夜 & stars"), LyricsLine("Again")), synced = false)),
            read("<lyrics artist=\"Someone\" title=\"Example\">  夜 &amp; stars\r\n\nAgain </lyrics>"),
        )
        assertEquals(
            listOf(Lyrics(listOf(LyricsLine("Timed", 2500)), synced = true, offsetMs = 40)),
            read("<lyrics><![CDATA[[offset:+40]\n[00:02.50]Timed]]></lyrics>"),
        )
        assertEquals(emptyList<Lyrics>(), read("<lyrics/>"))
    }

    @Test
    fun fragmentedSaxTextIsJoinedBeforeCreatingLines() {
        val value = "夜🎵".repeat(1000)
        val xml =
            "<subsonic-response status=\"ok\"><lyricsList><structuredLyrics synced=\"true\">" +
                "<line start=\"120\">$value</line></structuredLyrics></lyricsList></subsonic-response>"
        val fragmented =
            object : FilterInputStream(xml.byteInputStream()) {
                override fun read(
                    buffer: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int = super.read(buffer, offset, minOf(length, 7))
            }
        assertEquals(
            value,
            SubsonicXml
                .lyrics(fragmented)
                .single()
                .lines
                .single()
                .text,
        )
    }

    @Test
    fun responseErrorsAndExternalEntitiesStillUseSharedProtections() {
        val error =
            assertThrows(SubsonicException::class.java) {
                SubsonicXml.lyrics(
                    "<subsonic-response status=\"failed\"><error code=\"40\" message=\"Denied\"/></subsonic-response>".byteInputStream(),
                )
            }
        assertEquals(40, error.code)
        assertThrows(SubsonicException::class.java) {
            SubsonicXml.lyrics(
                (
                    "<!DOCTYPE subsonic-response [<!ENTITY xxe SYSTEM \"file:///unavailable-lyrics-fixture\">]>" +
                        "<subsonic-response status=\"ok\"><lyrics>&xxe;</lyrics></subsonic-response>"
                ).byteInputStream(),
            )
        }
        assertThrows(SubsonicException::class.java) {
            SubsonicXml.lyrics("<html>Unexpected</html>".byteInputStream())
        }
    }
}
