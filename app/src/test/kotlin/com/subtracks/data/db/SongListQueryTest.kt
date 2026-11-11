package com.subtracks.data.db

import com.subtracks.data.prefs.SongSort
import com.subtracks.data.prefs.SortNameMode
import com.subtracks.data.prefs.StarredFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongListQueryTest {
    @Test
    fun aSpecSurvivesEncodingIncludingASearchWithTheSeparator() {
        val spec =
            SongListSpec(
                sort = SongSort.Added,
                descending = true,
                starred = StarredFilter.NotStarred,
                downloaded = true,
                search = "a|b|c",
                sortNameMode = SortNameMode.NonLatin,
            )

        assertEquals(spec, SongListSpec.decode(spec.encode()))
    }

    @Test
    fun anythingElseDoesNotDecode() {
        assertNull(SongListSpec.decode(""))
        assertNull(SongListSpec.decode("v2|Name|0|Any|0|SortName|"))
        assertNull(SongListSpec.decode("v1|Rating|0|Any|0|SortName|"))
        assertNull(SongListSpec.decode("v1|Name|0|Any"))
    }

    @Test
    fun reversingFlipsEveryTermAndAddedIsNewestFirstByDefault() {
        assertEquals(
            "songs.created DESC, songs.sortName COLLATE NOCASE, songs.id",
            songOrder(SongListSpec(sort = SongSort.Added)),
        )
        assertEquals(
            "songs.created, songs.sortName COLLATE NOCASE DESC, songs.id DESC",
            songOrder(SongListSpec(sort = SongSort.Added, descending = true)),
        )
    }
}
