package com.subtracks.data.musicbrainz

import com.subtracks.log.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runs fresh-release refreshes in the app's scope: a refresh takes minutes at one MusicBrainz request a second, so
 * it must not stop when the Discover screen goes away. Screens ask for one when it may be due; [FreshReleases] decides.
 */
class FreshReleasesRefresher(
    private val feed: FreshReleases,
    private val scope: CoroutineScope,
    private val activeSourceId: suspend () -> Long?,
) {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    private var job: Job? = null

    /** Starts a refresh for [sourceId] when one is due and none is running. */
    @Synchronized
    fun refreshIfDue(sourceId: Long) {
        if (job?.isActive == true) return
        job =
            scope.launch {
                _running.value = true
                try {
                    feed.refreshIfStale(sourceId) { activeSourceId() == sourceId }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    Log.w("fresh", "fresh releases refresh failed", e)
                } finally {
                    _running.value = false
                }
            }
    }

    /** Stops the old refresh before a preference, connectivity or server change starts another. */
    suspend fun cancel() {
        job?.cancelAndJoin()
        job = null
    }
}
