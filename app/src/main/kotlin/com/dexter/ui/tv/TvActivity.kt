package com.dexter.ui.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
        setContent {
            DarkTheme {
                TvNav()
            }
        }
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
