package com.subtracks.data.lyrics

object LrcParser {
    private val timestamp = Regex("\\[([0-9]{1,3}):([0-9]{2})(?:[.:]([0-9]{1,3}))?]")
    private val wordTimestamp = Regex("<([0-9]{1,3}):([0-9]{2})(?:[.:]([0-9]{1,3}))?>")
    private val offset = Regex("\\[offset:\\s*([+-]?[0-9]+)\\s*]", RegexOption.IGNORE_CASE)

    fun parse(source: String): Lyrics? {
        var offsetMs = 0L
        val lines = mutableListOf<LyricsLine>()
        for (raw in source.lineSequence()) {
            val line = raw.trim().removePrefix("\uFEFF").trimStart()
            val offsetMatch = offset.matchEntire(line)
            if (offsetMatch != null) {
                offsetMatch.groupValues[1].toLongOrNull()?.let { offsetMs = it }
                continue
            }
            var end = 0
            val starts = mutableListOf<Long>()
            while (true) {
                val match = timestamp.find(line, end)?.takeIf { it.range.first == end } ?: break
                val seconds = match.groupValues[2].toLong()
                if (seconds < 60) {
                    val minutes = match.groupValues[1].toLong()
                    val fraction = match.groupValues[3].padEnd(3, '0').toLong()
                    starts += (minutes * 60 + seconds) * 1_000 + fraction
                }
                end = match.range.last + 1
                while (end < line.length && line[end].isWhitespace()) end++
            }
            if (starts.isEmpty()) continue
            val text = wordTimestamp.replace(line.substring(end), "").trim()
            if (text.isEmpty()) continue
            starts.forEach { lines += LyricsLine(text, it) }
        }
        if (lines.isEmpty()) return null
        return Lyrics(lines.sortedBy { it.startMs }, synced = true, offsetMs = offsetMs)
    }
}
