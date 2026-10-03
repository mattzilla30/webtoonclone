package com.dexter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dexter.automation.DexterAutomation
import com.dexter.data.CloudOAuth
import com.dexter.data.LocalChapter
import com.dexter.data.PageSource
import com.dexter.data.QolPrefs
import com.dexter.data.ReaderBackground
import com.dexter.data.ReadingStatus
import com.dexter.data.Settings
import com.dexter.data.StorageReport
import com.dexter.data.analyzeStorage
import com.dexter.data.localChapterById
import com.dexter.notify.EXTRA_CHAPTER_ID
import com.dexter.notify.EXTRA_ROUTE
import com.dexter.notify.EXTRA_SERIES_ID
import com.dexter.notify.MangaDexLink
import com.dexter.notify.openRoutes
import com.dexter.notify.parseMangaDexLink
import com.dexter.ui.AppLock
import com.dexter.ui.FirstRunSetup
import com.dexter.ui.LocalPaneWidthDp
import com.dexter.ui.LocalSharedScope
import com.dexter.ui.LockScreen
import com.dexter.ui.RAIL_MIN_WIDTH_DP
import com.dexter.ui.TWO_PANE_MIN_WIDTH_DP
import com.dexter.ui.author.AuthorScreen
import com.dexter.ui.author.AuthorViewModel
import com.dexter.ui.downloads.DownloadsScreen
import com.dexter.ui.downloads.DownloadsViewModel
import com.dexter.ui.downloads.StorageAnalyzerScreen
import com.dexter.ui.home.HomeScreen
import com.dexter.ui.home.HomeViewModel
import com.dexter.ui.library.LibraryScreen
import com.dexter.ui.library.LibraryViewModel
import com.dexter.ui.library.unreadSeriesCount
import com.dexter.ui.reader.GamepadKeys
import com.dexter.ui.reader.ReaderScreen
import com.dexter.ui.reader.ReaderViewModel
import com.dexter.ui.reader.VolumeKeyPager
import com.dexter.ui.reader.readerBackgroundColor
import com.dexter.ui.search.SearchScreen
import com.dexter.ui.search.SearchViewModel
import com.dexter.ui.series.SeriesScreen
import com.dexter.ui.series.SeriesViewModel
import com.dexter.ui.settings.ErrorLogScreen
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import java.io.File
import android.graphics.Color as AndroidColor

/** A notification tap: the series to open, and the chapter to open on top of it when there is one. */
private const val SPLASH_MAX_MS = 1_000L

