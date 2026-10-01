package com.dexter

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.dexter.data.Settings
import com.dexter.ui.LocalNavScope

internal data class Tab(val route: String, val label: String, val icon: ImageVector)

internal val tabs = listOf(
    Tab("home", "Home", Icons.Default.Home),
    Tab("search", "Search", Icons.Default.Search),
    Tab("updates", "Updates", Icons.Default.Refresh),
    Tab("library", "My Series", Icons.Default.Favorite),
    Tab("settings", "Settings", Icons.Default.Settings),
)

internal val tabRoutes = tabs.map { it.route }.toSet()

internal fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
internal fun SideRail(nav: NavHostController, current: String?, unread: Int) {
    val state = rememberWideNavigationRailState()
    WideNavigationRail(state = state) {
        tabs.forEach { tab ->
            WideNavigationRailItem(
                selected = current == tab.route,
                onClick = { nav.navigateTab(tab.route) },
                icon = { TabIcon(tab, unread) },
                label = { Text(tab.label) },
                railExpanded = state.targetValue == WideNavigationRailValue.Expanded,
            )
        }
    }
}

@Composable
internal fun BottomBar(nav: NavHostController, current: String?, unread: Int) {
    ShortNavigationBar {
        tabs.forEach { tab ->
            ShortNavigationBarItem(
                selected = current == tab.route,
                onClick = { nav.navigateTab(tab.route) },
                icon = { TabIcon(tab, unread) },
                label = { Text(tab.label) },
            )
        }
    }
}

/** A tab's icon. My Series carries a badge with the number of subscribed series that have unread chapters. */
@Composable
internal fun TabIcon(tab: Tab, unread: Int) {
    if (tab.route == "library" && unread > 0) {
        BadgedBox(badge = { Badge { Text(if (unread > 99) "99+" else unread.toString()) } }) {
            Icon(tab.icon, contentDescription = null)
        }
    } else {
        Icon(tab.icon, contentDescription = null)
    }
}

/** A destination that hands its animation scope to the screens in it, for shared cover transitions. */
internal fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    CompositionLocalProvider(LocalNavScope provides this) { content(entry) }
}
