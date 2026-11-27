package com.subtracks.widget

import android.graphics.Bitmap
import com.subtracks.R
import com.subtracks.data.audiomuse.AudioMuseScoreAccess
import com.subtracks.data.audiomuse.MoodRadioResult
import com.subtracks.data.audiomuse.RadioMood
import com.subtracks.playback.PlaybackState
import com.subtracks.playback.QueueItem
import com.subtracks.ui.discover.moodRadioMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/** What the Now Playing widget shows. [title] is null when there is nothing to play. */
data class NowPlayingWidgetContent(
    val title: String?,
    val artist: String?,
    val coverArtId: String?,
    val isPlaying: Boolean,
    /** A connected player owns the song, so previous and next work. False for the saved song of a stopped app. */
    val live: Boolean,
    val hasNext: Boolean,
    /** Mood radios can start: AudioMuse is set up for the active server and the app is online. */
    val moods: Boolean = false,
    /** The mood radio a widget tap is starting, until it plays or fails. */
    val startingMood: RadioMood? = null,
    /** The mood radio the player holds, whose button shows play or pause. */
    val playingMood: RadioMood? = null,
    /** Each widget's row below the player and the items of the chosen rows (UX-45). */
    val rows: NowPlayingRows = NowPlayingRows(),
) {
    companion object {
        val Empty = NowPlayingWidgetContent(null, null, null, isPlaying = false, live = false, hasNext = false)

        fun live(state: PlaybackState): NowPlayingWidgetContent? {
            val item = state.item ?: return null
            return NowPlayingWidgetContent(
                title = item.title,
                artist = item.artist,
                coverArtId = item.coverArtId,
                isPlaying = state.isPlaying,
                live = true,
                hasNext = state.hasNext,
            )
        }

        fun saved(item: QueueItem): NowPlayingWidgetContent =
            NowPlayingWidgetContent(item.title, item.artist, item.coverArtId, isPlaying = false, live = false, hasNext = false)
    }
}

/**
 * Keeps the home-screen widgets in step with playback. It renders only while widgets exist and only when what
 * they show changes (not on position ticks or queue edits), and loads the artwork only when the cover changes.
 * Without a playing item (the app was swiped away, or the process is new) it shows the saved song, paused.
 */
