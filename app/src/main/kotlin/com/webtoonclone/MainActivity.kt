package com.webtoonclone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.webtoonclone.notify.EXTRA_SERIES_ID
import com.webtoonclone.ui.home.HomeScreen
import com.webtoonclone.ui.home.HomeViewModel
import com.webtoonclone.ui.library.LibraryScreen
import com.webtoonclone.ui.library.LibraryViewModel
import com.webtoonclone.ui.reader.ReaderScreen
import com.webtoonclone.ui.reader.ReaderViewModel
import com.webtoonclone.ui.search.SearchScreen
import com.webtoonclone.ui.search.SearchViewModel
import com.webtoonclone.ui.series.SeriesScreen
import com.webtoonclone.ui.series.SeriesViewModel
import com.webtoonclone.ui.theme.Green
import com.webtoonclone.ui.updates.UpdatesScreen
import com.webtoonclone.ui.updates.UpdatesViewModel
import com.webtoonclone.ui.theme.WebtoonTheme

class MainActivity : ComponentActivity() {
    /** Counts app opens. The home screen reshuffles its picks when this changes. */
    private var openCount by mutableIntStateOf(0)

    /** Set when a notification opens the app. Consumed once by the navigation host. */
    private var pendingSeries by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        pendingSeries = intent.getStringExtra(EXTRA_SERIES_ID)
    }

    override fun onStart() {
        super.onStart()
        openCount++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app draws inside the system bars. Transparent bars with light icons suit the dark theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        pendingSeries = intent.getStringExtra(EXTRA_SERIES_ID)
        setContent { WebtoonNav(openCount, pendingSeries) { pendingSeries = null } }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Default.Home),
    Tab("search", "Search", Icons.Default.Search),
    Tab("updates", "Updates", Icons.Default.Refresh),
    Tab("library", "My Series", Icons.Default.Favorite),
)

@Composable
private fun WebtoonNav(openCount: Int, openSeries: String?, onOpened: () -> Unit) {
    val app = LocalContext.current.applicationContext as WebtoonApp
    val nav = rememberNavController()
    val route by nav.currentBackStackEntryAsState().let { entry ->
        androidx.compose.runtime.derivedStateOf { entry.value?.destination?.route }
    }
    LaunchedEffect(openSeries) {
        if (openSeries != null) {
            nav.navigate("series/$openSeries")
            onOpened()
        }
    }
    val onTab = route?.substringBefore('?') in tabs.map { it.route }

    WebtoonTheme {
      // One inset pad for the whole app keeps every screen between the status and navigation bars.
      Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = { if (onTab) BottomBar(nav, route?.substringBefore('?')) },
        ) { padding ->
            NavHost(
                nav,
                startDestination = "home",
                modifier = Modifier.padding(padding),
            ) {
                composable("home") {
                    val vm = viewModel { HomeViewModel(app.repository) }
                    Box(Modifier.fillMaxSize()) {
                        HomeScreen(
                            vm,
                            onOpenSeries = { nav.navigate("series/$it") },
                            onOpenSearch = { nav.navigateTab("search") },
                            openCount = openCount,
                        )
                    }
                }
                composable("search?genre={genre}") { entry ->
                    val vm = viewModel { SearchViewModel(app.repository, app.libraryStore) }
                    Box(Modifier.fillMaxSize()) {
                        SearchScreen(vm, entry.arguments?.getString("genre"), onOpenSeries = { nav.navigate("series/$it") })
                    }
                }
                composable("updates") {
                    val vm = viewModel { UpdatesViewModel(app.repository) }
                    Box(Modifier.fillMaxSize()) {
                        UpdatesScreen(vm, onOpenSeries = { nav.navigate("series/$it") })
                    }
                }
                composable("library") {
                    val vm = viewModel { LibraryViewModel(app.libraryStore) }
                    Box(Modifier.fillMaxSize()) {
                        LibraryScreen(vm, onOpenSeries = { nav.navigate("series/$it") }, onOpenSearch = { nav.navigateTab("search") })
                    }
                }
                composable("series/{seriesId}") { entry ->
                    val seriesId = entry.arguments!!.getString("seriesId")!!
                    val vm = viewModel { SeriesViewModel(seriesId, app.repository, app.libraryStore) }
                    SeriesScreen(
                        vm,
                        onOpenChapter = { nav.navigate("series/$seriesId/$it") },
                        onHome = { nav.navigateTab("home") },
                    )
                }
                composable("series/{seriesId}/{chapterId}") { entry ->
                    val seriesId = entry.arguments!!.getString("seriesId")!!
                    val chapterId = entry.arguments!!.getString("chapterId")!!
                    val vm = viewModel(key = chapterId) {
                        ReaderViewModel(seriesId, chapterId, app.repository, app.progressStore, app.libraryStore)
                    }
                    ReaderScreen(
                        vm,
                        seriesId,
                        onOpenChapter = { nav.navigate("series/$seriesId/$it") { popUpTo("series/{seriesId}") } },
                        onBack = { nav.popBackStack("series/{seriesId}", inclusive = false) },
                    )
                }
            }
        }
      }
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomBar(nav: NavHostController, current: String?) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = current == tab.route,
                onClick = { nav.navigateTab(if (tab.route == "search") "search" else tab.route) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Green,
                    selectedTextColor = Green,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = Color(0xFF8A8A8A),
                    unselectedTextColor = Color(0xFF8A8A8A),
                ),
            )
        }
    }
}
