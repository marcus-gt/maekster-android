package com.subtracks.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subtracks.data.model.FreshRelease
import com.subtracks.data.musicbrainz.FreshReleases
import com.subtracks.data.musicbrainz.FreshReleasesRefresher
import com.subtracks.data.prefs.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Whether Fresh releases is on, what it found for the active server, and whether a first result is being prepared. */
data class FreshReleasesState(
    val enabled: Boolean = false,
    val releases: List<FreshRelease> = emptyList(),
    val preparing: Boolean = false,
)

/**
 * The Discover Fresh releases feature. Off, it shows nothing and makes no request. On and online, it asks for a
 * refresh when one may be due; the stored result shows offline too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FreshReleasesViewModel(
    activeSourceId: Flow<Long?>,
    offline: Flow<Boolean>,
    private val preferences: UserPreferences,
    feed: FreshReleases,
    private val refresher: FreshReleasesRefresher,
) : ViewModel() {
    private val enabled = preferences.freshReleasesEnabled().distinctUntilChanged()

    val state: StateFlow<FreshReleasesState> =
        combine(activeSourceId.distinctUntilChanged(), enabled) { id, on -> id to on }
            .flatMapLatest { (id, on) ->
                if (!on || id == null) {
                    flowOf(FreshReleasesState(enabled = on))
                } else {
                    combine(feed.releases(id), refresher.running) { releases, running ->
                        FreshReleasesState(enabled = true, releases = releases, preparing = running && releases.isEmpty())
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FreshReleasesState())

    init {
        viewModelScope.launch {
            combine(activeSourceId.distinctUntilChanged(), enabled, offline.distinctUntilChanged()) { id, on, off -> Triple(id, on, off) }
                .collect { (id, on, off) ->
                    refresher.cancel()
                    if (on && id != null && !off) refresher.refreshIfDue(id)
                }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setFreshReleasesEnabled(enabled) }
    }
}

/** The release group's cover on the Cover Art Archive, 250 px. Requested only while Fresh releases is on. */
fun FreshRelease.coverUrl(): String = "https://coverartarchive.org/release-group/$id/front-250"

/** The release group's page on MusicBrainz. */
fun FreshRelease.pageUrl(): String = "https://musicbrainz.org/release-group/$id"
