package com.subtracks.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.paging.PagingData
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.R
import com.subtracks.data.model.AlbumCollectionKind
import com.subtracks.data.model.AlbumCollectionSummary
import com.subtracks.ui.theme.MaeksterTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AlbumCollectionsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun genreOpensItsAlbumCollection() = collection(LibraryTab.Genres, 1f)

    @Test
    fun labelOpensItsAlbumCollectionAtLargeFont() = collection(LibraryTab.Labels, 2f)

    private fun collection(
        tab: LibraryTab,
        fontScale: Float,
    ) {
        val opened = mutableListOf<Pair<AlbumCollectionKind, String>>()
        val rows = MutableStateFlow(PagingData.from(listOf(AlbumCollectionSummary("Rock / Pop", 12, null))))
        composeRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                MaeksterTheme {
                    LibraryListScreen(
                        list = tab,
                        coverArt = { _, _ -> null },
                        onSync = {},
                        onBack = {},
                        collections = rows,
                        onCollectionClick = { kind, name -> opened += kind to name },
                    )
                }
            }
        }
        composeRule.onNodeWithText("Rock / Pop").performClick()
        assertEquals(listOf(checkNotNull(tab.collectionKind) to "Rock / Pop"), opened)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/collection_${tab.name}_$fontScale.png")
    }

    @Test
    fun emptyLabelsExplainMissingMetadata() {
        val settled = androidx.paging.LoadState.NotLoading(endOfPaginationReached = true)
        val rows =
            MutableStateFlow(
                PagingData.empty<AlbumCollectionSummary>(sourceLoadStates = androidx.paging.LoadStates(settled, settled, settled)),
            )
        composeRule.setContent {
            MaeksterTheme {
                LibraryListScreen(
                    list = LibraryTab.Labels,
                    coverArt = { _, _ -> null },
                    onSync = {},
                    onBack = {},
                    collections = rows,
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/collection_empty.png")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(composeRule.activity.getString(R.string.labels_empty)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
