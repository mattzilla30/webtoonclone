package com.webtoonclone

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.webtoonclone.data.CrashLog
import com.webtoonclone.data.ImageReportInterceptor
import com.webtoonclone.data.ImageReporter
import com.webtoonclone.data.LibraryStore
import com.webtoonclone.data.MangaDexRepository
import com.webtoonclone.data.OfflineStore
import com.webtoonclone.data.ProgressStore
import com.webtoonclone.data.SeriesCacheStore
import com.webtoonclone.data.SettingsStore
import com.webtoonclone.data.cachingClient
import com.webtoonclone.notify.NewChaptersWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File

private const val IMAGE_CACHE_BYTES = 250L * 1024 * 1024

class WebtoonApp : Application(), SingletonImageLoader.Factory {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Read by the image loader's network thread, so it is volatile. */
    @Volatile private var reportImageLoads = true

    /** The HTTP client for the MangaDex API. It keeps responses for a minute in [File]. */
    val apiClient by lazy { cachingClient(File(cacheDir, "api")) }
    val repository by lazy { MangaDexRepository(apiClient) }
    val settingsStore by lazy { SettingsStore(this) }
    val progressStore by lazy { ProgressStore(this) }
    val libraryStore by lazy { LibraryStore(this) }
    val seriesCache by lazy { SeriesCacheStore(this) }
    val crashLog by lazy { CrashLog(this) }
    val offlineStore by lazy { OfflineStore(this) }

    override fun onCreate() {
        super.onCreate()
        NewChaptersWorker.schedule(this)
        crashLog.install()
        // Keep the parts that read settings off the main thread in step with what you choose.
        appScope.launch {
            settingsStore.settings.collect { settings ->
                repository.applySettings(settings)
                reportImageLoads = settings.reportImageLoads
                crashLog.enabled = settings.crashReports
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
