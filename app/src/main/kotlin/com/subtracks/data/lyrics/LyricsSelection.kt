package com.subtracks.data.lyrics

import java.util.Locale

object LyricsSelection {
    private val isoLanguages =
        Locale.getISOLanguages().associate { code ->
            val locale = Locale.forLanguageTag(code)
            locale.getISO3Language() to locale.language
        }

    fun language(tag: String): String {
        val parts = tag.trim().replace('_', '-').split('-')
        val primary = parts.first().lowercase(Locale.ROOT)
        if (primary.isEmpty() || primary in setOf("xxx", "und")) return "und"
        val base = isoLanguages[primary] ?: primary
        return Locale.forLanguageTag((listOf(base) + parts.drop(1)).joinToString("-")).toLanguageTag()
    }

    fun languages(variants: List<ProvidedLyrics>): List<String> = variants.map { language(it.lyrics.language) }.distinct()

    fun best(
        variants: List<ProvidedLyrics>,
        localeLanguage: String = Locale.getDefault().toLanguageTag(),
        preferredLanguage: String? = null,
    ): ProvidedLyrics? {
        fun List<ProvidedLyrics>.bestTimed(): ProvidedLyrics? = firstOrNull { it.lyrics.synced } ?: firstOrNull()
        if (!preferredLanguage.isNullOrBlank()) {
            val preferred = language(preferredLanguage)
            val exact = variants.filter { language(it.lyrics.language) == preferred }
            val sameLanguage = variants.filter { primary(it.lyrics.language) == primary(preferred) }
            val script = Locale.forLanguageTag(preferred).script
            val sameScript =
                if (script.isEmpty()) {
                    emptyList()
                } else {
                    sameLanguage.filter {
                        Locale.forLanguageTag(language(it.lyrics.language)).script ==
                            script
                    }
                }
            val matching = exact.ifEmpty { sameScript.ifEmpty { sameLanguage } }
            matching.bestTimed()?.let { return it }
        }
        val locale = language(localeLanguage)
        val script = Locale.forLanguageTag(locale).script
        val local =
            variants
                .filter { primary(it.lyrics.language) == primary(locale) }
                .sortedBy {
                    val tag = language(it.lyrics.language)
                    when {
                        tag == locale -> 0
                        script.isNotEmpty() && Locale.forLanguageTag(tag).script == script -> 1
                        else -> 2
                    }
                }
        return local.firstOrNull { it.lyrics.synced } ?: variants.firstOrNull { it.lyrics.synced }
            ?: local.firstOrNull() ?: variants.firstOrNull()
    }

    private fun primary(tag: String): String = language(tag).substringBefore('-')
}
