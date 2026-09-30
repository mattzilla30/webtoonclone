package com.dexter

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dexter.data.ReaderBackground
import com.dexter.data.Settings
import com.dexter.notify.EXTRA_CHAPTER_ID
import com.dexter.notify.EXTRA_ROUTE
import com.dexter.notify.EXTRA_SERIES_ID
import com.dexter.notify.MangaDexLink
import com.dexter.notify.openRoutes
import com.dexter.notify.parseMangaDexLink
import com.dexter.ui.RAIL_MIN_WIDTH_DP
import com.dexter.ui.author.AuthorScreen
import com.dexter.ui.author.AuthorViewModel
import com.dexter.ui.downloads.DownloadsScreen
import com.dexter.ui.downloads.DownloadsViewModel
import com.dexter.ui.home.HomeScreen
import com.dexter.ui.home.HomeViewModel
import com.dexter.ui.library.LibraryScreen
import com.dexter.ui.library.LibraryViewModel
import com.dexter.ui.reader.ReaderScreen
import com.dexter.ui.reader.ReaderViewModel
import com.dexter.ui.reader.VolumeKeyPager
import com.dexter.ui.reader.readerBackgroundColor
import com.dexter.ui.search.SearchScreen
import com.dexter.ui.search.SearchViewModel
import com.dexter.ui.series.SeriesScreen
import com.dexter.ui.series.SeriesViewModel
import com.dexter.ui.settings.SettingsScreen
import com.dexter.ui.settings.SettingsViewModel
import com.dexter.ui.theme.DarkTheme
import com.dexter.ui.theme.DexterTheme
import com.dexter.ui.theme.Green
import com.dexter.ui.theme.isDark
import com.dexter.ui.updates.UpdatesScreen
import com.dexter.ui.updates.UpdatesViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import android.graphics.Color as AndroidColor

/** A notification tap: the series to open, and the chapter to open on top of it when there is one. */
private data class PendingOpen(val seriesId: String?, val chapterId: String?, val route: String? = null)

class MainActivity : ComponentActivity() {
    /** Counts app opens. The home screen reshuffles its picks when this changes. */
    private var openCount by mutableIntStateOf(0)

    /** Set when a notification opens the app. Consumed once by the navigation host. */
    private var pending by mutableStateOf<PendingOpen?>(null)

