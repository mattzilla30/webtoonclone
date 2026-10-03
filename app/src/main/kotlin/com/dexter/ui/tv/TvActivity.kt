package com.dexter.ui.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dexter.DexterApp
import com.dexter.data.Settings
import com.dexter.ui.AppLock
import com.dexter.ui.LockScreen
import com.dexter.ui.reader.ReaderScreen
import com.dexter.ui.reader.ReaderViewModel
import com.dexter.ui.theme.DarkTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The Android TV entry point, shown by the Leanback launcher. Hosts the 10-foot browse UI and the
 * reader for continue-reading. The manifest entry (feature declaration, LeanbackLauncher
 * intent-filter) is not in this file; see the integration snippet in the task report.
 */
class TvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DexterApp
        setContent {
            // This activity is exported, so it honours the app lock like MainActivity; otherwise another
            // app could open it to see the library and history without unlocking.
            val loaded by app.settingsStore.settings.collectAsStateWithLifecycle<Settings?>(initialValue = app.settingsStore.settings.replayCache.firstOrNull())
            val settings = loaded ?: run {
                Box(Modifier.fillMaxSize().background(Color.Black))
                return@setContent
            }
            LaunchedEffect(settings.appLock) { setRecentsScreenshotEnabled(!settings.appLock) }
            LaunchedEffect(settings.lockMode, settings.relockTimeoutMs) {
                AppLock.lockMode = settings.lockMode
                AppLock.relockTimeoutMs = settings.relockTimeoutMs
            }
            DarkTheme {
                Box(Modifier.fillMaxSize()) {
                    TvNav()
                    if (settings.appLock && AppLock.locked) LockScreen(settings.lockMode)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppLock.onStart()
    }

    override fun onStop() {
        super.onStop()
        AppLock.onStop()
    }
}

@Composable
private fun TvNav() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "tv") {
        composable("tv") {
            TvBrowseScreen(
                onOpenSeries = { seriesId -> nav.navigate("tv/series/$seriesId") },
                onContinueReading = { seriesId, chapterId -> nav.navigate("tv/reader/$seriesId/$chapterId") },
                onBack = { nav.popBackStack() },
            )
        }
        composable("tv/series/{seriesId}") { entry ->
            val seriesId = entry.arguments!!.getString("seriesId")!!
            TvSeriesScreen(
                seriesId = seriesId,
                onOpenChapter = { chapterId -> nav.navigate("tv/reader/$seriesId/$chapterId") },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            "tv/reader/{seriesId}/{chapterId}",
            arguments = listOf(navArgument("seriesId") { type = NavType.StringType }, navArgument("chapterId") { type = NavType.StringType }),
        ) { entry ->
            val seriesId = entry.arguments!!.getString("seriesId")!!
            val chapterId = entry.arguments!!.getString("chapterId")!!
            val vm = koinViewModel<ReaderViewModel>(key = "tv:$chapterId") { parametersOf(seriesId, chapterId, -1) }
            ReaderScreen(
                vm,
                onOpenChapter = { next -> nav.navigate("tv/reader/$seriesId/$next") { popUpTo("tv/reader/{seriesId}/{chapterId}") { inclusive = true } } },
                onBack = { nav.popBackStack() },
            )
        }
    }
}