class NowPlayingWidgetUpdater(
    private val state: StateFlow<PlaybackState>,
    private val savedItem: suspend () -> QueueItem?,
    private val artwork: suspend (coverArtId: String) -> Bitmap?,
    private val render: (NowPlayingWidgetContent, Bitmap?) -> Unit,
    private val hasWidgets: () -> Boolean,
    private val scope: CoroutineScope,
    private val moodsAvailable: Flow<Boolean> = flowOf(false),
    private val queueLabel: Flow<String?> = flowOf(null),
    private val rows: Flow<NowPlayingRows> = flowOf(NowPlayingRows()),
) {
    private val lock = Mutex()
    private var live: NowPlayingWidgetContent? = null
    private var moods = false
    private var shownRows = NowPlayingRows()
    private var queueMood: RadioMood? = null
    private val starting = MutableStateFlow<RadioMood?>(null)
    private var startingMood: RadioMood? = null
    private var rendered: NowPlayingWidgetContent? = null
    private var coverArtId: String? = null
    private var bitmap: Bitmap? = null

    fun start(): Job =
        scope.launch {
            launch {
                state.map(NowPlayingWidgetContent::live).distinctUntilChanged().collect { content ->
                    lock.withLock { live = content }
                    update(force = false)
                }
            }
            launch {
                moodsAvailable.distinctUntilChanged().collect { available ->
                    lock.withLock { moods = available }
                    update(force = false)
                }
            }
            launch {
                queueLabel.map(::radioMoodOf).distinctUntilChanged().collect { mood ->
                    lock.withLock { queueMood = mood }
                    update(force = false)
                }
            }
            launch {
                rows.distinctUntilChanged().collect { chosen ->
                    lock.withLock { shownRows = chosen }
                    update(force = false)
                }
            }
            launch {
                starting.collect { mood ->
                    lock.withLock { startingMood = mood }
                    update(force = false)
                }
            }
        }

    /** Marks the mood radio a widget tap is starting, or clears it with null. */
    fun showStartingMood(mood: RadioMood?) {
        starting.value = mood
    }

    /** The mood radio a tap on any widget's mood row is starting, so every mood row dims together. */
    val moodStarting: StateFlow<RadioMood?> get() = starting

    /** Renders again even if nothing changed: a widget was added, resized or the launcher asked for it. */
    fun refresh() {
        scope.launch { update(force = true) }
    }

    private suspend fun update(force: Boolean) =
        lock.withLock {
            if (!hasWidgets()) {
                rendered = null
                return@withLock
            }
            val content =
                (live ?: savedItemOrNull()?.let(NowPlayingWidgetContent::saved) ?: NowPlayingWidgetContent.Empty)
                    .copy(
                        moods = moods,
                        startingMood = startingMood?.takeIf { moods },
                        playingMood = queueMood?.takeIf { moods && live != null },
                        rows = shownRows,
                    )
            if (!force && content == rendered) return@withLock
            render(content, artworkFor(content.coverArtId))
            rendered = content
        }

    private suspend fun artworkFor(id: String?): Bitmap? {
        if (id != coverArtId || (id != null && bitmap == null)) {
            bitmap = id?.let { loadArtwork(it) }
            coverArtId = id
        }
        return bitmap
    }

    private suspend fun loadArtwork(id: String): Bitmap? =
        try {
            artwork(id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    private suspend fun savedItemOrNull(): QueueItem? =
        try {
            savedItem()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
}

/**
 * Whether the widget offers mood radios, as Discover does: AudioMuse is set up for the active server and the app is
 * online (a mood radio is never cached, so offline there is nothing to start).
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun widgetMoodsAvailable(
    access: Flow<AudioMuseScoreAccess>,
    configured: (sourceId: Long) -> Flow<Boolean>,
): Flow<Boolean> =
    access
        .map { it.sourceId.takeIf { _ -> !it.offline } }
        .distinctUntilChanged()
        .flatMapLatest { id -> id?.let(configured) ?: flowOf(false) }
        .distinctUntilChanged()

enum class NowPlayingWidgetAction { PlayPause, Previous, Next }

/**
 * The widget's buttons, through [com.subtracks.playback.PlaybackController]. With no playing item, play resumes
 * the saved queue; previous and next only redraw, so a stale widget catches up instead of starting playback.
 */
class NowPlayingWidgetCommands(
    private val hasItem: () -> Boolean,
    private val togglePlayPause: () -> Unit,
    private val resume: suspend () -> Boolean,
    private val previous: () -> Unit,
    private val next: () -> Unit,
    private val refresh: () -> Unit,
    private val playMood: suspend (RadioMood) -> MoodRadioResult = { MoodRadioResult.Unavailable },
    private val showStartingMood: (RadioMood?) -> Unit = {},
    private val showMessage: (messageRes: Int) -> Unit = {},
    private val queueLabel: suspend () -> String? = { null },
) {
    private val startingMood = AtomicBoolean(false)

    /**
     * Starts a mood radio from the home screen, one at a time; taps while one starts are ignored. Nothing opens:
     * the widget shows the radio once it plays, and a short message says why when it can't start. A tap on the mood
     * radio the player already holds pauses or resumes it instead.
     */
    suspend fun startMood(mood: RadioMood) {
        if (hasItem() && radioMoodOf(queueLabel()) == mood) {
            togglePlayPause()
            return
        }
        if (!startingMood.compareAndSet(false, true)) return
        showStartingMood(mood)
        try {
            moodRadioMessage(playMood(mood))?.let(showMessage)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            showMessage(R.string.mood_radio_failed)
        } finally {
            showStartingMood(null)
            startingMood.set(false)
        }
    }

    suspend fun run(action: NowPlayingWidgetAction) {
        if (!hasItem()) {
            if (action != NowPlayingWidgetAction.PlayPause || !resume()) refresh()
            return
        }
        when (action) {
            NowPlayingWidgetAction.PlayPause -> togglePlayPause()
            NowPlayingWidgetAction.Previous -> previous()
            NowPlayingWidgetAction.Next -> next()
        }
    }
}