/** Keeps an id from another app's intent only when it has the characters real ids use, so it cannot reach other routes or API paths. */
private fun String.safeId(): String? = takeIf { it.isNotEmpty() && it.length <= 200 && it.all { c -> c.isLetterOrDigit() || c in ":_.-" } && ".." !in it }

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
            intent.hasExtra(EXTRA_SERIES_ID) -> PendingOpen(intent.getStringExtra(EXTRA_SERIES_ID)?.safeId(), intent.getStringExtra(EXTRA_CHAPTER_ID)?.safeId())
            intent.hasExtra(EXTRA_ROUTE) -> PendingOpen(null, null, intent.getStringExtra(EXTRA_ROUTE))
            // Tasker / automation intents arrive via AutomationReceiver with the launcher activity.
            intent.action == DexterAutomation.ACTION_OPEN_READER -> PendingOpen(
                intent.getStringExtra(DexterAutomation.EXTRA_SERIES_ID)?.safeId(),
                intent.getStringExtra(DexterAutomation.EXTRA_CHAPTER_ID)?.safeId(),
            )
            else -> null
        }
    }

    /** Resolves "open continue reading" from the automation intent against the library. */
    private fun handleContinueReadingIntent(intent: Intent) {
        if (intent.action != DexterAutomation.ACTION_OPEN_CONTINUE_READING) return
        val app = application as DexterApp
        lifecycleScope.launch {
            val last = app.libraryStore.current().recent.firstOrNull { it.chapterId != null }
            pending = last?.let { PendingOpen(it.id, it.chapterId) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pending = readPending(intent)
        handleContinueReadingIntent(intent)
        finishSignIn(intent)
    }

    /** AniList and MyAnimeList send you back to dexter://anilist or dexter://mal after signing in. */
    private fun finishSignIn(intent: Intent) {
        val uri = intent.data?.takeIf { it.scheme == "dexter" } ?: return
        val app = application as DexterApp
        lifecycleScope.launch {
            val message = try {
                // Cloud OAuth (dexter://oauth/...) is tried when the tracker redirect does not match.
                app.trackers.handleRedirect(uri)
                    ?: CloudOAuth.handleRedirect(uri, app.oauthClient, app.cloudTokenStore)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Sign-in did not finish. Try again from Settings."
            }
            message?.let { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() }
        }
    }

    override fun onStart() {
        super.onStart()
        openCount++
        AppLock.onStart()
    }

    override fun onStop() {
        super.onStop()
        AppLock.onStop()
    }

    /** While the reader is open and the setting is on, the volume keys scroll it instead of changing volume. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        VolumeKeyPager.handle(event) || GamepadKeys.handle(event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        VolumeKeyPager.handle(event) || GamepadKeys.handle(event) || super.onKeyUp(keyCode, event)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app draws inside the system bars. The bar style follows the theme once it is known.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        pending = readPending(intent)
        handleContinueReadingIntent(intent)
        finishSignIn(intent)
        val app = application as DexterApp
        setContent {
            val settings by app.settingsStore.settings.collectAsStateWithLifecycle(initialValue = app.settingsStore.latest)
            // Push the lock config down; LockScreen reads it live.
            LaunchedEffect(settings.lockMode, settings.relockTimeoutMs) {
                AppLock.lockMode = settings.lockMode
                AppLock.relockTimeoutMs = settings.relockTimeoutMs
            }
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
                nav.navigate("downloads") { launchSingleTop = true }
            } else {
                // A chapter link names only the chapter, so ask MangaDex which series it belongs to.
                val seriesId = open.seriesId ?: open.chapterId?.let { runCatching { app.repository.seriesIdForChapter(it) }.getOrNull() }
                // Ids from other apps' intents are unchecked; one with a slash or query names no route and would crash navigate.
                if (seriesId != null) {
                    // Single-top, so tapping a notification for the open series does not stack it twice.
                    runCatching { openRoutes(seriesId, open.chapterId).forEach { nav.navigate(it) { launchSingleTop = true } } }
                } else if (open.chapterId != null) {
                    Toast.makeText(app, "Couldn't open that chapter link.", Toast.LENGTH_LONG).show()
                }
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

    // With the lock on, the recent apps screen shows a blank card instead of what you were reading.
    LaunchedEffect(activity, settings.appLock) { activity?.setRecentsScreenshotEnabled(!settings.appLock) }
    // The haptics switch: views skip their vibrations, and Compose's own feedback goes nowhere.
    val rootView = LocalView.current
    SideEffect { rootView.isHapticFeedbackEnabled = settings.haptics }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    DexterTheme(settings) {
        CompositionLocalProvider(LocalHapticFeedback provides if (settings.haptics) haptics else NoHaptics) {
            val rootBackground = if (onReader) readerBackgroundColor(settings.readerBackground) else MaterialTheme.colorScheme.background
            // One inset pad for the whole app keeps every screen between the status and navigation bars. The reader
            // draws edge to edge and pads only its own bars, so the pages fill the screen when the system bars hide.
            Box(Modifier.fillMaxSize().background(rootBackground).then(if (onReader) Modifier else Modifier.systemBarsPadding())) {
                val wide = windowWidthDp() >= RAIL_MIN_WIDTH_DP
                val motion = MaterialTheme.motionScheme
                // On a wide screen a series opens beside the list it came from. Back closes it.
                val twoPane = windowWidthDp() >= TWO_PANE_MIN_WIDTH_DP
                var paneSeries by rememberSaveable { mutableStateOf<String?>(null) }
                val showPane = twoPane && onTab && paneSeries != null
                val openSeries: (String) -> Unit = { id -> if (twoPane && onTab) paneSeries = id else nav.navigate("series/$id") }
                BackHandler(showPane) { paneSeries = null }
                Row(Modifier.fillMaxSize()) {
                    if (wide && onTab) SideRail(nav, route?.substringBefore('?'), unread)
                    Scaffold(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.background,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        bottomBar = { if (onTab && !wide) BottomBar(nav, route?.substringBefore('?'), unread) },
                    ) { padding ->
                        BoxWithConstraints {
                            CompositionLocalProvider(LocalPaneWidthDp provides if (showPane) maxWidth.value else null) {
                                // Covers fly from a list into the series page, and back.
                                SharedTransitionLayout {
                                    CompositionLocalProvider(LocalSharedScope provides this) {
                                        NavHost(
                                            nav,
                                            startDestination = "home",
                                            modifier = Modifier.padding(padding),
                                            enterTransition = { fadeIn(motion.defaultEffectsSpec()) + slideInHorizontally(motion.defaultSpatialSpec()) { it / 10 } },
                                            exitTransition = { fadeOut(motion.fastEffectsSpec()) },
                                            popEnterTransition = { fadeIn(motion.defaultEffectsSpec()) },
                                            popExitTransition = { fadeOut(motion.fastEffectsSpec()) + slideOutHorizontally(motion.defaultSpatialSpec()) { it / 10 } },
                                        ) {
                                            screen("home") {
                                                val vm = koinViewModel<HomeViewModel>()
                                                Box(Modifier.fillMaxSize()) {
                                                    HomeScreen(
                                                        vm,
                                                        onOpenSeries = openSeries,
                                                        onOpenChapter = { series, chapter -> nav.navigate("series/$series/$chapter") },
                                                        onBrowse = { label -> nav.navigate("search?browse=${Uri.encode(label)}") },
                                                        openCount = openCount,
                                                    )
                                                }
                                            }
                                            screen("search?genre={genre}&browse={browse}") { entry ->
                                                val vm = koinViewModel<SearchViewModel>()
                                                Box(Modifier.fillMaxSize()) {
                                                    SearchScreen(
                                                        vm,
                                                        entry.arguments?.getString("genre"),
                                                        initialBrowse = entry.arguments?.getString("browse"),
                                                        onOpenSeries = openSeries,
                                                        onOpenAuthor = { id, name -> nav.navigate("author/$id?name=${Uri.encode(name)}") },
                                                    )
                                                }
                                            }
                                            screen("updates") {
                                                val vm = koinViewModel<UpdatesViewModel>()
                                                Box(Modifier.fillMaxSize()) {
                                                    UpdatesScreen(
                                                        vm,
                                                        onOpenSeries = openSeries,
                                                        onOpenChapter = { series, chapter -> nav.navigate("series/$series/$chapter") },
                                                    )
                                                }
                                            }
                                            screen("library") {
                                                val vm = koinViewModel<LibraryViewModel>()
                                                Box(Modifier.fillMaxSize()) {
                                                    LibraryScreen(
                                                        vm,
                                                        onOpenSeries = openSeries,
                                                        onOpenSearch = { nav.navigateTab("search") },
                                                    )
                                                }
                                            }
                                            screen("settings") {
                                                val vm = koinViewModel<SettingsViewModel>()
                                                SettingsScreen(vm, onOpenDownloads = { nav.navigate("downloads") }, onOpenStats = { nav.navigate("stats") }, onOpenErrors = { nav.navigate("errors") }, onOpenStorage = { nav.navigate("storage") })
                                            }
                                            screen("series/{seriesId}") { entry ->
                                                val seriesId = entry.arguments!!.getString("seriesId")!!
                                                val vm = koinViewModel<SeriesViewModel> { parametersOf(seriesId) }
                                                SeriesScreen(
                                                    vm,
                                                    // Local series read through the local reader route; MangaDex ids keep the old one.
                                                    onOpenChapter = {
                                                        if (seriesId.startsWith("local:")) nav.navigate("local/$seriesId/$it")
                                                        else nav.navigate("series/$seriesId/$it")
                                                    },
                                                    onOpenBookmark = { chapter, page ->
                                                        if (seriesId.startsWith("local:")) nav.navigate("local/$seriesId/$chapter?page=$page")
                                                        else nav.navigate("series/$seriesId/$chapter?page=$page")
                                                    },
                                                    onHome = { nav.navigateTab("home") },
                                                    onOpenTag = { tag -> nav.navigate("search?genre=${Uri.encode(tag)}") },
                                                    onOpenSeries = openSeries,
                                                    onOpenAuthor = { id, name -> nav.navigate("author/$id?name=${Uri.encode(name)}") },
                                                )
                                            }
                                            screen("errors") { ErrorLogScreen(onBack = { nav.popBackStack() }) }
                                            screen("stats") {
                                                val vm = koinViewModel<StatsViewModel>()
                                                StatsScreen(vm, onBack = { nav.popBackStack() })
                                            }
                                            screen("downloads") {
                                                val vm = koinViewModel<DownloadsViewModel>()
                                                DownloadsScreen(
                                                    vm,
                                                    onBack = { nav.popBackStack() },
                                                    onOpenChapter = { series, chapter -> nav.navigate("series/$series/$chapter") },
                                                    onOpenSeries = openSeries,
                                                )
                                            }
                                            screen("storage") {
                                                val context = LocalContext.current
                                                val app = context.applicationContext as DexterApp
                                                val scope = rememberCoroutineScope()
                                                val rows by app.downloadStore.saved.collectAsStateWithLifecycle(initialValue = emptyList())
                                                val library by app.libraryStore.data.collectAsStateWithLifecycle(initialValue = app.libraryStore.latest)
                                                var report by remember { mutableStateOf<StorageReport?>(null) }
                                                LaunchedEffect(rows) {
                                                    report = withContext(Dispatchers.IO) { analyzeStorage(File(context.filesDir, "downloads"), rows) }
                                                }
                                                val finished = remember(library) { library.lists.filter { it.status == ReadingStatus.Completed }.map { it.id }.toSet() }
                                                report?.let {
                                                    StorageAnalyzerScreen(
                                                        report = it,
                                                        finishedSeriesIds = finished,
                                                        onDeleteChapter = { id -> scope.launch { app.downloadStore.delete(id) } },
                                                        onDeleteSeries = { id -> scope.launch { app.downloadStore.deleteSeries(id) } },
                                                        onBack = { nav.popBackStack() },
                                                    )
                                                }
                                            }
                                            screen("author/{authorId}?name={name}") { entry ->
                                                val authorId = entry.arguments!!.getString("authorId")!!
                                                val name = entry.arguments?.getString("name").orEmpty()
                                                val vm = koinViewModel<AuthorViewModel>(key = authorId) { parametersOf(authorId) }
                                                AuthorScreen(vm, name, onBack = { nav.popBackStack() }, onOpenSeries = openSeries)
                                            }
                                            screen(
                                                "series/{seriesId}/{chapterId}?page={page}",
                                                arguments = listOf(navArgument("page") { type = NavType.IntType; defaultValue = -1 }),
                                            ) { entry ->
                                                val seriesId = entry.arguments!!.getString("seriesId")!!
                                                val chapterId = entry.arguments!!.getString("chapterId")!!
                                                // A bookmark opens at its page. Otherwise the reader picks up where you left off.
                                                val page = entry.arguments!!.getInt("page", -1)
                                                val vm = koinViewModel<ReaderViewModel>(key = "$chapterId@$page") { parametersOf(seriesId, chapterId, page) }
                                                // The reader stays dark in a light app, so its bars and text keep their contrast.
                                                DarkTheme {
                                                    ReaderScreen(
                                                        vm,
                                                        // The next chapter replaces this one, so Back leaves the reader instead of stepping through chapters.
                                                        onOpenChapter = { nav.navigate("series/$seriesId/$it") { popUpTo("series/{seriesId}/{chapterId}?page={page}") { inclusive = true } } },
                                                        // The screen under the reader is the one that opened it: the series page, Home, Stats, or an author.
                                                        onBack = { nav.popBackStack() },
                                                    )
                                                }
                                            }
                                            screen(
                                                "local/{seriesId}/{chapterId}?page={page}",
                                                arguments = listOf(navArgument("page") { type = NavType.IntType; defaultValue = -1 }),
                                            ) { entry ->
                                                val seriesId = entry.arguments!!.getString("seriesId")!!
                                                val chapterId = entry.arguments!!.getString("chapterId")!!
                                                // A bookmark opens at its page. Otherwise the reader picks up where you left off.
                                                val page = entry.arguments!!.getInt("page", -1)
                                                val qol: QolPrefs = koinInject()
                                                // Null until the setting loads, then "" when no folder is set, so the lookup waits for the real value.
                                                val folderFlow = remember(qol) { qol.localFolder.map { it.orEmpty() } }
                                                val folder by folderFlow.collectAsStateWithLifecycle(initialValue = null)
                                                var localChapter by remember(chapterId) { mutableStateOf<LocalChapter?>(null) }
                                                var unresolved by remember(chapterId) { mutableStateOf(false) }
                                                LaunchedEffect(folder, chapterId) {
                                                    val loaded = folder ?: return@LaunchedEffect
                                                    val root = loaded.takeIf { it.isNotEmpty() }?.let(::File)?.takeIf { it.isDirectory }
                                                    localChapter = root?.let { localChapterById(chapterId, it) }
                                                    unresolved = localChapter == null
                                                }
                                                val chapter = localChapter
                                                when {
                                                    chapter != null -> {
                                                        val vm = koinViewModel<ReaderViewModel>(key = "local:$chapterId@$page") {
                                                            parametersOf(seriesId, chapterId, page, PageSource.Local(chapter))
                                                        }
                                                        // The reader stays dark in a light app, so its bars and text keep their contrast.
                                                        DarkTheme {
                                                            ReaderScreen(
                                                                vm,
                                                                // The next chapter replaces this one, so Back leaves the reader instead of stepping through chapters.
                                                                onOpenChapter = { nav.navigate("local/$seriesId/$it") { popUpTo("local/{seriesId}/{chapterId}?page={page}") { inclusive = true } } },
                                                                onBack = { nav.popBackStack() },
                                                            )
                                                        }
                                                    }
                                                    unresolved -> Column(
                                                        Modifier.fillMaxSize().padding(24.dp),
                                                        verticalArrangement = Arrangement.Center,
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                    ) {
                                                        Text("Could not find that chapter in the local folder.")
                                                        TextButton(onClick = { nav.popBackStack() }) { Text("Back") }
                                                    }
                                                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                        CircularProgressIndicator()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (showPane) {
                        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                            CompositionLocalProvider(LocalPaneWidthDp provides maxWidth.value) {
                                val seriesId = paneSeries!!
                                key(seriesId) {
                                    val vm = koinViewModel<SeriesViewModel>(key = "pane-$seriesId") { parametersOf(seriesId) }
                                    SeriesScreen(
                                        vm,
                                        onOpenChapter = { nav.navigate("series/$seriesId/$it") },
                                        onOpenBookmark = { chapter, page -> nav.navigate("series/$seriesId/$chapter?page=$page") },
                                        onHome = { paneSeries = null },
                                        onOpenTag = { tag -> nav.navigate("search?genre=${Uri.encode(tag)}") },
                                        onOpenSeries = { paneSeries = it },
                                        onOpenAuthor = { id, name -> nav.navigate("author/$id?name=${Uri.encode(name)}") },
                                    )
                                }
                            }
                        }
                    }
                }
                // A new install picks its basics first. With the lock on, the app stays covered until you unlock it.
                if (!settings.setupDone) {
                    FirstRunSetup(settings) { change -> scope.launch { app.settingsStore.update(change) } }
                } else if (settings.appLock && AppLock.locked) {
                    LockScreen()
                }
            }
        }
    }
}

/** Haptic feedback that does nothing, for when you turn haptics off. */
private object NoHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}
