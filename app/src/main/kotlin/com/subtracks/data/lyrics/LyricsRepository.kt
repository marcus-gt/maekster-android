package com.subtracks.data.lyrics

import com.subtracks.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class LyricsSource {
    Auto,
    Server,
    Lrclib,
}

data class ProvidedLyrics(
    val provider: LyricsProviderId,
    val lyrics: Lyrics,
)

class LyricsRepository(
    private val cache: LyricsCache,
    private val server: LyricsProvider,
    private val lrclib: LyricsProvider,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun fetch(
        song: Song,
        source: LyricsSource,
        offline: Boolean,
        allowLrclib: Boolean,
    ): List<ProvidedLyrics> {
        currentCoroutineContext().ensureActive()
        if (source == LyricsSource.Server) return load(song, LyricsProviderId.Server, offline).providedBy(LyricsProviderId.Server)
        if (source == LyricsSource.Lrclib) {
            return if (allowLrclib) load(song, LyricsProviderId.Lrclib, offline).providedBy(LyricsProviderId.Lrclib) else emptyList()
        }
        val fromServer =
            try {
                load(song, LyricsProviderId.Server, offline)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (!allowLrclib) throw failure
                emptyList()
            }
        if (!allowLrclib || fromServer.any { it.synced }) return fromServer.providedBy(LyricsProviderId.Server)
        val fromLrclib =
            try {
                load(song, LyricsProviderId.Lrclib, offline)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (fromServer.isEmpty()) throw failure
                emptyList()
            }
        if (fromServer.isEmpty()) return fromLrclib.providedBy(LyricsProviderId.Lrclib)
        return fromLrclib.filter { it.synced }.providedBy(LyricsProviderId.Lrclib) + fromServer.providedBy(LyricsProviderId.Server)
    }

    private suspend fun load(
        song: Song,
        provider: LyricsProviderId,
        offline: Boolean,
    ): List<Lyrics> {
        currentCoroutineContext().ensureActive()
        cache.read(song.sourceId, song.id, provider)?.let { return it }
        if (offline) return emptyList()
        val lyrics =
            when (provider) {
                LyricsProviderId.Server -> server.fetch(song)
                LyricsProviderId.Lrclib -> lrclib.fetch(song)
            }
        currentCoroutineContext().ensureActive()
        if (lyrics.isNotEmpty()) cache.put(song.sourceId, song.id, provider, lyrics, now())
        return lyrics
    }

    private fun List<Lyrics>.providedBy(provider: LyricsProviderId): List<ProvidedLyrics> = map { ProvidedLyrics(provider, it) }
}
