package com.dexter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.dexter.ui.library.unreadSeriesCount
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
import com.dexter.ui.stats.StatsScreen
import com.dexter.ui.stats.StatsViewModel
import com.dexter.ui.theme.DarkTheme
import com.dexter.ui.theme.DexterTheme
import com.dexter.ui.theme.isDark
import com.dexter.ui.updates.UpdatesScreen
import com.dexter.ui.updates.UpdatesViewModel
import com.dexter.ui.windowWidthDp
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import android.graphics.Color as AndroidColor

/** A notification tap: the series to open, and the chapter to open on top of it when there is one. */
private const val SPLASH_MAX_MS = 1_000L

private data class PendingOpen(val seriesId: String?, val chapterId: String?, val route: String? = null)

class MainActivity : ComponentActivity() {
    /** Counts app opens. The home screen reshuffles its picks when this changes. */
    private var openCount by mutableIntStateOf(0)

    /** Set when a notification opens the app. Consumed once by the navigation host. */
    private var pending by mutableStateOf<PendingOpen?>(null)

    private fun readPending(intent: Intent): PendingOpen? {
        val link = intent.data?.let { parseMangaDexLink(it.host, it.pathSegments.orEmpty()) }
        return when {
            link is MangaDexLink.Title -> PendingOpen(link.id, null)
            link is MangaDexLink.Chapter -> PendingOpen(null, link.id)
            intent.hasExtra(EXTRA_SERIES_ID) -> PendingOpen(intent.getStringExtra(EXTRA_SERIES_ID), intent.getStringExtra(EXTRA_CHAPTER_ID))
            intent.hasExtra(EXTRA_ROUTE) -> PendingOpen(null, null, intent.getStringExtra(EXTRA_ROUTE))
            else -> null
        }
    }

    override fun onNewIntent(intent: Intent) {
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
            val settings by app.settingsStore.settings.collectAsStateWithLifecycle(initialValue = app.settingsStore.latest)
            DexterNav(settings, openCount, pending) { pending = null }
        }
        holdFirstFrameForSettings(app)
    }

    /**
     * Waits to draw the first frame until your saved settings have arrived, so it already has your theme and not the
     * default one. A second is the most it waits. The system splash screen stays up meanwhile.
     */
    private fun holdFirstFrameForSettings(app: DexterApp) {
        val started = SystemClock.uptimeMillis()
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val ready = app.settingsStore.settings.replayCache.isNotEmpty() || SystemClock.uptimeMillis() - started >= SPLASH_MAX_MS
                    if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
                    return ready
                }
            },
        )
    }
}

