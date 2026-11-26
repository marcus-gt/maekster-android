package com.subtracks.widget

import android.graphics.Bitmap
import com.subtracks.playback.PlaybackState
import com.subtracks.playback.QueueItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the Now Playing widget shows. [title] is null when there is nothing to play. */
data class NowPlayingWidgetContent(
    val title: String?,
    val artist: String?,
    val coverArtId: String?,
    val isPlaying: Boolean,
    /** A connected player owns the song, so previous and next work. False for the saved song of a stopped app. */
    val live: Boolean,
    val hasNext: Boolean,
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
) {
    private val lock = Mutex()
    private var live: NowPlayingWidgetContent? = null
    private var rendered: NowPlayingWidgetContent? = null
    private var coverArtId: String? = null
    private var bitmap: Bitmap? = null

    fun start(): Job =
        scope.launch {
            state.map(NowPlayingWidgetContent::live).distinctUntilChanged().collect { content ->
                lock.withLock { live = content }
                update(force = false)
            }
        }

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
            val content = live ?: savedItemOrNull()?.let(NowPlayingWidgetContent::saved) ?: NowPlayingWidgetContent.Empty
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
) {
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
