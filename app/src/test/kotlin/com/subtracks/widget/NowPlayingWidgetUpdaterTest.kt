package com.subtracks.widget

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.playback.PlaybackState
import com.subtracks.playback.QueueItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class NowPlayingWidgetUpdaterTest {
    private val state = MutableStateFlow(PlaybackState())
    private val renders = mutableListOf<Pair<NowPlayingWidgetContent, Bitmap?>>()
    private val artworkLoads = mutableListOf<String>()
    private var saved: QueueItem? = null
    private var savedLookups = 0
    private var widgets = true

    private fun item(
        id: String,
        cover: String? = "cover-$id",
    ) = QueueItem(id = id, title = "Song $id", artist = "Artist $id", album = "Album", coverArtId = cover)

    private fun playing(
        item: QueueItem,
        isPlaying: Boolean = true,
        hasNext: Boolean = true,
    ) = PlaybackState(item = item, isPlaying = isPlaying, hasNext = hasNext)

    private fun kotlinx.coroutines.test.TestScope.startUpdater(): NowPlayingWidgetUpdater =
        NowPlayingWidgetUpdater(
            state = state,
            savedItem = {
                savedLookups++
                saved
            },
            artwork = { id ->
                artworkLoads += id
                Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            },
            render = { content, art -> renders += content to art },
            hasWidgets = { widgets },
            scope = backgroundScope,
        ).also { it.start() }

    @Test
    fun aPlayingSongIsShownWithPauseAndSkipAndOnlyChangesThatShowRenderAgain() =
        runTest(UnconfinedTestDispatcher()) {
            startUpdater()
            state.value = playing(item("1"))
            // Position ticks, queue edits and buffering don't change what the widget shows.
            state.value = state.value.copy(position = 4, layout = 7, durationMs = 180_000)
            state.value = state.value.copy(isPlaying = false)

            val shown = renders.drop(1).map { it.first }
            assertEquals(
                listOf(
                    NowPlayingWidgetContent("Song 1", "Artist 1", "cover-1", isPlaying = true, live = true, hasNext = true),
                    NowPlayingWidgetContent("Song 1", "Artist 1", "cover-1", isPlaying = false, live = true, hasNext = true),
                ),
                shown,
            )
        }

    @Test
    fun artworkLoadsOncePerCoverAndIsReusedForTheSameAlbum() =
        runTest(UnconfinedTestDispatcher()) {
            startUpdater()
            state.value = playing(item("1", cover = "album"))
            state.value = state.value.copy(isPlaying = false)
            state.value = playing(item("2", cover = "album"))
            state.value = playing(item("3", cover = "other"))

            assertEquals(listOf("album", "other"), artworkLoads)
            assertSame(renders[1].second, renders[3].second)
        }

    @Test
    fun withoutAPlayingItemTheSavedSongShowsPausedAndWithoutSkipping() =
        runTest(UnconfinedTestDispatcher()) {
            saved = item("saved")
            startUpdater()
            state.value = playing(item("1"))
            // The app was swiped away: the service stops and the controller forgets the item.
            state.value = PlaybackState()

            assertEquals(
                NowPlayingWidgetContent("Song saved", "Artist saved", "cover-saved", isPlaying = false, live = false, hasNext = false),
                renders.last().first,
            )
        }

    @Test
    fun withNothingSavedTheWidgetSaysNothingIsPlaying() =
        runTest(UnconfinedTestDispatcher()) {
            startUpdater()

            assertEquals(NowPlayingWidgetContent.Empty, renders.single().first)
            assertNull(renders.single().second)
        }

    @Test
    fun withoutPlacedWidgetsNothingRendersOrReadsTheQueue() =
        runTest(UnconfinedTestDispatcher()) {
            widgets = false
            startUpdater()
            state.value = playing(item("1"))
            state.value = PlaybackState()

            assertEquals(0, renders.size)
            assertEquals(0, savedLookups)
            assertEquals(0, artworkLoads.size)
        }

    @Test
    fun aRefreshRendersAgainForANewlyPlacedWidget() =
        runTest(UnconfinedTestDispatcher()) {
            widgets = false
            val updater = startUpdater()
            state.value = playing(item("1"))
            widgets = true
            updater.refresh()

            assertEquals(
                NowPlayingWidgetContent("Song 1", "Artist 1", "cover-1", isPlaying = true, live = true, hasNext = true),
                renders.single().first,
            )
        }
}