@Composable
private fun DexterNav(settings: Settings, openCount: Int, open: PendingOpen?, onOpened: () -> Unit) {
    val app = LocalContext.current.applicationContext as DexterApp
    val nav = rememberNavController()
    val library = app.libraryStore.data.collectAsStateWithLifecycle(initialValue = app.libraryStore.latest)
    // Read inside derivedStateOf, so a library change only redraws the navigation when the badge number changes.
    val unread by remember { derivedStateOf { unreadSeriesCount(library.value) } }
    val backStackEntry by nav.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    LaunchedEffect(open) {
        if (open != null) {
            if (open.route in tabRoutes) {
                nav.navigateTab(open.route!!)
            } else if (open.route == "downloads") {
                nav.navigate("downloads")
            } else {
                // A chapter link names only the chapter, so ask MangaDex which series it belongs to.
                val seriesId = open.seriesId ?: open.chapterId?.let { runCatching { app.repository.seriesIdForChapter(it) }.getOrNull() }
                if (seriesId != null) openRoutes(seriesId, open.chapterId).forEach { nav.navigate(it) }
            }
            onOpened()
        }
    }
    val onTab = route?.substringBefore('?') in tabRoutes
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
        // One inset pad for the whole app keeps every screen between the status and navigation bars. The reader
        // draws edge to edge and pads only its own bars, so the pages fill the screen when the system bars hide.
        Box(Modifier.fillMaxSize().background(rootBackground).then(if (onReader) Modifier else Modifier.systemBarsPadding())) {
            val wide = windowWidthDp() >= RAIL_MIN_WIDTH_DP
            val motion = MaterialTheme.motionScheme
            Row(Modifier.fillMaxSize()) {
                if (wide && onTab) SideRail(nav, route?.substringBefore('?'), unread)
                Scaffold(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = { if (onTab && !wide) BottomBar(nav, route?.substringBefore('?'), unread) },
                ) { padding ->
                    NavHost(
                        nav,
                        startDestination = "home",
                        modifier = Modifier.padding(padding),
                        enterTransition = { fadeIn(motion.defaultEffectsSpec()) + slideInHorizontally(motion.defaultSpatialSpec()) { it / 10 } },
                        exitTransition = { fadeOut(motion.fastEffectsSpec()) },
                        popEnterTransition = { fadeIn(motion.defaultEffectsSpec()) },
                        popExitTransition = { fadeOut(motion.fastEffectsSpec()) + slideOutHorizontally(motion.defaultSpatialSpec()) { it / 10 } },
                    ) {
                        composable("home") {
                            val vm = koinViewModel<HomeViewModel>()
                            Box(Modifier.fillMaxSize()) {
                                HomeScreen(
                                    vm,
                                    onOpenSeries = { nav.navigate("series/$it") },
                                    onOpenSearch = { nav.navigateTab("search") },
                                    onOpenChapter = { series, chapter -> nav.navigate("series/$series/$chapter") },
                                    onBrowse = { label -> nav.navigate("search?browse=${Uri.encode(label)}") },
                                    openCount = openCount,
                                )
                            }
                        }
                        composable("search?genre={genre}&browse={browse}") { entry ->
                            val vm = koinViewModel<SearchViewModel>()
                            Box(Modifier.fillMaxSize()) {
                                SearchScreen(
                                    vm,
                                    entry.arguments?.getString("genre"),
                                    initialBrowse = entry.arguments?.getString("browse"),
                                    onOpenSeries = { nav.navigate("series/$it") },
                                    onOpenAuthor = { id, name -> nav.navigate("author/$id?name=${Uri.encode(name)}") },
                                )
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
                                )
                            }
                        }
                        composable("settings") {
                            val vm = koinViewModel<SettingsViewModel>()
                            SettingsScreen(vm, onOpenDownloads = { nav.navigate("downloads") }, onOpenStats = { nav.navigate("stats") })
                        }
                        composable("series/{seriesId}") { entry ->
                            val seriesId = entry.arguments!!.getString("seriesId")!!
                            val vm = koinViewModel<SeriesViewModel> { parametersOf(seriesId) }
                            SeriesScreen(
                                vm,
                                onOpenChapter = { nav.navigate("series/$seriesId/$it") },
                                onHome = { nav.navigateTab("home") },
                                onOpenTag = { tag -> nav.navigate("search?genre=${Uri.encode(tag)}") },
                                onOpenSeries = { nav.navigate("series/$it") },
                                onOpenAuthor = { id, name -> nav.navigate("author/$id?name=${Uri.encode(name)}") },
                            )
                        }
                        composable("stats") {
                            val vm = koinViewModel<StatsViewModel>()
                            StatsScreen(vm, onBack = { nav.popBackStack() })
                        }
                        composable("downloads") {
                            val vm = koinViewModel<DownloadsViewModel>()
                            DownloadsScreen(
                                vm,
                                onBack = { nav.popBackStack() },
                                onOpenChapter = { series, chapter -> nav.navigate("series/$series/$chapter") },
                                onOpenSeries = { nav.navigate("series/$it") },
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
                                    // The next chapter replaces this one, so Back leaves the reader instead of stepping through chapters.
                                    onOpenChapter = { nav.navigate("series/$seriesId/$it") { popUpTo("series/{seriesId}/{chapterId}") { inclusive = true } } },
                                    // The screen under the reader is the one that opened it: the series page, Home, Stats, or an author.
                                    onBack = { nav.popBackStack() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
