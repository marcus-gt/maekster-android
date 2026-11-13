package com.subtracks.ui.playlist

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.model.Playlist
import com.subtracks.ui.theme.MaeksterTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.PixelXL)
class AddToPlaylistSheetTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val recent = Playlist(1, "recent", "Road trip", null, null, 12, 1)
    private val all = (0 until 20).map { recent.copy(id = "p$it", name = "Playlist $it") }
    private val state = mutableStateOf(AddToPlaylistUi(recent = listOf(recent)))
    private val picks = mutableListOf<String>()
    private val choices = mutableListOf<Boolean>()
    private var creates = 0

    @Suppress("DEPRECATION")
    private fun show(fontScale: Float = 1f) {
        val resources = rule.activity.resources
        val configuration =
            android.content.res
                .Configuration(resources.configuration)
                .apply { this.fontScale = fontScale }
        resources.updateConfiguration(configuration, resources.displayMetrics)
        val data = MutableStateFlow(PagingData.from(all))
        rule.setContent {
            val destinations = data.collectAsLazyPagingItems()
            run {
                MaeksterTheme {
                    AddToPlaylistSheet(
                        ui = state.value,
                        destinations = destinations,
                        coverArt = { null },
                        onQuery = { state.value = state.value.copy(query = it) },
                        onNewName = { state.value = state.value.copy(newName = it) },
                        onCreate = { creates++ },
                        onPick = { picks += it.id },
                        onResolveDuplicates = { choices += it },
                        onDismissDuplicates = { state.value = state.value.copy(duplicatesIn = null) },
                        onDismiss = {},
                    )
                }
            }
        }
    }

    @Test
    fun fieldsAndDestinationRowsDispatchTheirActions() {
        show()
        rule.onNodeWithText("Create").assertIsNotEnabled()
        rule.onNodeWithText("New playlist name").performTextInput("Evening")
        rule.onNodeWithText("Create").performClick()
        assertEquals(1, creates)
        rule.onNodeWithText("Search playlists").performTextInput("mix")
        assertEquals("mix", state.value.query)
        rule.onNodeWithTag(addToPlaylistRowTag("recent")).performScrollTo().performClick()
        rule.onNodeWithTag("playlist-destinations").performScrollToNode(hasTestTag(addToPlaylistRowTag("p19")))
        rule.onNodeWithTag(addToPlaylistRowTag("p19")).performClick()
        assertEquals(listOf("recent", "p19"), picks)
    }

    @Test
    fun busyDisablesWritesAndDuplicateButtonsKeepTheirMeaning() {
        state.value = state.value.copy(newName = "Evening", busy = "recent")
        show()
        rule.onNodeWithText("Create").assertIsNotEnabled()
        rule.onNodeWithTag(addToPlaylistRowTag("recent")).performScrollTo().assertIsNotEnabled()
        rule.runOnIdle { state.value = state.value.copy(busy = null, duplicatesIn = recent) }
        rule.onNodeWithText("Skip duplicates").performClick()
        rule.onNodeWithText("Add anyway").performClick()
        assertEquals(listOf(true, false), choices)
    }

    @Test
    fun normalLayoutRenders() {
        show()
        rule.onNodeWithTag(addToPlaylistRowTag("recent")).assertIsDisplayed()
        rule.onNodeWithTag(ADD_TO_PLAYLIST_TAG).captureRoboImage("src/test/screenshots/add_to_playlist.png")
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp")
    fun largeFontSmallWindowCanReachTheLastPlaylistAndReturnToCreate() {
        show(fontScale = 2f)
        rule.onNodeWithTag("playlist-destinations").performScrollToNode(hasTestTag(addToPlaylistRowTag("p19")))
        rule.onNodeWithTag(addToPlaylistRowTag("p19")).assertIsDisplayed().performClick()
        assertEquals(listOf("p19"), picks)
        rule.onNodeWithTag("playlist-destinations").performScrollToIndex(0)
        rule.onNodeWithText("New playlist name").performScrollTo().performTextInput("Evening")
        rule.onNodeWithText("Create").performScrollTo().performClick()
        assertEquals(1, creates)
        rule.onNodeWithTag(ADD_TO_PLAYLIST_TAG).captureRoboImage("src/test/screenshots/add_to_playlist_large_font.png")
    }
}
