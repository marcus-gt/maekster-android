package com.subtracks.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subtracks.R
import com.subtracks.UiMessage
import com.subtracks.data.mix.QueueBuilder
import com.subtracks.data.mix.QueueBuilderIndex
import com.subtracks.data.mix.QueueBuilderPicks
import com.subtracks.data.mix.QueueBuilderSelection
import com.subtracks.data.mix.available
import com.subtracks.data.prefs.UserPreferences
import com.subtracks.log.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the Queue Builder shows: the genres and decades on offer, the picks still available among them
 * ([selection]), the length, and how many songs match (null while counting).
 */
data class QueueBuilderState(
    val loading: Boolean = true,
    val offline: Boolean = false,
    val index: QueueBuilderIndex = QueueBuilderIndex(),
    val selection: QueueBuilderSelection = QueueBuilderSelection(),
    val length: Int = QueueBuilderPicks.DEFAULT_LENGTH,
    val matchCount: Long? = null,
) {
    val canBuild: Boolean get() = !loading && matchCount != 0L

    /** The main genre is picked whole or through some of its sub-genres (the card's genre row). */
    fun isPicked(main: String): Boolean = main in selection.mains || index.column(main)?.pills?.any { it.key in selection.tags } == true

    /** A sub-genre counts as picked when picked itself or when its whole main genre is. */
    fun isSelected(
        pill: String,
        main: String,
    ): Boolean = pill in selection.tags || main in selection.mains
}

private data class LoadedIndex(
    val sourceId: Long,
    val offline: Boolean,
    val index: QueueBuilderIndex,
)

/**
 * The Discover Queue Builder: pick genres and decades, choose a length, build and play. Picks and length are
 * remembered per server; the index is read from the mirror (downloads only when [offline]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QueueBuilderViewModel(
    activeSourceId: Flow<Long?>,
    offline: Flow<Boolean>,
    private val builder: QueueBuilder,
    private val preferences: UserPreferences,
    private val play: suspend (QueueBuilderSelection, Int) -> Boolean,
    private val showMessage: (UiMessage) -> Unit,
) : ViewModel() {
    private val reload = MutableStateFlow(0)
    private val picks = MutableStateFlow<Pair<Long, QueueBuilderPicks>?>(null)

    private val loaded =
        combine(activeSourceId.distinctUntilChanged(), offline.distinctUntilChanged(), reload) { id, off, _ -> id to off }
            .mapLatest { (id, off) ->
                id?.let {
                    try {
                        LoadedIndex(it, off, builder.index(it, off))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        Log.w("mix", "queue builder index failed", e)
                        LoadedIndex(it, off, QueueBuilderIndex())
                    }
                }
            }

    val state: StateFlow<QueueBuilderState> =
        combine(loaded, picks) { index, saved -> index to saved }
            .mapLatest { (loaded, saved) ->
                when {
                    loaded == null -> {
                        QueueBuilderState(loading = false)
                    }

                    saved == null || saved.first != loaded.sourceId -> {
                        QueueBuilderState(offline = loaded.offline)
                    }

                    else -> {
                        val selection = loaded.index.available(saved.second.selection)
                        QueueBuilderState(
                            loading = false,
                            offline = loaded.offline,
                            index = loaded.index,
                            selection = selection,
                            length = saved.second.length,
                            matchCount = builder.matchCount(loaded.sourceId, selection, loaded.offline),
                        )
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QueueBuilderState())

    private val _building = MutableStateFlow(false)
    val building = _building.asStateFlow()

    private val _focusedMain = MutableStateFlow<String?>(null)

    /** The main genre whose sub-genres the Discover card shows in its second row. */
    val focusedMain = _focusedMain.asStateFlow()

    init {
        viewModelScope.launch {
            activeSourceId.distinctUntilChanged().collectLatest { id ->
                if (id == null) {
                    picks.value = null
                } else {
                    preferences.queueBuilderPicks(id).collect { picks.value = id to it }
                }
            }
        }
    }

    /** Re-reads the library, for a sync that may have changed its genres or years. */
    fun refresh() {
        reload.value++
    }

    /** A column title on the full page: picks the whole main genre (dropping its single sub-genres), or unpicks it. */
    fun toggleMain(main: String) =
        editSelection {
            if (main in it.mains) it.copy(mains = it.mains - main) else it.withoutTagsOf(main).copy(mains = it.mains + main)
        }

    /**
     * A main genre on the Discover card. Unpicked: picks it whole and shows its sub-genres. Picked while another
     * genre's sub-genres show: shows this one's. Picked and showing: unpicks it with its sub-genres.
     */
    fun tapMainInCard(main: String) {
        // The saved picks, not the shown state, so quick taps never act on a match count still being worked out.
        val saved = picks.value?.second?.selection ?: return
        when {
            !state.value.copy(selection = saved).isPicked(main) -> {
                editSelection { it.copy(mains = it.mains + main) }
                _focusedMain.value = main
            }

            _focusedMain.value != main -> {
                _focusedMain.value = main
            }

            else -> {
                editSelection { it.withoutTagsOf(main).copy(mains = it.mains - main) }
                _focusedMain.value = null
            }
        }
    }

    /** A sub-genre pill. On a wholly picked main genre it narrows the pick to this sub-genre; otherwise it toggles it. */
    fun togglePill(
        pill: String,
        main: String,
    ) = editSelection {
        if (main in it.mains) it.copy(mains = it.mains - main, tags = it.tags + pill) else it.copy(tags = it.tags.toggle(pill))
    }

    fun toggleDecade(decade: Int) = editSelection { it.copy(decades = it.decades.toggle(decade)) }

    fun clearGenres() {
        editSelection { it.copy(mains = emptySet(), tags = emptySet()) }
        _focusedMain.value = null
    }

    fun clearDecades() = editSelection { it.copy(decades = emptySet()) }

    /** Clears every pick; the length stays. */
    fun reset() {
        editSelection { QueueBuilderSelection() }
        _focusedMain.value = null
    }

    private fun QueueBuilderSelection.withoutTagsOf(main: String): QueueBuilderSelection {
        val pills =
            state.value.index
                .column(main)
                ?.pills
                ?.map { it.key }
                .orEmpty()
        return copy(tags = tags - pills.toSet())
    }

    fun setLength(length: Int) = edit { it.copy(length = QueueBuilderPicks.clampLength(length)) }

    /** Builds and plays a queue from the picks shown; [onStarted] runs once playback has started. */
    fun build(onStarted: () -> Unit = {}) {
        val current = state.value
        if (_building.value || !current.canBuild) return
        _building.value = true
        viewModelScope.launch {
            try {
                if (play(current.selection, current.length)) {
                    onStarted()
                } else {
                    showMessage(UiMessage(R.string.queue_builder_no_match))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.w("mix", "queue builder failed", e)
                showMessage(UiMessage(R.string.mix_failed))
            } finally {
                _building.value = false
            }
        }
    }

    // Edits apply to the saved picks, so a pick hidden only for now (offline, no downloads in that genre)
    // comes back later; what is shown and built is always the available part.
    private fun editSelection(change: (QueueBuilderSelection) -> QueueBuilderSelection) = edit { it.copy(selection = change(it.selection)) }

    private fun edit(change: (QueueBuilderPicks) -> QueueBuilderPicks) {
        val (sourceId, current) = picks.value ?: return
        val updated = change(current)
        if (updated == current) return
        picks.value = sourceId to updated
        viewModelScope.launch { preferences.setQueueBuilderPicks(sourceId, updated) }
    }

    private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
}
