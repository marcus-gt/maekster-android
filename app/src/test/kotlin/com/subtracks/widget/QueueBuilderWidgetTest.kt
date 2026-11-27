package com.subtracks.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.R
import com.subtracks.data.audiomuse.QueueBuilderMood
import com.subtracks.data.mix.GenreTaxonomy
import com.subtracks.data.mix.QueueBuilderColumn
import com.subtracks.data.mix.QueueBuilderIndex
import com.subtracks.data.mix.QueueBuilderPicks
import com.subtracks.data.mix.QueueBuilderPill
import com.subtracks.data.mix.QueueBuilderSelection
import com.subtracks.data.model.QueueKind
import com.subtracks.playback.PlaybackState
import com.subtracks.playback.QueueCollection
import com.subtracks.playback.QueueContext
import com.subtracks.playback.QueueItem
import com.subtracks.ui.discover.GenreTint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueueBuilderWidgetTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun column(
        main: String,
        songs: Long,
        vararg pills: String,
    ) = QueueBuilderColumn(main, songs, pills.map { QueueBuilderPill(it, it.replaceFirstChar(Char::uppercase), 1) })

    private val index =
        QueueBuilderIndex(
            columns =
                listOf(
                    column("Electronic", 600, "techno", "house"),
                    column("Rock", 500, "punk"),
                    column("Jazz", 400),
                    column("Hip Hop", 300),
                    column("Funk / Soul", 200),
                    column("Pop", 100, "synth-pop"),
                    column(GenreTaxonomy.OTHER, 50),
                ),
            decades = mapOf(1950 to 5L, 1960 to 5L, 1980 to 10L, 2000 to 20L, 2010 to 30L, 2020 to 40L),
        )

    @Test
    fun theWidgetOffersTheFiveBiggestMainGenresAndNeverOther() {
        assertEquals(listOf("Electronic", "Rock", "Jazz", "Hip Hop", "Funk / Soul"), index.widgetGenres())

        val small = QueueBuilderIndex(columns = listOf(column("Jazz", 10), column(GenreTaxonomy.OTHER, 5)))
        assertEquals(listOf("Jazz"), small.widgetGenres())
    }

    @Test
    fun decadeGroupsPickEveryLibraryDecadeInThemAndClearThemAllAgain() {
        val library = index.decades.keys

        val recent = DecadeGroup.Recent.toggle(QueueBuilderSelection(), library)
        assertEquals(setOf(2000, 2010, 2020), recent.decades)
        assertTrue(DecadeGroup.Recent.isPicked(recent))
        assertEquals(emptySet<Int>(), DecadeGroup.Recent.toggle(recent, library).decades)

        val older = DecadeGroup.Older.toggle(QueueBuilderSelection(decades = setOf(1980)), library)
        assertEquals(setOf(1950, 1960, 1980), older.decades)
        assertFalse(DecadeGroup.Seventies.isPicked(older))
        assertTrue(DecadeGroup.Eighties.isPicked(older))
    }

    @Test
    fun aGroupWithOneDecadePickedInTheAppShowsPickedAndATapClearsOnlyThatGroup() {
        val fromApp = QueueBuilderSelection(decades = setOf(2010, 1980))

        assertTrue(DecadeGroup.Recent.isPicked(fromApp))
        assertEquals(setOf(1980), DecadeGroup.Recent.toggle(fromApp, index.decades.keys).decades)
    }

    @Test
    fun aGenrePickedThroughItsSubGenresIsPickedAndATapClearsItWithThem() {
        val fromApp = QueueBuilderSelection(tags = setOf("techno", "punk"))

        assertTrue(fromApp.isGenrePicked("Electronic", index))
        val cleared = fromApp.toggleGenre("Electronic", index)
        assertEquals(QueueBuilderSelection(tags = setOf("punk")), cleared)

        assertFalse(cleared.isGenrePicked("Electronic", index))

        val none = cleared.toggleGenre("Rock", index)
        assertEquals(QueueBuilderSelection(), none)
        assertEquals(QueueBuilderSelection(mains = setOf("Rock")), none.toggleGenre("Rock", index))
    }

    @Test
    fun picksAreShownInTheirColoursWithTheirState() {
        val selection = QueueBuilderSelection(mains = setOf("Rock"), decades = setOf(2010), moods = setOf(QueueBuilderMood.SAD))
        val view = inflate(QueueBuilderWidgetContent(1, index = index, selection = selection, moods = true))

        val rock = view.findViewById<TextView>(R.id.widget_qb_genre_1)
        assertEquals("Rock", rock.text)
        assertEquals("Rock: picked", rock.contentDescription)
        assertEquals(ColorStateList.valueOf(GenreTint.of("Rock")!!.toArgb()), rock.backgroundTintList)
        val electronic = view.findViewById<TextView>(R.id.widget_qb_genre_0)
        assertEquals("Electronic: not picked", electronic.contentDescription)

        val recent = view.findViewById<TextView>(R.id.widget_qb_decade_0)
        assertEquals("2000+", recent.text)
        assertEquals("2000 and later: picked", recent.contentDescription)
        assertEquals(ColorStateList.valueOf(context.getColor(R.color.widget_accent)), recent.backgroundTintList)

        val sad = view.findViewById<TextView>(R.id.widget_qb_mood_1)
        assertEquals("Sad", sad.text)
        assertEquals("Sad: picked", sad.contentDescription)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widget_qb_reset).visibility)
    }

    @Test
    fun onlyTheGroupsTheLibraryHasAreShownAndMoodsOnlyWithAudioMuse() {
        val view = inflate(QueueBuilderWidgetContent(1, index = index))

        val shown = DecadeGroup.entries.filter { group -> view.findViewById<View>(decadeChip(group)).visibility == View.VISIBLE }
        assertEquals(listOf(DecadeGroup.Recent, DecadeGroup.Eighties, DecadeGroup.Older), shown)
        assertEquals(View.GONE, view.findViewById<View>(R.id.widget_qb_moods).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.widget_qb_reset).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.widget_qb_offline).visibility)

        val few = inflate(QueueBuilderWidgetContent(1, index = QueueBuilderIndex(listOf(column("Jazz", 1)), mapOf(1990 to 1L))))
        assertEquals(View.GONE, few.findViewById<View>(R.id.widget_qb_genres_second).visibility)
        assertEquals(View.GONE, few.findViewById<View>(R.id.widget_qb_genre_1).visibility)
    }

    @Test
    fun theSummaryNamesEveryPickIncludingOnesTheWidgetHasNoChipFor() {
        val selection = QueueBuilderSelection(mains = setOf("Rock"), tags = setOf("synth-pop"), decades = setOf(2010, 1980))
        val view = inflate(QueueBuilderWidgetContent(1, index = index, selection = selection))

        assertEquals("Rock and Synth-pop · 2010s and 1980s", view.findViewById<TextView>(R.id.widget_qb_summary).text)
        val nothing = inflate(QueueBuilderWidgetContent(1, index = index))
        assertEquals(context.getString(R.string.queue_builder_anything), nothing.findViewById<TextView>(R.id.widget_qb_summary).text)
    }

    @Test
    fun emptyAndOfflineStatesSayWhy() {
        val noServer = inflate(QueueBuilderWidgetContent(null))
        assertEquals(context.getString(R.string.widget_recent_albums_no_server), noServer.findViewById<TextView>(R.id.widget_qb_empty).text)
        assertEquals(View.GONE, noServer.findViewById<View>(R.id.widget_qb_play_frame).visibility)
        assertEquals(View.GONE, noServer.findViewById<View>(R.id.widget_qb_genres).visibility)

        val noDownloads = inflate(QueueBuilderWidgetContent(1, offline = true))
        assertEquals(
            context.getString(R.string.queue_builder_nothing_offline),
            noDownloads.findViewById<TextView>(R.id.widget_qb_empty).text,
        )

        val offline = inflate(QueueBuilderWidgetContent(1, offline = true, index = index))
        assertEquals(View.VISIBLE, offline.findViewById<View>(R.id.widget_qb_offline).visibility)
        assertEquals(View.GONE, offline.findViewById<View>(R.id.widget_qb_empty).visibility)
        assertEquals(View.VISIBLE, offline.findViewById<View>(R.id.widget_qb_play_frame).visibility)
    }

    @Test
    fun whileAQueueBuildsASpinnerTakesPlaysPlace() {
        val building = inflate(QueueBuilderWidgetContent(1, index = index, building = true))
        assertEquals(View.INVISIBLE, building.findViewById<View>(R.id.widget_qb_play).visibility)
        assertEquals(View.VISIBLE, building.findViewById<View>(R.id.widget_qb_building).visibility)

        val idle = inflate(QueueBuilderWidgetContent(1, index = index))
        assertEquals(View.VISIBLE, idle.findViewById<View>(R.id.widget_qb_play).visibility)
        assertEquals(View.GONE, idle.findViewById<View>(R.id.widget_qb_building).visibility)
    }

    @Test
    fun whileTheQueueBuilderQueuePlaysPlayBecomesPause() {
        val playing = inflate(QueueBuilderWidgetContent(1, index = index, queuePlaying = true))
        assertEquals(context.getString(R.string.controls_pause), playing.findViewById<View>(R.id.widget_qb_play).contentDescription)

        val idle = inflate(QueueBuilderWidgetContent(1, index = index))
        assertEquals(
            context.getString(R.string.queue_builder_play_queue),
            idle.findViewById<View>(R.id.widget_qb_play).contentDescription,
        )
    }

    @Test
    fun pauseShowsOnlyWhileThisServersQueueBuilderQueuePlaysAndTheCoverFollowsWhatPlaysHere() {
        val content = QueueBuilderWidgetContent(1, index = index)
        val item = QueueItem(id = "s", title = "Song", artist = null, album = null, coverArtId = "cover")
        val here = QueueContext(QueueKind.Song, sourceId = 1, refId = "s")
        val playing = PlaybackState(item = item, isPlaying = true, context = here)
        val builder = QueueCollection.QueueBuilder.storedLabel

        val shown = content.withPlayback(playing, builder)
        assertTrue(shown.queuePlaying)
        assertEquals("cover", shown.backgroundCoverArt)

        val paused = content.withPlayback(playing.copy(isPlaying = false), builder)
        assertFalse(paused.queuePlaying)
        // Paused keeps the cover: pausing doesn't blank the background.
        assertEquals("cover", paused.backgroundCoverArt)

        val radio = content.withPlayback(playing, QueueCollection.artistRadioLabel("A"))
        assertFalse(radio.queuePlaying)
        assertEquals("cover", radio.backgroundCoverArt)

        val elsewhere = content.withPlayback(playing.copy(context = here.copy(sourceId = 2)), builder)
        assertFalse(elsewhere.queuePlaying)
        assertEquals(null, elsewhere.backgroundCoverArt)
    }

    @Test
    fun aPausedQueueContinuesWhileThePicksMatchWhatItWasBuiltFrom() {
        val jazz = QueueBuilderSelection(mains = setOf("Jazz"))
        val paused = QueueBuilderWidgetContent(1, index = index, selection = jazz, length = 40, queueHeld = true)

        assertTrue(paused.copy(builtPicks = QueueBuilderPicks(jazz, 40)).resumable)
        assertFalse(paused.copy(builtPicks = QueueBuilderPicks(jazz, 45)).resumable)
        assertFalse(paused.copy(builtPicks = QueueBuilderPicks(QueueBuilderSelection(mains = setOf("Rock")), 40)).resumable)
        assertFalse(paused.resumable)
        val playing = paused.copy(builtPicks = QueueBuilderPicks(jazz, 40), queuePlaying = true)
        assertFalse(playing.resumable)
        assertFalse(playing.paused)
    }

    @Test
    fun whilePausedTheWidgetSaysWhetherPlayContinuesOrBuildsANewQueue() {
        val jazz = QueueBuilderSelection(mains = setOf("Jazz"))
        val paused = QueueBuilderWidgetContent(1, index = index, selection = jazz, length = 40, queueHeld = true)

        val same = inflate(paused.copy(builtPicks = QueueBuilderPicks(jazz, 40)))
        assertEquals(context.getString(R.string.widget_queue_builder_paused), same.findViewById<TextView>(R.id.widget_qb_status).text)
        assertEquals(context.getString(R.string.controls_play), same.findViewById<View>(R.id.widget_qb_play).contentDescription)

        val changed = inflate(paused.copy(builtPicks = QueueBuilderPicks(QueueBuilderSelection(), 40)))
        assertEquals(
            context.getString(R.string.widget_queue_builder_picks_changed),
            changed.findViewById<TextView>(R.id.widget_qb_status).text,
        )
        assertEquals(
            context.getString(R.string.queue_builder_play_queue),
            changed.findViewById<View>(R.id.widget_qb_play).contentDescription,
        )

        val idle = inflate(QueueBuilderWidgetContent(1, index = index, selection = jazz))
        assertEquals(View.GONE, idle.findViewById<View>(R.id.widget_qb_status).visibility)
    }

    @Test
    fun theCoverShowsAsTheBackground() {
        val cover = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val withCover =
            queueBuilderViews(context, QueueBuilderWidgetContent(1, index = index), cover).apply(context, FrameLayout(context))
        assertEquals(View.VISIBLE, withCover.findViewById<View>(R.id.widget_art_background).visibility)

        assertEquals(
            View.GONE,
            inflate(QueueBuilderWidgetContent(1, index = index)).findViewById<View>(R.id.widget_art_background).visibility,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun theContentFollowsTheSavedPicksAndReloadsTheIndexAfterASyncOrGoingOffline() =
        runTest {
            val picks = MutableStateFlow(QueueBuilderPicks(QueueBuilderSelection(mains = setOf("Rock", "Gone"))))
            val offline = MutableStateFlow(false)
            val synced = MutableSharedFlow<Unit>()
            val loads = mutableListOf<Boolean>()
            val content =
                queueBuilderWidgetContent(
                    activeSourceId = flowOf(1L),
                    offline = offline,
                    librarySynced = synced,
                    index = { _, off ->
                        loads += off
                        index
                    },
                    picks = { picks },
                    moodsAvailable = { flowOf(false) },
                )
            val seen = mutableListOf<QueueBuilderWidgetContent>()
            val job = launch { content.collect { seen += it } }
            runCurrent()

            assertEquals(setOf("Rock"), seen.last().selection.mains)
            picks.value = QueueBuilderPicks(QueueBuilderSelection(mains = setOf("Jazz"), moods = setOf(QueueBuilderMood.SAD)))
            runCurrent()
            assertEquals(QueueBuilderSelection(mains = setOf("Jazz")), seen.last().selection)

            synced.emit(Unit)
            runCurrent()
            offline.value = true
            runCurrent()
            assertEquals(listOf(false, false, true), loads)
            assertTrue(seen.last().offline)
            job.cancel()
        }

    @Test
    fun withoutAServerThereIsNothingToPick() =
        runTest {
            val content =
                queueBuilderWidgetContent(
                    flowOf(null),
                    flowOf(false),
                    flowOf(),
                    { _, _ -> error("no server, no index") },
                    { error("no server, no picks") },
                    { flowOf(true) },
                ).take(1).toList()

            assertEquals(listOf(QueueBuilderWidgetContent(null)), content)
        }

    private fun decadeChip(group: DecadeGroup): Int =
        when (group) {
            DecadeGroup.Recent -> R.id.widget_qb_decade_0
            DecadeGroup.Nineties -> R.id.widget_qb_decade_1
            DecadeGroup.Eighties -> R.id.widget_qb_decade_2
            DecadeGroup.Seventies -> R.id.widget_qb_decade_3
            DecadeGroup.Older -> R.id.widget_qb_decade_4
        }

    private fun inflate(content: QueueBuilderWidgetContent): View = queueBuilderViews(context, content).apply(context, FrameLayout(context))
}
