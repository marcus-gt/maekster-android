package com.subtracks.data.audiomuse

import com.subtracks.data.db.AudioMuseScoreDao
import com.subtracks.data.model.AudioMuseConfig
import com.subtracks.data.model.CachedAudioMuseScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** A snapshot for a later explicit fetch. No credentials are exposed in generated string representations. */
class AudioMuseScoreScope internal constructor(
    internal val config: AudioMuseConfig,
) {
    val sourceId: Long get() = config.sourceId
    internal val key: String =
        MessageDigest
            .getInstance("SHA-256")
            .digest((config.url + "\n" + config.tokenCiphertext.orEmpty()).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

/** Persistent analysis only: reading this cache never starts an AudioMuse request. */
class AudioMuseMoodCache(
    private val dao: AudioMuseScoreDao,
) {
    suspend fun scope(sourceId: Long): AudioMuseScoreScope? = dao.configuration(sourceId)?.let(::AudioMuseScoreScope)

    fun observeScope(sourceId: Long): kotlinx.coroutines.flow.Flow<AudioMuseScoreScope?> =
        dao.observeConfiguration(sourceId).map { it?.let(::AudioMuseScoreScope) }

    suspend fun read(
        sourceId: Long,
        songIds: List<String>,
    ): Map<String, AudioMuseMoodScores> {
        require(songIds.size <= MAX_BATCH)
        if (songIds.isEmpty()) return emptyMap()
        val scope = scope(sourceId) ?: return emptyMap()
        val rows = dao.readIfCurrent(scope.config, scope.key, songIds)
        return withContext(Dispatchers.Default) {
            rows
                .mapNotNull { row ->
                    AudioMuseScoreCodec.decode(row.payload)?.let { row.songId to it }
                }.toMap()
        }
    }

    /** Returns false when settings changed or were removed while the caller was fetching this score. */
    suspend fun store(
        scope: AudioMuseScoreScope,
        songId: String,
        scores: AudioMuseMoodScores,
    ): Boolean {
        require(songId.isNotBlank())
        val payload = withContext(Dispatchers.Default) { AudioMuseScoreCodec.encode(scores) }
        return dao.putIfCurrent(scope.config, CachedAudioMuseScore(scope.sourceId, songId, scope.key, payload))
    }

    companion object {
        const val MAX_BATCH = 500
    }
}

internal object AudioMuseScoreCodec {
    fun encode(scores: AudioMuseMoodScores): String {
        require(scores.values.size <= 128 && scores.styles.size <= 128)
        val values = JSONObject()
        for ((name, score) in scores.values) {
            require(valid(name, score))
            values.put(name, score)
        }
        val styles = JSONArray()
        for (style in scores.styles) {
            require(valid(style.label, style.score))
            styles.put(JSONObject().put("label", style.label).put("score", style.score))
        }
        return JSONObject()
            .put("version", 1)
            .put("values", values)
            .put("styles", styles)
            .toString()
    }

    fun decode(payload: String): AudioMuseMoodScores? =
        try {
            val json = JSONObject(payload)
            require(json.get("version") == 1)
            val values = json.getJSONObject("values")
            val styles = json.getJSONArray("styles")
            require(values.length() <= 128 && styles.length() <= 128)
            val named =
                values.keys().asSequence().associateWith { name ->
                    val score = (values.get(name) as Number).toDouble()
                    require(valid(name, score))
                    score
                }
            val ranked =
                List(styles.length()) { index ->
                    val style = styles.getJSONObject(index)
                    val name = style.get("label") as String
                    val score = (style.get("score") as Number).toDouble()
                    require(valid(name, score))
                    AudioMuseStyle(name, score)
                }.sortedByDescending { it.score }
            AudioMuseMoodScores(named, ranked)
        } catch (_: Exception) {
            null
        }

    private fun valid(
        name: String,
        score: Double,
    ): Boolean = name.isNotBlank() && name.length <= 256 && name.none(Char::isISOControl) && score.isFinite() && score in 0.0..1.0
}

/** Queue Builder supports Party too; AudioMuse Song Path endpoints support only the five path moods. */
enum class QueueBuilderMood(
    val wireName: String,
) {
    HAPPY("happy"),
    SAD("sad"),
    RELAXED("relaxed"),
    AGGRESSIVE("aggressive"),
    PARTY("party"),
    DANCEABLE("danceable"),
}

/** Ranks one bounded candidate batch. Missing any picked score excludes a song; ties keep input order. */
fun rankMoodCandidates(
    candidates: List<String>,
    scores: Map<String, AudioMuseMoodScores>,
    moods: Set<QueueBuilderMood>,
    limit: Int,
): List<String> {
    require(candidates.size <= AudioMuseMoodCache.MAX_BATCH)
    if (moods.isEmpty()) return candidates.take(limit.coerceAtLeast(0))
    return candidates
        .mapNotNull { id ->
            val values = scores[id]?.values ?: return@mapNotNull null
            val picked = moods.map { values[it.wireName] ?: return@mapNotNull null }
            if (picked.any { !it.isFinite() || it !in 0.0..1.0 }) return@mapNotNull null
            id to picked.average()
        }.sortedByDescending { it.second }
        .take(limit.coerceAtLeast(0))
        .map { it.first }
}
