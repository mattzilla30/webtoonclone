package com.webtoonclone

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.webtoonclone.data.ImageReportInterceptor
import com.webtoonclone.data.ImageReporter
import com.webtoonclone.data.LibraryStore
import com.webtoonclone.data.MangaDexRepository
import com.webtoonclone.data.OfflineStore
import com.webtoonclone.data.ProgressStore
import com.webtoonclone.data.SeriesCacheStore
import com.webtoonclone.data.SettingsStore
import com.webtoonclone.di.appModule
import com.webtoonclone.notify.NewChaptersWorker
import com.webtoonclone.notify.updateContinueReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.io.File

private const val IMAGE_CACHE_BYTES = 250L * 1024 * 1024

class WebtoonApp : Application(), SingletonImageLoader.Factory {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Read by the image loader's network thread, so it is volatile. */
    @Volatile private var reportImageLoads = true

    /** The HTTP client for the MangaDex API. It keeps responses for a minute in [File]. */
    val apiClient: OkHttpClient by inject()
    val repository: MangaDexRepository by inject()
    val settingsStore: SettingsStore by inject()
    val progressStore: ProgressStore by inject()
    val libraryStore: LibraryStore by inject()
    val seriesCache: SeriesCacheStore by inject()
    val offlineStore: OfflineStore by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@WebtoonApp)
            modules(appModule)
        }
        NewChaptersWorker.schedule(this)
        // Keep the parts that read settings off the main thread in step with what you choose.
        appScope.launch {
            libraryStore.data
                .map { library -> library.recent.firstOrNull { it.chapterId != null } }
                .distinctUntilChanged()
                .collect { last -> updateContinueReading(this@WebtoonApp, last) }
        }
        appScope.launch {
            settingsStore.settings.collect { settings ->
                repository.applySettings(settings)
                reportImageLoads = settings.reportImageLoads
            }
        }
    }

    /** Page and cover images. Loads from MangaDex@Home servers are reported when allowed. */
    override fun newImageLoader(context: Context): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor(ImageReportInterceptor(ImageReporter()) { reportImageLoads })
            .build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "images").toOkioPath())
                    .maxSizeBytes(IMAGE_CACHE_BYTES)
                    .build()
            }
            .build()
    }
}
