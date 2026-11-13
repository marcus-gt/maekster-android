package com.subtracks.data.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistAppendIntentTest {
    @Test
    fun nothingArrivedSendsEverythingInOrder() {
        val intent = PlaylistAppendIntent(listOf("a", "b", "b"), existingSongIds = listOf("x"))
        assertEquals(listOf("a", "b", "b"), intent.remaining(listOf("x")))
    }

    @Test
    fun aLostResponseIsNotRepeated() {
        val intent = PlaylistAppendIntent(listOf("a", "b", "b"), existingSongIds = emptyList())
        assertEquals(emptyList<String>(), intent.remaining(listOf("a", "b", "b")))
    }

    @Test
    fun aPartialAppendRetriesOnlyMissingOccurrences() {
        val intent = PlaylistAppendIntent(listOf("a", "b", "b", "c"), existingSongIds = emptyList())
        assertEquals(listOf("b", "b", "c"), intent.remaining(listOf("a")))
        assertEquals(listOf("b", "c"), intent.remaining(listOf("a", "b")))
    }

    @Test
    fun duplicatesAlreadyThereDoNotCountAsArrived() {
        val intent = PlaylistAppendIntent(listOf("b"), existingSongIds = listOf("b", "b"))
        assertEquals(listOf("b"), intent.remaining(listOf("b", "b")))
        assertEquals(emptyList<String>(), intent.remaining(listOf("b", "b", "b")))
    }

    @Test
    fun songsSomeoneElseAddedAreLeftAlone() {
        val intent = PlaylistAppendIntent(listOf("a"), existingSongIds = listOf("x"))
        assertEquals(listOf("a"), intent.remaining(listOf("x", "y")))
        assertEquals(emptyList<String>(), intent.remaining(listOf("y", "a")))
    }

    @Test
    fun removalsElsewhereDoNotGoNegative() {
        val intent = PlaylistAppendIntent(listOf("a"), existingSongIds = listOf("a", "a"))
        assertEquals(listOf("a"), intent.remaining(emptyList()))
    }
}
