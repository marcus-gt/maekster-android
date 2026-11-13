package com.subtracks.data.lyrics

import java.math.BigInteger

class LyricsTiming(
    lyrics: Lyrics,
    correctionMs: Long = 0,
) {
    private data class Cue(
        val line: Int,
        val time: Long,
    )

    private val cues =
        if (!lyrics.synced || lyrics.instrumental) {
            emptyList()
        } else {
            val offset = BigInteger.valueOf(correctionMs).subtract(BigInteger.valueOf(lyrics.offsetMs))
            lyrics.lines
                .mapIndexedNotNull { index, line ->
                    line.startMs?.takeIf { it >= 0 }?.let { start ->
                        val adjusted =
                            BigInteger
                                .valueOf(start)
                                .add(offset)
                                .coerceIn(MIN_TIME, MAX_TIME)
                                .toLong()
                        Cue(index, adjusted)
                    }
                }.sortedBy { it.time }
        }
    private val byLine = cues.associateBy { it.line }

    fun currentLine(positionMs: Long): Int? {
        if (positionMs < 0) return null
        var low = 0
        var high = cues.size
        while (low < high) {
            val middle = low + (high - low) / 2
            if (cues[middle].time <= positionMs) low = middle + 1 else high = middle
        }
        return cues.getOrNull(low - 1)?.line
    }

    fun seekPosition(
        line: Int,
        durationMs: Long? = null,
    ): Long? {
        val time = byLine[line]?.time?.coerceAtLeast(0) ?: return null
        return if (durationMs != null && durationMs >= 0) time.coerceAtMost(durationMs) else time
    }

    companion object {
        private val MIN_TIME = BigInteger.valueOf(Long.MIN_VALUE)
        private val MAX_TIME = BigInteger.valueOf(Long.MAX_VALUE)
    }
}
