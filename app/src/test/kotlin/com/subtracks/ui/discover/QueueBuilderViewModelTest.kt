package com.subtracks.ui.discover

import androidx.lifecycle.viewModelScope
import androidx.room3.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.R
import com.subtracks.UiMessage
import com.subtracks.cancelAndJoinBlocking
import com.subtracks.data.audiomuse.QueueBuilderMood
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.mix.GenreTaxonomy
import com.subtracks.data.mix.MoodShortfall
import com.subtracks.data.mix.QueueBuilder
import com.subtracks.data.mix.QueueBuilderPicks
import com.subtracks.data.mix.QueueBuilderSelection
import com.subtracks.data.model.Album
import com.subtracks.data.model.Song
import com.subtracks.data.model.Source
import com.subtracks.data.prefs.UserPreferences
import com.subtracks.data.prefs.fakeUserPreferences
import com.subtracks.playback.QueueBuilderOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class QueueBuilderViewModelTest {
    private lateinit var db: SubtracksDatabase
    private lateinit var prefs: UserPreferences
    private val activeSource = MutableStateFlow<Long?>(null)
    private val offline = MutableStateFlow(false)
    private val played = mutableListOf<Pair<QueueBuilderSelection, Int>>()
    private val messages = mutableListOf<UiMessage>()
    private var playResult = QueueBuilderOutcome(true)
    private val moodsAvailable = MutableStateFlow(false)
    private var model: QueueBuilderViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = createTestDatabase()
        prefs = fakeUserPreferences()
        runBlocking { seed() }
    }

    @After
    fun tearDown() {
        cancelAndJoinBlocking(model?.viewModelScope)
        db.close()
        Dispatchers.resetMain()
    }

    private fun model() =
        QueueBuilderViewModel(
            activeSource,
            offline,
            QueueBuilder(db.mixDao()),
            prefs,
            play = { selection, length ->
                played += selection to length
                playResult
            },
            showMessage = { messages += it },
            moodsAvailable = { id -> moodsAvailable.map { it && id == 1L } },
        ).also { model = it }

    private val modal = GenreTaxonomy.DISCOGS.resolve("Modal").key
    private val fusion = GenreTaxonomy.DISCOGS.resolve("Fusion").key
    private val techno = GenreTaxonomy.DISCOGS.resolve("Techno").key

    private fun QueueBuilderViewModel.awaitState(predicate: (QueueBuilderState) -> Boolean): QueueBuilderState =
        runBlocking { withTimeout(5_000) { state.first(predicate) } }

    @Test
    fun twoPlacementsObserveEachOthersSavedPicks() {
        activeSource.value = 1
        val home = model()
        val discover = model()
        try {
            home.awaitState { !it.loading }
            discover.awaitState { !it.loading }
            home.toggleMain("Jazz")
            discover.awaitState { it.selection.mains == setOf("Jazz") }
            discover.toggleDecade(1990)
            home.awaitState { it.selection.decades == setOf(1990) && it.selection.mains == setOf("Jazz") }
        } finally {
            cancelAndJoinBlocking(home.viewModelScope, discover.viewModelScope)
        }
    }

    @Test
    fun picksAreSavedPerServerAndCountTheMatchingSongs() {
        activeSource.value = 1
        val model = model()
        val loaded = model.awaitState { !it.loading && it.matchCount != null }
        assertEquals(listOf("Jazz", "Electronic"), loaded.index.columns.map { it.main })
        assertEquals(3L, loaded.matchCount)

        model.toggleMain("Jazz")
        model.toggleDecade(1990)
        model.setLength(42)
        model.awaitState { it.selection.mains == setOf("Jazz") && it.matchCount == 1L && it.length == 40 }
        assertEquals(
            QueueBuilderPicks(QueueBuilderSelection(mains = setOf("Jazz"), decades = setOf(1990)), 40),
            runBlocking { prefs.queueBuilderPicks(1).first { it.length == 40 } },
        )

        model.reset()
        model.awaitState { it.selection == QueueBuilderSelection() && it.length == 40 && it.matchCount == 3L }
    }

    @Test
    fun theCardPicksAWholeGenreThenNarrowsItToSubGenresAndUnpicksOnASecondTap() {
        activeSource.value = 1
        val model = model()
        model.awaitState { it.matchCount == 3L }

        model.tapMainInCard("Jazz")
        assertEquals("Jazz", model.focusedMain.value)
        model.awaitState { it.selection.mains == setOf("Jazz") && it.matchCount == 2L }

        // On a wholly picked genre, a sub-genre narrows the pick to just that sub-genre.
        model.togglePill(modal, "Jazz")
        val narrowed = model.awaitState { it.selection.tags == setOf(modal) && it.matchCount == 1L }
        assertEquals(emptySet<String>(), narrowed.selection.mains)
        assertTrue(narrowed.isPicked("Jazz"))
        assertTrue(narrowed.isSelected(modal, "Jazz"))
        assertFalse(narrowed.isSelected(fusion, "Jazz"))

        // A picked genre whose sub-genres are not showing is focused first, then a second tap unpicks it.
        model.tapMainInCard("Electronic")
        model.awaitState { it.selection.mains == setOf("Electronic") }
        model.tapMainInCard("Jazz")
        assertEquals("Jazz", model.focusedMain.value)
        model.tapMainInCard("Jazz")
        assertEquals(null, model.focusedMain.value)
        model.awaitState { it.selection == QueueBuilderSelection(mains = setOf("Electronic")) && it.matchCount == 1L }
    }

    @Test
    fun aRememberedGenreNoLongerInTheLibraryNeitherShowsNorNarrowsTheBuild() {
        val picks = QueueBuilderSelection(mains = setOf("Reggae"), tags = setOf("polka", techno))
        runBlocking { prefs.setQueueBuilderPicks(1, QueueBuilderPicks(picks, 30)) }
        activeSource.value = 1
        val model = model()
        val state = model.awaitState { it.matchCount != null }
        assertEquals(QueueBuilderSelection(tags = setOf(techno)), state.selection)
        assertEquals(1L, state.matchCount)

        model.build()
        runBlocking { withTimeout(5_000) { model.building.first { !it } } }
        assertEquals(listOf(QueueBuilderSelection(tags = setOf(techno)) to 30), played)
    }

    @Test
    fun nothingMatchingCannotBuildAndAnEmptyBuildExplainsItself() {
        runBlocking {
            prefs.setQueueBuilderPicks(
                1,
                QueueBuilderPicks(QueueBuilderSelection(tags = setOf(techno), decades = setOf(1960))),
            )
        }
        activeSource.value = 1
        val model = model()
        val empty = model.awaitState { it.matchCount != null }
        assertEquals(0L, empty.matchCount)
        assertFalse(empty.canBuild)
        model.build()
        assertEquals(emptyList<Pair<QueueBuilderSelection, Int>>(), played)

        model.clearDecades()
        model.awaitState { it.matchCount == 1L }
        playResult = QueueBuilderOutcome(false)
        model.build()
        runBlocking { withTimeout(5_000) { model.building.first { !it } } }
        assertEquals(listOf(UiMessage(R.string.queue_builder_no_match)), messages)
    }

    @Test
    fun moodsArePickedOnlyWithAudioMuseAndSavedPicksReturnWithIt() {
        activeSource.value = 1
        moodsAvailable.value = true
        val model = model()
        model.awaitState { it.moodsAvailable && it.matchCount == 3L }
        model.toggleMain("Jazz")
        model.toggleMood(QueueBuilderMood.HAPPY)
        model.toggleMood(QueueBuilderMood.PARTY)
        model.toggleMood(QueueBuilderMood.PARTY)
        val picked = model.awaitState { it.selection.moods == setOf(QueueBuilderMood.HAPPY) }
        // Moods rank the matches; they do not change how many songs the genre and decade picks match.
        assertEquals(2L, picked.matchCount)
        val saved = runBlocking { withTimeout(5_000) { prefs.queueBuilderPicks(1).first { it.selection.mains.isNotEmpty() } } }
        assertEquals(setOf(QueueBuilderMood.HAPPY), saved.selection.moods)

        model.build()
        runBlocking { withTimeout(5_000) { model.building.first { !it } } }
        assertEquals(QueueBuilderSelection(mains = setOf("Jazz"), moods = setOf(QueueBuilderMood.HAPPY)), played.last().first)

        // AudioMuse removed: moods neither show nor reach the build, but stay saved for when it returns.
        moodsAvailable.value = false
        val hidden = model.awaitState { !it.moodsAvailable }
        assertEquals(QueueBuilderSelection(mains = setOf("Jazz")), hidden.selection)
        model.build()
        runBlocking { withTimeout(5_000) { model.building.first { !it } } }
        assertEquals(QueueBuilderSelection(mains = setOf("Jazz")), played.last().first)
        moodsAvailable.value = true
        model.awaitState { it.selection.moods == setOf(QueueBuilderMood.HAPPY) }

        model.reset()
        model.awaitState { it.selection == QueueBuilderSelection() }
    }

    @Test
    fun aMoodLimitedBuildExplainsItself() {
        activeSource.value = 1
        moodsAvailable.value = true
        val model = model()
        model.awaitState { it.matchCount == 3L }
        model.toggleMood(QueueBuilderMood.SAD)
        model.awaitState { it.selection.moods.isNotEmpty() }
        for (outcome in listOf(
            QueueBuilderOutcome(false, MoodShortfall.SCORES_UNAVAILABLE),
            QueueBuilderOutcome(false, MoodShortfall.NO_MOOD_MATCH),
            QueueBuilderOutcome(true, MoodShortfall.PARTIAL),
            QueueBuilderOutcome(true),
        )) {
            playResult = outcome
            model.build()
            runBlocking { withTimeout(5_000) { model.building.first { !it } } }
        }
        assertEquals(
            listOf(
                R.string.queue_builder_moods_unavailable,
                R.string.queue_builder_moods_no_match,
                R.string.queue_builder_moods_partial,
            ).map { UiMessage(it) },
            messages,
        )
    }

    @Test
    fun offlineTheIndexOnlyOffersDownloadedSongs() {
        activeSource.value = 1
        offline.value = true
        val model = model()
        val state = model.awaitState { it.matchCount != null }
        assertEquals(true, state.offline)
        assertEquals(emptyList<String>(), state.index.columns.map { it.main })
        assertEquals(0L, state.matchCount)
    }

    private suspend fun seed() {
        db.sourcesDao().upsertSource(Source(1, "home", "http://localhost", true, 1))
        val songs = listOf(Triple("a", "Modal", 1965L), Triple("b", "Fusion", 1992L), Triple("c", "Techno", 1994L))
        db.libraryDao().upsertAlbums(
            songs.map { (id, _, year) -> Album(1, "al-$id", null, "Album $id", null, 0, null, null, year, null, 1) },
        )
        db.libraryDao().upsertSongs(songs.map { (id, _, _) -> Song(1, id, "al-$id", null, id, "Album $id", null, 200, 1, 1, null) })
        db.useWriterConnection { connection ->
            songs.forEach { (id, genre, _) ->
                connection.usePrepared("INSERT INTO song_genres (sourceId, songId, position, genre) VALUES (1, ?, 0, ?)") {
                    it.bindText(1, id)
                    it.bindText(2, genre)
                    it.step()
                }
            }
        }
    }
}
