package com.subtracks.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.subtracks.R

/**
 * The app's top-level destinations. Each is a nested navigation graph ([graph]) whose start destination is the
 * tab's root screen ([root]), so every tab keeps its own back stack.
 */
enum class TopLevelTab(
    val graph: String,
    val root: String,
    @param:StringRes val label: Int,
    val icon: ImageVector,
) {
    Home("tab/home", "home", R.string.navigation_tabs_home, Icons.Rounded.Home),
    Library("tab/library", "library", R.string.navigation_tabs_library, Icons.Rounded.LibraryMusic),
    Discover("tab/discover", "discover", R.string.discover_title, Icons.Rounded.Explore),
    Search("tab/search", "search", R.string.search, Icons.Rounded.Search),
}

/**
 * The tab on screen, given the back stack's routes from bottom to top. Shared screens (album, artist, Settings)
 * belong to whichever tab root sits under them; Home is the start destination, so it is always somewhere below.
 */
internal fun currentTab(routes: List<String?>): TopLevelTab =
    routes.asReversed().firstNotNullOfOrNull { route -> TopLevelTab.entries.firstOrNull { it.root == route } }
        ?: TopLevelTab.Home

/**
 * [currentTab] for the controller's back stack right now, for use outside composition. `currentBackStack` is
 * restricted to androidx.navigation, but it is the only way to see which tab root is under a shared screen.
 */
@Suppress("RestrictedApi")
internal fun NavController.currentTabNow(): TopLevelTab = currentTab(currentBackStack.value.map { it.destination.route })

/**
 * Selects a tab. Switching saves the tab being left and restores the one being entered, so each keeps its
 * screens. Selecting the tab already on screen pops it back to its root.
 */
internal fun NavController.selectTab(
    tab: TopLevelTab,
    current: TopLevelTab,
) {
    if (tab == current) {
        popBackStack(tab.root, inclusive = false)
        return
    }
    navigate(tab.graph) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private const val INDICATOR_ALPHA = 0.16f

/**
 * The compact Material 3 bar: every tab keeps its label, and the selected one takes the accent. The label stays
 * in the regular text colour, because the light theme's orange is too light for small text.
 */
@Composable
internal fun MaeksterNavigationBar(
    current: TopLevelTab,
    onSelect: (TopLevelTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors =
        ShortNavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            selectedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = INDICATOR_ALPHA),
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    ShortNavigationBar(modifier = modifier) {
        TopLevelTab.entries.forEach { tab ->
            ShortNavigationBarItem(
                selected = tab == current,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
                colors = colors,
            )
        }
    }
}
