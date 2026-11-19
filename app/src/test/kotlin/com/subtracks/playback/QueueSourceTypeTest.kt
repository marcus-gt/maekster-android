package com.subtracks.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.QueueKind
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Now Playing's second line for every stored source, as #54 asks: a type, never an album name. */
@RunWith(AndroidJUnit4::class)
class QueueSourceTypeTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private fun type(
        label: String?,
        contextKind: QueueKind? = null,
        generated: Boolean = false,
    ) = nowPlayingSourceType(label, contextKind, generated, resources)

    @Test
    fun everyNamedKindShowsItsTypeRatherThanItsName() {
        val expected =
            mapOf(
                QueueKind.Album to "Album",
                QueueKind.Song to "Album",
                QueueKind.Playlist to "Playlist",
                QueueKind.Artist to "Artist",
                QueueKind.ArtistAppearances to "Artist",
                QueueKind.Genre to "Genre",
                QueueKind.Downloaded to "Downloads",
                QueueKind.Starred to "Favorites",
                QueueKind.Library to "Songs",
                QueueKind.Order to "Songs",
            )
        assertEquals(QueueKind.entries.toSet(), expected.keys)
        for ((kind, title) in expected) {
            assertEquals(kind.name, title, type(NamedQueueSource.label(kind, "Love and Theft")))
        }
    }

    @Test
    fun everyCollectionShowsItsOwnName() {
        val expected =
            mapOf(
                QueueCollection.Songs to "Songs",
                QueueCollection.Favorites to "Favorites",
                QueueCollection.Downloads to "Downloads",
                QueueCollection.SmartShuffle to "Smart Shuffle",
                QueueCollection.InstantMix to "Instant Mix",
                QueueCollection.ArtistRadio to "Artist radio",
                QueueCollection.QueueBuilder to "Queue Builder",
                QueueCollection.MoodNight to "Night radio",
                QueueCollection.MoodEnergetic to "Energetic radio",
                QueueCollection.MoodWorkout to "Workout radio",
                QueueCollection.MoodChill to "Chill radio",
                QueueCollection.MoodFocus to "Focus radio",
                QueueCollection.SongPath to "Song Path",
            )
        assertEquals(QueueCollection.entries.toSet(), expected.keys)
        for ((collection, title) in expected) {
            assertEquals(collection.name, title, type(collection.storedLabel, QueueKind.Song))
        }
        assertEquals("Bob Dylan radio", type(QueueCollection.artistRadioLabel("Bob Dylan"), QueueKind.Song))
    }

    @Test
    fun autoplayAfterAFiniteSourceIsSimilarToItWhileMixesKeepTheirName() {
        assertEquals("Similar to Love and Theft", type(NamedQueueSource.label(QueueKind.Album, "Love and Theft"), generated = true))
        assertEquals("Similar to Jazz tunes", type(NamedQueueSource.label(QueueKind.Playlist, "Jazz tunes"), generated = true))
        assertEquals("Similar to Favorites", type(QueueCollection.Favorites.storedLabel, generated = true))
        assertEquals("Smart Shuffle", type(QueueCollection.SmartShuffle.storedLabel, generated = true))
        assertEquals("Bob Dylan radio", type(QueueCollection.artistRadioLabel("Bob Dylan"), generated = true))
        assertEquals("Autoplay", type(null, QueueKind.Song, generated = true))
    }

    @Test
    fun oldPlainNameLabelsAndUnlabelledQueuesFallBackToTheCurrentKind() {
        assertEquals("Playlist", type("Jazz tunes", QueueKind.Playlist))
        assertEquals("Similar to Jazz tunes", type("Jazz tunes", QueueKind.Song, generated = true))
        assertEquals("Album", type(null, QueueKind.Album))
        assertEquals("Songs", type(null, QueueKind.Song))
        assertEquals("Queue", type(null, null))
    }

    @Test
    fun theQueueHeadingStillReadsTheName() {
        assertEquals("Love and Theft", QueueCollection.displayName(NamedQueueSource.label(QueueKind.Album, "Love and Theft"), resources))
        assertEquals("AC/DC: Live", QueueCollection.displayName(NamedQueueSource.label(QueueKind.Artist, "AC/DC: Live"), resources))
    }
}
