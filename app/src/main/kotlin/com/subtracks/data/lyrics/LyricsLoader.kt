package com.subtracks.data.lyrics

import com.subtracks.data.model.Song
import com.subtracks.data.prefs.LyricsPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformLatest
import java.util.Locale

sealed interface LyricsState {
    /** Nothing is playing. */
    data object Idle : LyricsState

    data object Loading : LyricsState

    data class Found(
        val variants: List<ProvidedLyrics>,
    ) : LyricsState

    /** No lyrics; when [offline], only the cache was consulted. */
    data class Missing(
        val offline: Boolean,
    ) : LyricsState

    data object Failed : LyricsState
}

/** The lyrics to show, chosen from a [LyricsState.Found] by language, with timing for the listener's correction. */
data class LyricsDisplay(
    val lyrics: ProvidedLyrics,
    val languages: List<String>,
    val language: String,
    val timing: LyricsTiming,
)

fun LyricsState.Found.display(
    preferredLanguage: String?,
    correctionMs: Int,
    localeLanguage: String = Locale.getDefault().toLanguageTag(),
): LyricsDisplay? {
    val chosen = LyricsSelection.best(variants, localeLanguage, preferredLanguage) ?: return null
    return LyricsDisplay(
        lyrics = chosen,
        languages = LyricsSelection.languages(variants),
        language = LyricsSelection.language(chosen.lyrics.language),
        timing = LyricsTiming(chosen.lyrics, correctionMs.toLong()),
    )
}

/**
 * Follows the playing song and the lyric settings, loading lyrics once per song, source choice, LRCLIB permission and
 * connectivity. Timing correction and the preview setting do not reload anything. A newer request cancels the
 * previous lookup.
 */
class LyricsLoader(
    private val fetch: suspend (song: Song, source: LyricsSource, offline: Boolean, allowLrclib: Boolean) -> List<ProvidedLyrics>,
) {
    private data class Request(
        val song: Song?,
        val source: LyricsSource,
        val allowLrclib: Boolean,
        val offline: Boolean,
        val attempt: Int,
    ) {
        val key = listOf(song?.sourceId, song?.id, song?.title, song?.artist, song?.album, source, allowLrclib, offline, attempt)
    }

    /**
     * Loading is shown only when the song changes; a reload for the same song (connectivity or a setting) keeps the
     * lyrics on screen until the new result replaces them.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun states(
        song: Flow<Song?>,
        preferences: Flow<LyricsPreferences>,
        offline: Flow<Boolean>,
        attempts: Flow<Int> = flowOf(0),
    ): Flow<LyricsState> =
        flow {
            var shown: Pair<Long, String>? = null
            val requests =
                combine(song, preferences, offline, attempts) { current, prefs, isOffline, attempt ->
                    Request(current, prefs.source, prefs.allowLrclib, isOffline, attempt)
                }.distinctUntilChanged { old, new -> old.key == new.key }
            emitAll(
                requests.transformLatest { request ->
                    val current = request.song
                    if (current == null) {
                        shown = null
                        emit(LyricsState.Idle)
                        return@transformLatest
                    }
                    val identity = current.sourceId to current.id
                    if (identity != shown) {
                        shown = identity
                        emit(LyricsState.Loading)
                    }
                    emit(load(current, request))
                },
            )
        }

    private suspend fun load(
        song: Song,
        request: Request,
    ): LyricsState =
        try {
            val variants = fetch(song, request.source, request.offline, request.allowLrclib)
            if (variants.isEmpty()) LyricsState.Missing(request.offline) else LyricsState.Found(variants)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            LyricsState.Failed
        }

    companion object {
        fun from(repository: LyricsRepository) = LyricsLoader(repository::fetch)
    }
}
