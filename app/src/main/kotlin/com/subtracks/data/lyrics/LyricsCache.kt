package com.subtracks.data.lyrics

import com.subtracks.data.db.LyricsCacheDao
import com.subtracks.data.model.CachedLyrics

enum class LyricsProviderId(
    val key: String,
) {
    Server("server"),
    Lrclib("lrclib"),
}

class LyricsCache(
    private val dao: LyricsCacheDao,
) {
    suspend fun read(
        sourceId: Long,
        songId: String,
        provider: LyricsProviderId,
    ): List<Lyrics>? = dao.get(sourceId, songId, provider.key)?.let { LyricsCodec.decode(it.payload) }

    suspend fun put(
        sourceId: Long,
        songId: String,
        provider: LyricsProviderId,
        lyrics: List<Lyrics>,
        fetchedAt: Long,
    ) {
        dao.put(CachedLyrics(sourceId, songId, provider.key, LyricsCodec.encode(lyrics), fetchedAt))
    }

    suspend fun remove(
        sourceId: Long,
        songId: String,
        provider: LyricsProviderId,
    ) {
        dao.remove(sourceId, songId, provider.key)
    }
}