    private fun readPending(intent: android.content.Intent): PendingOpen? {
        val link = intent.data?.let { parseMangaDexLink(it.host, it.pathSegments.orEmpty()) }
        return when {
            link is MangaDexLink.Title -> PendingOpen(link.id, null)
            link is MangaDexLink.Chapter -> PendingOpen(null, link.id)
            intent.hasExtra(EXTRA_SERIES_ID) -> PendingOpen(intent.getStringExtra(EXTRA_SERIES_ID), intent.getStringExtra(EXTRA_CHAPTER_ID))
            intent.hasExtra(EXTRA_ROUTE) -> PendingOpen(null, null, intent.getStringExtra(EXTRA_ROUTE))
            else -> null
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        pending = readPending(intent)
    }

    override fun onStart() {
        super.onStart()
        openCount++
    }

    /** While the reader is open and the setting is on, the volume keys scroll it instead of changing volume. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        VolumeKeyPager.handle(event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        VolumeKeyPager.handle(event) || super.onKeyUp(keyCode, event)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app draws inside the system bars. The bar style follows the theme once it is known.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        pending = readPending(intent)
        val app = application as DexterApp
        setContent {
            val settings by app.settingsStore.settings.collectAsState(initial = Settings())
            DexterNav(settings, openCount, pending) { pending = null }
        }
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
private fun DexterNav(settings: Settings, openCount: Int, open: PendingOpen?, onOpened: () -> Unit) {
    val app = LocalContext.current.applicationContext as DexterApp
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    LaunchedEffect(open) {
        if (open != null) {
            if (open.route in tabs.map { it.route }) {
                nav.navigateTab(open.route!!)
            } else {
                // A chapter link names only the chapter, so ask MangaDex which series it belongs to.
                val seriesId = open.seriesId ?: open.chapterId?.let { runCatching { app.repository.seriesIdForChapter(it) }.getOrNull() }
                if (seriesId != null) openRoutes(seriesId, open.chapterId).forEach { nav.navigate(it) }
            }
            onOpened()
        }
    }
    val onTab = route?.substringBefore('?') in tabs.map { it.route }
    val onReader = route?.startsWith("series/") == true && route.count { it == '/' } == 2

    // Light icons on dark surfaces and dark icons on light ones. The reader has its own background.
    val activity = LocalActivity.current as? ComponentActivity
    val systemDark = isSystemInDarkTheme()
    val barsDark = if (onReader) settings.readerBackground != ReaderBackground.White else isDark(settings.theme, systemDark)
    DisposableEffect(barsDark) {
        val style = if (barsDark) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }

    DexterTheme(settings) {
        val rootBackground = if (onReader) readerBackgroundColor(settings.readerBackground) else MaterialTheme.colorScheme.background
        // One inset pad for the whole app keeps every screen between the status and navigation bars.
        Box(Modifier.fillMaxSize().background(rootBackground).systemBarsPadding()) {
            val wide = LocalConfiguration.current.screenWidthDp >= RAIL_MIN_WIDTH_DP
            Row(Modifier.fillMaxSize()) {
                if (wide && onTab) SideRail(nav, route?.substringBefore('?'))
                Scaffold(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = { if (onTab && !wide) BottomBar(nav, route?.substringBefore('?')) },
                ) { padding ->
                    NavHost(
                        nav,
                        startDestination = "home",
                        modifier = Modifier.padding(padding),
                        enterTransition = { fadeIn(tween(200)) + slideInHorizontally(tween(250)) { it / 12 } },
                        exitTransition = { fadeOut(tween(150)) },
                        popEnterTransition = { fadeIn(tween(200)) },
                        popExitTransition = { fadeOut(tween(150)) + slideOutHorizontally(tween(250)) { it / 12 } },
                    ) {
                        composable("home") {
                            val vm = koinViewModel<HomeViewModel>()
                            Box(Modifier.fillMaxSize()) {
                                HomeScreen(
                                    vm,
                                    onOpenSeries = { nav.navigate("series/$it") },
                                    onOpenSearch = { nav.navigateTab("search") },
                                    onOpenChapter = { series, chapter -> nav.navigate("series/$series/$chapter") },
                                    openCount = openCount,
                                )
                            }
                        }
                        composable("search?genre={genre}") { entry ->
                            val vm = koinViewModel<SearchViewModel>()
                            Box(Modifier.fillMaxSize()) {
                                SearchScreen(vm, entry.arguments?.getString("genre"), onOpenSeries = { nav.navigate("series/$it") })
                            }
                        }
                        composable("updates") {
                            val vm = koinViewModel<UpdatesViewModel>()
                            Box(Modifier.fillMaxSize()) {
                                UpdatesScreen(vm, onOpenSeries = { nav.navigate("series/$it") })
                            }
                        }
                        composable("library") {
                            val vm = koinViewModel<LibraryViewModel>()
                            Box(Modifier.fillMaxSize()) {
                                LibraryScreen(
                                    vm,
                                    onOpenSeries = { nav.navigate("series/$it") },
                                    onOpenSearch = { nav.navigateTab("search") },
                                    onOpenSettings = { nav.navigate("settings") },
                                )
                            }
                        }
                        composable("settings") {
                            val vm = koinViewModel<SettingsViewModel>()
                            SettingsScreen(vm, onBack = { nav.popBackStack() }, onOpenDownloads = { nav.navigate("downloads") })
                        }
                        composable("series/{seriesId}") { entry ->
                            val seriesId = entry.arguments!!.getString("seriesId")!!
                            val vm = koinViewModel<SeriesViewModel> { parametersOf(seriesId) }
                            SeriesScreen(
                                vm,
                                onOpenChapter = { nav.navigate("series/$seriesId/$it") },
                                onHome = { nav.navigateTab("home") },
                                onOpenTag = { tag -> nav.navigate("search?genre=${android.net.Uri.encode(tag)}") },
                                onOpenSeries = { nav.navigate("series/$it") },
                                onOpenAuthor = { id, name -> nav.navigate("author/$id?name=${android.net.Uri.encode(name)}") },
                            )
                        }
                        composable("downloads") {
                            val vm = koinViewModel<DownloadsViewModel>()
                            DownloadsScreen(
                                vm,
                                onBack = { nav.popBackStack() },
                                onOpenChapter = { series, chapter -> nav.navigate("series/$series/$chapter") },
                            )
                        }
                        composable("author/{authorId}?name={name}") { entry ->
                            val authorId = entry.arguments!!.getString("authorId")!!
                            val name = entry.arguments?.getString("name").orEmpty()
                            val vm = koinViewModel<AuthorViewModel>(key = authorId) { parametersOf(authorId) }
                            AuthorScreen(vm, name, onBack = { nav.popBackStack() }, onOpenSeries = { nav.navigate("series/$it") })
                        }
                        composable("series/{seriesId}/{chapterId}") { entry ->
                            val seriesId = entry.arguments!!.getString("seriesId")!!
                            val chapterId = entry.arguments!!.getString("chapterId")!!
                            val vm = koinViewModel<ReaderViewModel>(key = chapterId) { parametersOf(seriesId, chapterId) }
                            // The reader stays dark in a light app, so its bars and text keep their contrast.
                            DarkTheme {
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
private fun SideRail(nav: NavHostController, current: String?) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
        tabs.forEach { tab ->
            NavigationRailItem(
                selected = current == tab.route,
                onClick = { nav.navigateTab(tab.route) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                colors = NavigationRailItemDefaults.colors(
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

@Composable
private fun BottomBar(nav: NavHostController, current: String?) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = current == tab.route,
                onClick = { nav.navigateTab(tab.route) },
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
