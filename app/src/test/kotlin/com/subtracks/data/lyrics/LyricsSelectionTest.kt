package com.subtracks.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsSelectionTest {
    private fun variant(
        language: String,
        synced: Boolean,
        text: String = language,
    ) = ProvidedLyrics(LyricsProviderId.Server, Lyrics(listOf(LyricsLine(text, if (synced) 1000 else null)), synced, language))

    @Test
    fun explicitLanguageOverridesTimingAndPrefersItsSyncedVariant() {
        val foreignTimed = variant("en", true)
        val preferredPlain = variant("fra", false)
        val preferredTimed = variant("fr", true)
        assertEquals(preferredPlain, LyricsSelection.best(listOf(foreignTimed, preferredPlain), "en", "fr"))
        assertEquals(preferredTimed, LyricsSelection.best(listOf(preferredPlain, foreignTimed, preferredTimed), "en", "fra"))
    }

    @Test
    fun automaticChoicePrefersLocaleTimingThenAnyTimingThenLocalePlain() {
        val foreignTimed = variant("de", true)
        val localPlain = variant("en", false)
        val localTimed = variant("eng", true)
        assertEquals(localTimed, LyricsSelection.best(listOf(foreignTimed, localPlain, localTimed), "en-US"))
        assertEquals(foreignTimed, LyricsSelection.best(listOf(localPlain, foreignTimed), "en-US"))
        assertEquals(localPlain, LyricsSelection.best(listOf(variant("de", false), localPlain), "en-US"))
        assertEquals(foreignTimed, LyricsSelection.best(listOf(foreignTimed), "fr", "missing"))
        assertNull(LyricsSelection.best(emptyList(), "en"))
    }

    @Test
    fun normalizesIsoLanguageCodesAndPreservesScriptAndRegionChoices() {
        assertEquals("en", LyricsSelection.language("ENG"))
        assertEquals("pt-BR", LyricsSelection.language("por_br"))
        assertEquals("zh-Hant", LyricsSelection.language("zho-Hant"))
        assertEquals("und", LyricsSelection.language("xxx"))
        assertEquals("und", LyricsSelection.language(" "))
        val variants =
            listOf(
                variant("xxx", false),
                variant("und", true),
                variant("eng", false),
                variant("en", true),
                variant("zh-Hans", true),
                variant("zh-Hant", true),
            )
        assertEquals(listOf("und", "en", "zh-Hans", "zh-Hant"), LyricsSelection.languages(variants))
        assertEquals(variants.last(), LyricsSelection.best(variants, "zh-Hant-TW"))
    }

    @Test
    fun exactUserChoiceDoesNotSwitchRegionToGainTimings() {
        val usTimed = variant("en-US", true)
        val gbPlain = variant("en-GB", false)
        assertEquals(gbPlain, LyricsSelection.best(listOf(usTimed, gbPlain), "en-US", "en-GB"))
        assertEquals(usTimed, LyricsSelection.best(listOf(gbPlain, usTimed), "en-GB"))
    }
}
