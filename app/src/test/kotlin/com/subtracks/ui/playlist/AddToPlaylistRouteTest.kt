package com.subtracks.ui.playlist

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.R
import com.subtracks.UiMessage
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Song
import com.subtracks.data.model.Source
import com.subtracks.data.repo.PlaylistRepository
import com.subtracks.data.source.FakePlaylistWriter
import com.subtracks.ui.theme.MaeksterTheme
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddToPlaylistRouteTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun failureKeepsTheSheetAndDraftThenSuccessfulRetryDismissesIt() {
        val db = createTestDatabase()
        val server = FakePlaylistWriter(1)
        val messages = CopyOnWriteArrayList<UiMessage>()
        val dismissals = AtomicInteger()
        val shown = mutableStateOf(true)
        runBlocking {
            db.sourcesDao().upsertSource(Source(1, "fixture", "http://localhost", true, 1))
            db.libraryDao().upsertSongs(listOf(Song(1, "s", null, null, "Song", null, null, 60, null, null, null)))
        }
        val model =
            AddToPlaylistViewModel(
                AddToPlaylistRequest(1, listOf("s")),
                PlaylistRepository(db, writerFor = { server }),
                owner = { "tester" },
                showMessage = { messages += it },
            )
        try {
            rule.setContent {
                MaeksterTheme {
                    if (shown.value) {
                        AddToPlaylistContent(
                            viewModel = model,
                            onDismiss = {
                                dismissals.incrementAndGet()
                                shown.value = false
                            },
                            coverArt = { null },
                        )
                    }
                }
            }
            rule.onNodeWithText("New playlist name").performTextInput("Evening")
            server.failNext = FakePlaylistWriter.Failure.AfterFirstSong
            rule.onNodeWithText("Create").performClick()
            rule.waitUntil(10_000) {
                rule.runOnIdle {}
                model.ui.value.error != null && model.ui.value.busy == null
            }
            rule.onNodeWithTag(ADD_TO_PLAYLIST_TAG).assertIsDisplayed()
            rule.onNode(hasText("Evening") and hasSetTextAction()).assertIsDisplayed()
            assertEquals(0, dismissals.get())
            assertEquals(emptyList<UiMessage>(), messages.toList())

            rule.onNodeWithText("Create").performClick()
            rule.waitUntil(10_000) {
                rule.runOnIdle {}
                dismissals.get() == 1
            }
            rule.onNodeWithTag(ADD_TO_PLAYLIST_TAG).assertDoesNotExist()
            assertEquals(1, server.creates)
            assertEquals(listOf("s"), server.songs("new1"))
            assertEquals(listOf(UiMessage(R.string.playlist_created, listOf("Evening"))), messages.toList())
            runBlocking { assertEquals(listOf("s"), db.queueDao().playlistSongIds(1, "new1")) }
        } finally {
            rule.runOnIdle {
                shown.value = false
                model.viewModelScope.cancel()
            }
            rule.waitForIdle()
            db.close()
        }
    }
}
