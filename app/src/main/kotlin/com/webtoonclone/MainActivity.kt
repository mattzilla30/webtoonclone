package com.webtoonclone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.webtoonclone.ui.home.HomeScreen
import com.webtoonclone.ui.home.HomeViewModel
import com.webtoonclone.ui.reader.ReaderScreen
import com.webtoonclone.ui.reader.ReaderViewModel
import com.webtoonclone.ui.series.SeriesScreen
import com.webtoonclone.ui.series.SeriesViewModel
import com.webtoonclone.ui.theme.WebtoonTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WebtoonTheme { WebtoonNav() } }
    }
}

@Composable
private fun WebtoonNav() {
    val app = LocalContext.current.applicationContext as WebtoonApp
    val nav = rememberNavController()

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") {
                val vm = viewModel { HomeViewModel(app.repository) }
                HomeScreen(vm, onOpenSeries = { nav.navigate("series/$it") })
            }
            composable("series/{seriesId}") { entry ->
                val seriesId = entry.arguments!!.getString("seriesId")!!
                val vm = viewModel { SeriesViewModel(seriesId, app.repository, app.progressStore) }
                SeriesScreen(vm, onOpenChapter = { nav.navigate("series/$seriesId/$it") })
            }
            composable("series/{seriesId}/{chapterId}") { entry ->
                val seriesId = entry.arguments!!.getString("seriesId")!!
                val chapterId = entry.arguments!!.getString("chapterId")!!
                val vm = viewModel(key = chapterId) {
                    ReaderViewModel(seriesId, chapterId, app.repository, app.progressStore)
                }
                ReaderScreen(
                    vm,
                    onOpenChapter = { nav.navigate("series/$seriesId/$it") { popUpTo("series/$seriesId") } },
                    onBack = { nav.popBackStack("series/{seriesId}", inclusive = false) },
                )
            }
        }
    }
}
