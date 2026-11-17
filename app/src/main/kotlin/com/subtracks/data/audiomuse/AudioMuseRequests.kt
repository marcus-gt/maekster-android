package com.subtracks.data.audiomuse

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Runs one explicit AudioMuse action for [sourceId]. Returns null without creating a client when the source is
 * not active, the app is offline, or AudioMuse is not configured or its token unreadable. While [block] runs, a
 * change of source, connectivity or AudioMuse URL (or its removal) cancels it with a CancellationException.
 */
internal suspend fun <T> guardedAudioMuseRequest(
    configuration: AudioMuseConfiguration,
    access: StateFlow<AudioMuseScoreAccess>,
    client: (AudioMuseConnection) -> AudioMuseClient,
    sourceId: Long,
    block: suspend (AudioMuseClient) -> T,
): T? {
    val gate = access.value
    if (gate.sourceId != sourceId || gate.offline) return null
    val connection = configuration.connection(sourceId) ?: return null
    return coroutineScope {
        val requestScope = this
        val watcher =
            launch(start = CoroutineStart.UNDISPATCHED) {
                combine(access, configuration.observe(sourceId)) { current, config ->
                    current == gate && config.available && config.url == connection.url
                }.collect { valid ->
                    if (!valid) requestScope.cancel("AudioMuse access changed")
                }
            }
        try {
            block(client(connection))
        } finally {
            watcher.cancel()
        }
    }
}
