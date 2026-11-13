package com.subtracks.data.lyrics

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal object LyricsCodec {
    fun encode(lyrics: List<Lyrics>): String {
        val tracks = JSONArray()
        for (track in lyrics) {
            val lines = JSONArray()
            for (line in track.lines) {
                lines.put(JSONObject().put("text", line.text).put("startMs", line.startMs ?: JSONObject.NULL))
            }
            tracks.put(
                JSONObject()
                    .put("synced", track.synced)
                    .put("language", track.language)
                    .put("offsetMs", track.offsetMs)
                    .put("lines", lines),
            )
        }
        return JSONObject().put("version", 1).put("tracks", tracks).toString()
    }

    fun decode(payload: String): List<Lyrics>? =
        try {
            val root = JSONObject(payload)
            if (root.long("version") != 1L) return null
            val tracks = root.getJSONArray("tracks")
            (0 until tracks.length()).map { index ->
                val track = tracks.getJSONObject(index)
                val synced = track.get("synced") as? Boolean ?: return null
                val language = (track.get("language") as? String)?.takeIf { it.isNotBlank() } ?: return null
                val offsetMs = track.long("offsetMs") ?: return null
                val values = track.getJSONArray("lines")
                val lines =
                    (0 until values.length()).map { lineIndex ->
                        val line = values.getJSONObject(lineIndex)
                        val text = (line.get("text") as? String)?.takeIf { it.isNotBlank() } ?: return null
                        val start = if (line.get("startMs") == JSONObject.NULL) null else line.long("startMs") ?: return null
                        if ((synced && (start == null || start < 0)) || (!synced && start != null)) return null
                        LyricsLine(text, start)
                    }
                if (lines.isEmpty() || (synced && lines.zipWithNext().any { (a, b) -> a.startMs!! > b.startMs!! })) return null
                Lyrics(lines, synced, language, offsetMs)
            }
        } catch (_: JSONException) {
            null
        }

    private fun JSONObject.long(key: String): Long? =
        when (val value = get(key)) {
            is Long -> value
            is Int -> value.toLong()
            else -> null
        }
}
