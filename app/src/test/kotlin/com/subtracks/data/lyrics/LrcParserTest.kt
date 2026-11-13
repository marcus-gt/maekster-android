package com.subtracks.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LrcParserTest {
    @Test
    fun timestampsUseMillisecondsAndKeepOffsetSeparate() {
        val parsed =
            LrcParser.parse(
                """
                [ar:An artist]
                [offset:+175]
                [00:01.7]A
                [02:03.45]B
                [123:59.006]C
                [00:04:25]D
                [00:05]E
                """.trimIndent(),
            )!!
        assertEquals(listOf(1_700L, 4_250L, 5_000L, 123_450L, 7_439_006L), parsed.lines.map { it.startMs })
        assertEquals(listOf("A", "D", "E", "B", "C"), parsed.lines.map { it.text })
        assertEquals(175L, parsed.offsetMs)
        assertEquals(true, parsed.synced)
        assertEquals("und", parsed.language)
    }

    @Test
    fun repeatedTimestampsAndEqualTimesKeepTheirSourceOrder() {
        val parsed = LrcParser.parse("[00:09][00:03]Refrain\n[00:03]Translation\n[00:01]Intro")!!
        assertEquals(listOf(1_000L, 3_000L, 3_000L, 9_000L), parsed.lines.map { it.startMs })
        assertEquals(listOf("Intro", "Refrain", "Translation", "Refrain"), parsed.lines.map { it.text })
    }

    @Test
    fun unicodeLineEndingsBomAndEnhancedTagsPreserveLyricText() {
        val parsed = LrcParser.parse("\uFEFF[00:01.005]  夜 <00:01.10>の歌 🎵  \r\n[00:02]Blåbær\r[00:03]مرحبا")!!
        assertEquals(listOf("夜 の歌 🎵", "Blåbær", "مرحبا"), parsed.lines.map { it.text })
        assertEquals(listOf(1_005L, 2_000L, 3_000L), parsed.lines.map { it.startMs })
    }

    @Test
    fun bracketedTextInsideLyricsIsNotAnotherTimestamp() {
        val parsed = LrcParser.parse("[00:01]Wait [00:09] for me\n [00:02] [00:04]Again")!!
        assertEquals(listOf("Wait [00:09] for me", "Again", "Again"), parsed.lines.map { it.text })
        assertEquals(listOf(1_000L, 2_000L, 4_000L), parsed.lines.map { it.startMs })
    }

    @Test
    fun lastValidSignedOffsetWinsWithoutShiftingStoredTimestamps() {
        val parsed =
            LrcParser.parse(
                "[offset:50]\n[00:01]Line\n[OFFSET: -250 ]\n[offset:9223372036854775808]\n[offset:no]",
            )!!
        assertEquals(-250L, parsed.offsetMs)
        assertEquals(1_000L, parsed.lines.single().startMs)
    }

    @Test
    fun ignoresMetadataEmptyLinesAndMalformedTimes() {
        val source =
            """
            [ti:Example]
            [00:60]Invalid seconds
            [1:2]Missing zero
            [1000:01]Too many minutes
            [-1:02]Negative
            [00:02.1234]Too much precision
            [00:03]
            [00:04]<00:04.00>
            [00:05.50]Valid
            """.trimIndent()
        assertEquals(listOf(LyricsLine("Valid", 5_500L)), LrcParser.parse(source)!!.lines)
        for (empty in listOf("", "Untimed text", "[ar:An artist]\n[offset:200]", "[00:99]Bad", "[00:01]  ")) {
            assertNull(empty, LrcParser.parse(empty))
        }
    }
}
