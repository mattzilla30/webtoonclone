package com.dexter

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.dexter.data.AccountStore
import com.dexter.data.BackupService
import com.dexter.data.DownloadStore
import com.dexter.data.ErrorLog
import com.dexter.data.ImageReportInterceptor
import com.dexter.data.ImageReporter
import com.dexter.data.LibraryStore
import com.dexter.data.MangaDexAccount
import com.dexter.data.MangaDexRepository
import com.dexter.data.SettingsStore
import com.dexter.data.Trackers
import com.dexter.di.BASE_CLIENT
import com.dexter.di.appModule
import com.dexter.notify.AutoBackupWorker
import com.dexter.notify.DownloadWorker
import com.dexter.notify.GoalReminderWorker
import com.dexter.notify.NewChaptersWorker
import com.dexter.notify.refreshUpdatesWidgets
import com.dexter.notify.updateContinueReading
import com.dexter.notify.widgetUpdates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import java.io.File

private const val IMAGE_CACHE_BYTES = 250L * 1024 * 1024
private const val IMAGE_REQUESTS_PER_HOST = 10

class DexterApp : Application(), SingletonImageLoader.Factory {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Read by the image loader's network thread, so it is volatile. */
    @Volatile var reportImageLoads = true

    /** The HTTP client for the MangaDex API. It keeps responses for a minute in [File]. */
    val apiClient: OkHttpClient by inject()
    val repository: MangaDexRepository by inject()
    val settingsStore: SettingsStore by inject()
    val libraryStore: LibraryStore by inject()
    val downloadStore: DownloadStore by inject()
    val backupService: BackupService by inject()
    val accountStore: AccountStore by inject()
    val mangaDexAccount: MangaDexAccount by inject()
    val trackers: Trackers by inject()
    private val baseClient: OkHttpClient by inject(named(BASE_CLIENT))

    /** The client with no response cache, for calls outside MangaDex such as the update check. */
    val plainClient: OkHttpClient get() = baseClient
    private val imageReporter: ImageReporter by inject()

    override fun onCreate() {
        super.onCreate()
        ErrorLog.init(this)
        startKoin {
            androidContext(this@DexterApp)
            modules(appModule)
        }
        // Scheduling opens WorkManager's database, so it stays off the main thread and out of the launch.
        appScope.launch { NewChaptersWorker.schedule(this@DexterApp, settingsStore.current().checkIntervalMinutes) }
        appScope.launch { AutoBackupWorker.sync(this@DexterApp, settingsStore.current().autoBackupFolder) }
        appScope.launch { GoalReminderWorker.sync(this@DexterApp, settingsStore.current().goalReminderHour) }
        // Keep the parts that read settings off the main thread in step with what you choose.
        appScope.launch { runCatching { downloadStore.prune() } }
        // Chapters still in the queue from before a restart start saving again.
        appScope.launch {
            if (runCatching { downloadStore.nextQueued() }.getOrNull() != null) {
                DownloadWorker.start(this@DexterApp, settingsStore.current().downloadWifiOnly)
            }
        }
        appScope.launch {
            libraryStore.data
                .map { library -> library.recent.firstOrNull { it.chapterId != null } }
                .distinctUntilChanged()
                .collect { last -> updateContinueReading(this@DexterApp, last) }
        }
        appScope.launch {
            libraryStore.data
                .map { library -> widgetUpdates(library) }
                .distinctUntilChanged()
                .collect { updates -> runCatching { refreshUpdatesWidgets(this@DexterApp, updates) } }
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
        val client = baseClient.newBuilder()
            .addInterceptor(ImageReportInterceptor(imageReporter::send) { reportImageLoads })
            // A chapter's pages come from one image server. The default of 5 at a time holds back the pages being prefetched.
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = IMAGE_REQUESTS_PER_HOST })
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
