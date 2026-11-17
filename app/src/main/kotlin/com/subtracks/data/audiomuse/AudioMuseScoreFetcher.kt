package com.subtracks.data.audiomuse

import com.subtracks.data.security.CredentialStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class AudioMuseScoreAccess(
    val sourceId: Long? = null,
    val offline: Boolean = true,
)

data class AudioMuseScoreBatch(
    val scores: Map<String, AudioMuseMoodScores> = emptyMap(),
    val notAnalysed: Set<String> = emptySet(),
    val unavailable: Set<String> = emptySet(),
    val failed: Set<String> = emptySet(),
)

/** Explicit actions only. Reading AudioMuseMoodCache remains independent of this network coordinator. */
class AudioMuseScoreFetcher(
    private val cache: AudioMuseMoodCache,
    private val credentials: CredentialStore,
    private val access: StateFlow<AudioMuseScoreAccess>,
    private val client: (AudioMuseConnection) -> AudioMuseClient = { OkHttpAudioMuseClient(it.url, it.token) },
) {
    private val lock = Mutex()
    private val misses =
        object : LinkedHashMap<Miss, Unit>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Miss, Unit>): Boolean = size > MAX_MISSES
        }

    private data class Miss(
        val sourceId: Long,
        val key: String,
        val songId: String,
    )

    /** Serializes batches so overlapping callers reuse the first batch's persisted scores and 404 answers. */
    suspend fun fetch(
        sourceId: Long,
        songIds: List<String>,
    ): AudioMuseScoreBatch =
        lock.withLock {
            require(songIds.size <= AudioMuseMoodCache.MAX_BATCH && songIds.all { it.isNotBlank() })
            val wanted = songIds.distinct()
            if (wanted.isEmpty()) return@withLock AudioMuseScoreBatch()
            if (access.value.sourceId != sourceId) return@withLock AudioMuseScoreBatch(unavailable = wanted.toSet())
            val scope = cache.scope(sourceId) ?: return@withLock AudioMuseScoreBatch(unavailable = wanted.toSet())
            val token =
                withContext(Dispatchers.IO) {
                    scope.config.tokenCiphertext?.let { credentials.decrypt(it) }
                }
            if (scope.config.tokenCiphertext != null && token == null) {
                return@withLock AudioMuseScoreBatch(unavailable = wanted.toSet())
            }
            val cached = cache.read(sourceId, wanted)
            checkCurrent(scope, online = false)
            val knownMissing = wanted.filter { it !in cached && misses.containsKey(Miss(sourceId, scope.key, it)) }.toSet()
            val remaining = wanted.filter { it !in cached && it !in knownMissing }
            if (remaining.isEmpty()) return@withLock AudioMuseScoreBatch(cached, knownMissing)
            if (access.value.offline) return@withLock AudioMuseScoreBatch(cached, knownMissing, remaining.toSet())

            coroutineScope {
                val requestScope = this
                val watcher =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        combine(access, cache.observeScope(sourceId)) { gate, current ->
                            gate.sourceId == sourceId && !gate.offline && current?.key == scope.key
                        }.collect { valid ->
                            if (!valid) requestScope.cancel("AudioMuse access changed")
                        }
                    }
                try {
                    checkCurrent(scope)
                    val api = client(AudioMuseConnection(scope.config.url, token))
                    val scores = cached.toMutableMap()
                    val missing = knownMissing.toMutableSet()
                    val failed = mutableSetOf<String>()
                    for (chunk in remaining.chunked(MAX_CONCURRENT)) {
                        val outcomes =
                            chunk
                                .map { id ->
                                    async {
                                        checkCurrent(scope)
                                        try {
                                            Outcome(id, api.moodScores(id))
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (failure: AudioMuseHttpException) {
                                            Outcome(id, notAnalysed = failure.failure == AudioMuseFailure.NOT_FOUND)
                                        } catch (_: Exception) {
                                            Outcome(id)
                                        }
                                    }
                                }.awaitAll()
                        for (outcome in outcomes) {
                            checkCurrent(scope)
                            when {
                                outcome.scores != null -> {
                                    val stored =
                                        try {
                                            cache.store(scope, outcome.id, outcome.scores)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            failed += outcome.id
                                            continue
                                        }
                                    if (!stored) throw CancellationException("AudioMuse configuration changed")
                                    scores[outcome.id] = outcome.scores
                                }

                                outcome.notAnalysed -> {
                                    missing += outcome.id
                                    misses[Miss(sourceId, scope.key, outcome.id)] = Unit
                                }

                                else -> {
                                    failed += outcome.id
                                }
                            }
                        }
                    }
                    checkCurrent(scope)
                    AudioMuseScoreBatch(scores, missing, failed = failed)
                } finally {
                    watcher.cancel()
                }
            }
        }

    private suspend fun checkCurrent(
        scope: AudioMuseScoreScope,
        online: Boolean = true,
    ) {
        currentCoroutineContext().ensureActive()
        val gate = access.value
        if (gate.sourceId != scope.sourceId || (online && gate.offline) || cache.scope(scope.sourceId)?.key != scope.key) {
            throw CancellationException("AudioMuse access changed")
        }
    }

    private data class Outcome(
        val id: String,
        val scores: AudioMuseMoodScores? = null,
        val notAnalysed: Boolean = false,
    )

    companion object {
        const val MAX_CONCURRENT = 5
        private const val MAX_MISSES = 2_000
    }
}
