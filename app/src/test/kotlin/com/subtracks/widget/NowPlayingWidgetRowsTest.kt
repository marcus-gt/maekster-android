package com.subtracks.widget

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.R
import com.subtracks.data.model.Artist
import com.subtracks.data.model.QueueKind
import com.subtracks.playback.PlaybackState
import com.subtracks.playback.QueueCollection
import com.subtracks.playback.QueueContext
import com.subtracks.playback.QueueItem
import com.subtracks.ui.theme.MaeksterTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class NowPlayingWidgetRowsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val renderer = NowPlayingWidgetRenderer(context)
    private val art = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    private fun content(
        row: NowPlayingWidgetRow,
        items: List<NowPlayingRowItem>,
        moods: Boolean = true,
    ) = NowPlayingWidgetContent(
        title = "Song",
        artist = "Artist",
        coverArtId = null,
        isPlaying = true,
        live = true,
        hasNext = true,
        moods = moods,
        rows = NowPlayingRows(items = mapOf(row to items)),
    )

    private fun inflate(
        row: NowPlayingWidgetRow,
        items: List<NowPlayingRowItem> = emptyList(),
        moods: Boolean = true,
    ): View = renderer.views(NowPlayingWidgetLayout.Moods, content(row, items, moods), null, row).apply(context, FrameLayout(context))

    private val tiles = listOf(R.id.widget_row_tile_0, R.id.widget_row_tile_1, R.id.widget_row_tile_2)

    @Test
    fun aWidgetWithoutAChoiceShowsTheMoodRow() {
        val rows = NowPlayingRows(choices = mapOf(7 to NowPlayingWidgetRow.ArtistRadio))

        assertEquals(NowPlayingWidgetRow.ArtistRadio, rows.choice(7))
        assertEquals(NowPlayingWidgetRow.Moods, rows.choice(8))
        assertEquals(NowPlayingWidgetRow.Moods, NowPlayingWidgetRow.of(null))
        assertEquals(NowPlayingWidgetRow.Moods, NowPlayingWidgetRow.of("Retired"))

        val view = inflate(NowPlayingWidgetRow.Moods)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widget_moods).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.widget_row_tiles).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.widget_row_songs).visibility)
    }

    @Test
    fun albumTilesShowTheirCoversAndThePlayingAlbumShowsPause() {
        val items =
            listOf(
                NowPlayingRowItem(1, "a", "Album A", art = art, isPlaying = true),
                NowPlayingRowItem(1, "b", "Album B", art = art),
            )
        val view = inflate(NowPlayingWidgetRow.RecentlyAdded, items)

        assertEquals(View.GONE, view.findViewById<View>(R.id.widget_moods).visibility)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widget_row_tiles).visibility)
        assertEquals("Pause: Album A", view.findViewById<View>(tiles[0]).contentDescription)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widget_row_tile_state_0).visibility)
        assertEquals("Album B", view.findViewById<View>(tiles[1]).contentDescription)
        assertEquals(View.GONE, view.findViewById<View>(R.id.widget_row_tile_state_1).visibility)
        assertEquals(View.INVISIBLE, view.findViewById<View>(tiles[2]).visibility)
    }

    @Test
    fun aTileTapGoesToTheWidgetThatPlaysThatKindOfItem() {
        fun tapped(
            row: NowPlayingWidgetRow,
            item: NowPlayingRowItem,
        ): String? {
            inflate(row, listOf(item))
                .findViewById<View>(
                    if (row ==
                        NowPlayingWidgetRow.FavoriteSongs
                    ) {
                        R.id.widget_row_song_0
                    } else {
                        tiles[0]
                    },
                ).performClick()
            return shadowOf(context as Application)
                .broadcastIntents
                .lastOrNull()
                ?.component
                ?.className
        }

        val album = NowPlayingRowItem(1, "a", "Album A")
        assertEquals(RecentlyAddedAlbumsWidget::class.java.name, tapped(NowPlayingWidgetRow.RecentlyAdded, album))
        assertEquals(RecentlyPlayedAlbumsWidget::class.java.name, tapped(NowPlayingWidgetRow.RecentlyPlayed, album))
        assertEquals(RadioStationsWidget::class.java.name, tapped(NowPlayingWidgetRow.ArtistRadio, NowPlayingRowItem(1, "x", "Björk")))
        assertEquals(FavoriteSongsWidget::class.java.name, tapped(NowPlayingWidgetRow.FavoriteSongs, NowPlayingRowItem(1, "s", "Song")))

        val radio =
            shadowOf(context as Application).broadcastIntents.first {
                it.component?.className ==
                    RadioStationsWidget::class.java.name
            }
        assertEquals(WidgetStationTap(1, "x", "Björk"), RadioStationsWidget.stationOf(radio))
    }

    @Test
    fun artistTilesAreNamedAsTheirRadio() {
        val view = inflate(NowPlayingWidgetRow.ArtistRadio, listOf(NowPlayingRowItem(1, "x", "Björk", isPlaying = false)))

        assertEquals("Play: Björk radio", view.findViewById<View>(tiles[0]).contentDescription)
    }

    @Test
    fun favoriteSongsAreAShortListAndThePlayingSongStandsOut() {
        val items =
            listOf(
                NowPlayingRowItem(1, "s1", "First", subtitle = "Artist", isPlaying = true),
                NowPlayingRowItem(1, "s2", "Second", subtitle = null),
            )
        val view = inflate(NowPlayingWidgetRow.FavoriteSongs, items)

        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widget_row_songs).visibility)
        val first = view.findViewById<TextView>(R.id.widget_row_song_0)
        assertEquals("First · Artist", first.text)
        assertEquals(context.getColor(R.color.widget_accent), first.currentTextColor)
        assertEquals("Second", view.findViewById<TextView>(R.id.widget_row_song_1).text)
        assertEquals(context.getColor(R.color.widget_text_primary), view.findViewById<TextView>(R.id.widget_row_song_1).currentTextColor)
        assertEquals(View.INVISIBLE, view.findViewById<View>(R.id.widget_row_song_2).visibility)
    }

    @Test
    fun nothingAnEmptyRowOrUnavailableMoodsGiveThePlayerTheRoom() {
        assertNull(inflate(NowPlayingWidgetRow.Nothing, listOf(NowPlayingRowItem(1, "a", "A"))).findViewById<View>(R.id.widget_row_tiles))
        assertNull(inflate(NowPlayingWidgetRow.RecentlyPlayed).findViewById<View>(R.id.widget_row_tiles))
        assertNull(inflate(NowPlayingWidgetRow.Moods, moods = false).findViewById<View>(R.id.widget_row_tiles))
        assertTrue(inflate(NowPlayingWidgetRow.Moods).findViewById<View>(R.id.widget_row_tiles) != null)
    }

    @Test
    fun thePlayStateFollowsTheAlbumTheSongOrTheArtistRadioThePlayerHolds() {
        val item = QueueItem(id = "s1", title = "Song", artist = null, album = null, coverArtId = null, albumId = "a")
        val playing = PlaybackState(item = item, isPlaying = true, context = QueueContext(QueueKind.Album, sourceId = 1, refId = "a"))
        val albums = listOf(NowPlayingRowItem(1, "a", "A"), NowPlayingRowItem(1, "b", "B"))
        assertEquals(listOf(true, null), albums.withPlayState(NowPlayingWidgetRow.RecentlyAdded, 1, playing, null).map { it.isPlaying })
        assertEquals(
            listOf(false, null),
            albums.withPlayState(NowPlayingWidgetRow.RecentlyAdded, 1, playing.copy(isPlaying = false), null).map { it.isPlaying },
        )
        assertEquals(listOf(null, null), albums.withPlayState(NowPlayingWidgetRow.RecentlyAdded, 2, playing, null).map { it.isPlaying })

        val songs = listOf(NowPlayingRowItem(1, "s0", "Other"), NowPlayingRowItem(1, "s1", "Song"))
        assertEquals(listOf(null, true), songs.withPlayState(NowPlayingWidgetRow.FavoriteSongs, 1, playing, null).map { it.isPlaying })

        val artists = listOf(NowPlayingRowItem(1, "x", "Björk"), NowPlayingRowItem(1, "y", "Can"))
        val label = QueueCollection.artistRadioLabel("Can")
        assertEquals(listOf(null, true), artists.withPlayState(NowPlayingWidgetRow.ArtistRadio, 1, playing, label).map { it.isPlaying })
        assertEquals(listOf(null, null), artists.withPlayState(NowPlayingWidgetRow.ArtistRadio, 1, playing, null).map { it.isPlaying })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun onlyChosenRowsAreReadAndCoversLoadOnceWhilePlaybackChanges() =
        runTest {
            val coverLoads = mutableListOf<String>()
            var favoriteReads = 0
            var artistReads = 0
            val choices = MutableStateFlow(mapOf(1 to NowPlayingWidgetRow.RecentlyAdded, 2 to NowPlayingWidgetRow.Nothing))
            val playback = MutableStateFlow(PlaybackState())
            val sources =
                NowPlayingRowSources(
                    recentlyAdded = { _, _ -> flowOf((1..7).map { WidgetAlbum("a$it", "Album $it", "c$it") }) },
                    recentlyPlayed = { _, _ -> error("not chosen") },
                    favoriteSongs = { _, _ ->
                        favoriteReads++
                        flowOf(emptyList())
                    },
                    stationArtists = {
                        artistReads++
                        flowOf(listOf(Artist(1, "x", "Björk", 1, null)))
                    },
                    albumArt = { _, cover ->
                        coverLoads += cover
                        null
                    },
                    artistArt = { null },
                )
            val seen = mutableListOf<NowPlayingRows>()
            val job = launch { nowPlayingRows(choices, flowOf(1L), playback, flowOf(null), sources).collect { seen += it } }
            runCurrent()

            val albums = seen.last().items.getValue(NowPlayingWidgetRow.RecentlyAdded)
            assertEquals((1..5).map { "a$it" }, albums.map { it.refId })
            assertEquals(setOf(NowPlayingWidgetRow.RecentlyAdded), seen.last().items.keys)
            assertEquals((1..5).map { "c$it" }, coverLoads)
            assertEquals(0, favoriteReads + artistReads)

            val song = QueueItem(id = "s", title = "S", artist = null, album = null, coverArtId = null, albumId = "a2")
            playback.value = PlaybackState(item = song, isPlaying = true, context = QueueContext(QueueKind.Album, 1, "a2"))
            runCurrent()
            assertEquals(
                true,
                seen
                    .last()
                    .items
                    .getValue(NowPlayingWidgetRow.RecentlyAdded)[1]
                    .isPlaying,
            )
            assertEquals(5, coverLoads.size)

            choices.value = mapOf(1 to NowPlayingWidgetRow.ArtistRadio)
            runCurrent()
            assertEquals(
                listOf("Björk"),
                seen
                    .last()
                    .items
                    .getValue(NowPlayingWidgetRow.ArtistRadio)
                    .map { it.title },
            )
            assertEquals(1, artistReads)
            job.cancel()
        }

    @Test
    fun theSettingsListEveryChoiceAndATapPicksIt() {
        var picked: NowPlayingWidgetRow? = null
        composeRule.setContent { MaeksterTheme { NowPlayingRowChoice(NowPlayingWidgetRow.FavoriteSongs) { picked = it } } }

        composeRule.onNodeWithTag("widgetRow.FavoriteSongs").assertIsSelected()
        composeRule.onNodeWithTag("widgetRow.Moods").assertIsNotSelected()
        NowPlayingWidgetRow.entries.forEach { composeRule.onNodeWithTag("widgetRow.${it.name}").assertExists() }
        composeRule.onNodeWithTag("widgetRow.ArtistRadio").performClick()

        assertEquals(NowPlayingWidgetRow.ArtistRadio, picked)
    }
}
