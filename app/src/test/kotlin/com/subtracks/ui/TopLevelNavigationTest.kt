package com.subtracks.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The shell's tab behaviour, on a graph shaped like the app's: one nested graph per tab plus shared screens. */
@RunWith(AndroidJUnit4::class)
class TopLevelNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController

    private fun showShell() {
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController, startDestination = TopLevelTab.Home.graph) {
                TopLevelTab.entries.forEach { tab ->
                    navigation(startDestination = tab.root, route = tab.graph) {
                        composable(tab.root) { Text("root ${tab.root}") }
                    }
                }
                composable("detail/{id}") { entry -> Text("detail ${entry.arguments?.getString("id")}") }
            }
        }
        composeRule.waitForIdle()
    }

    private fun select(tab: TopLevelTab) {
        composeRule.runOnIdle { navController.selectTab(tab, navController.currentTabNow()) }
        composeRule.waitForIdle()
    }

    private fun open(id: String) {
        composeRule.runOnIdle { navController.navigate("detail/$id") }
        composeRule.waitForIdle()
    }

    private fun back() {
        composeRule.runOnIdle { navController.popBackStack() }
        composeRule.waitForIdle()
    }

    private fun assertShown(text: String) {
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun theAppStartsOnHome() {
        showShell()

        assertShown("root home")
        assertEquals(TopLevelTab.Home, navController.currentTabNow())
    }

    @Test
    fun eachTabKeepsItsOwnScreensWhenYouSwitchAway() {
        showShell()
        open("home-album")
        select(TopLevelTab.Library)
        open("library-album")

        select(TopLevelTab.Home)
        assertShown("detail home-album")
        assertEquals(TopLevelTab.Home, navController.currentTabNow())

        select(TopLevelTab.Library)
        assertShown("detail library-album")
        assertEquals(TopLevelTab.Library, navController.currentTabNow())
    }

    @Test
    fun aScreenOpenedFromATabBelongsToThatTab() {
        showShell()
        select(TopLevelTab.Search)
        open("found-artist")

        assertEquals(TopLevelTab.Search, navController.currentTabNow())
    }

    @Test
    fun selectingTheCurrentTabAgainReturnsToItsRoot() {
        showShell()
        select(TopLevelTab.Library)
        open("first")
        open("second")

        select(TopLevelTab.Library)

        assertShown("root library")
        assertEquals(TopLevelTab.Library, navController.currentTabNow())
    }

    @Test
    fun backFromAnotherTabsRootGoesHomeAndBackFromHomeLeavesTheApp() {
        showShell()
        select(TopLevelTab.Search)

        back()

        assertShown("root home")
        assertEquals(TopLevelTab.Home, navController.currentTabNow())
        // The shell only handles back while there is an entry to return to; with none, the system closes the app.
        composeRule.runOnIdle { assertNull(navController.previousBackStackEntry) }
    }

    @Test
    fun theTabOnScreenIsTheTopmostTabRootInTheStack() {
        assertEquals(TopLevelTab.Home, currentTab(emptyList()))
        assertEquals(TopLevelTab.Home, currentTab(listOf(null, "tab/home", "home", "album/{albumId}")))
        assertEquals(
            TopLevelTab.Library,
            currentTab(listOf(null, "tab/home", "home", "tab/library", "library", "artist/{artistId}", "settings")),
        )
    }
}
