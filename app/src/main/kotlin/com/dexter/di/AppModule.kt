package com.dexter.di

import androidx.room.Room
import com.dexter.DexterApp
import com.dexter.data.BackupService
import com.dexter.data.DownloadStore
import com.dexter.data.ImageExport
import com.dexter.data.ImageReportInterceptor
import com.dexter.data.ImageReporter
import com.dexter.data.LibraryStore
import com.dexter.data.MangaDexRepository
import com.dexter.data.OfflineStore
import com.dexter.data.ProgressStore
import com.dexter.data.SeriesCacheStore
import com.dexter.data.SettingsStore
import com.dexter.data.StatsStore
import com.dexter.data.cachingClient
import com.dexter.data.db.AppDatabase
import com.dexter.ui.author.AuthorViewModel
import com.dexter.ui.downloads.DownloadsViewModel
import com.dexter.ui.home.HomeViewModel
import com.dexter.ui.library.LibraryViewModel
import com.dexter.ui.reader.ReaderViewModel
import com.dexter.ui.search.SearchViewModel
import com.dexter.ui.series.SeriesViewModel
import com.dexter.ui.settings.SettingsViewModel
import com.dexter.ui.stats.StatsViewModel
import com.dexter.ui.updates.UpdatesViewModel
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File

/** The plain client the others are built from. */
const val BASE_CLIENT = "base"

/**
 * Everything the app builds once, and how each screen's view model is made. Series, reader, and
 * author view models take their ids as parameters when a screen asks for them.
 */
val appModule = module {
    // One connection pool and thread pool under every client. Page images, downloads, and reports go to the same
    // image servers, so a connection one opens, another reuses instead of paying for a new TLS handshake.
    single(named(BASE_CLIENT)) { OkHttpClient() }
    single<OkHttpClient> { cachingClient(File(androidContext().cacheDir, "api"), get(named(BASE_CLIENT))) }
    single { ImageReporter(get(named(BASE_CLIENT))) }
    single { SettingsStore(androidContext()) }
    single {
        val settings = get<SettingsStore>()
        MangaDexRepository(get()) { settings.current() }
    }
    single { ProgressStore(androidContext()) }
    single {
        Room.databaseBuilder(androidContext(), AppDatabase::class.java, "library.db").build()
    }
    single { LibraryStore(androidContext(), get()) }
    single(named("downloads")) {
        get<OkHttpClient>(named(BASE_CLIENT)).newBuilder()
            .addInterceptor(ImageReportInterceptor(get<ImageReporter>()::send) { (androidApplication() as DexterApp).reportImageLoads })
            .build()
    }
    single { StatsStore(get()) }
    single { DownloadStore(androidContext(), get(), get(named("downloads"))) }
    single { SeriesCacheStore(androidContext()) }
    single { OfflineStore(androidContext()) }
    single { ImageExport(androidContext(), get(named(BASE_CLIENT))) }
    single { BackupService(androidContext(), get(), get(), get()) }

    viewModel { HomeViewModel(get(), get(), get(), get(), get()) }
    viewModel { SearchViewModel(get(), get(), get(), get()) }
    viewModel { UpdatesViewModel(get(), get(), get()) }
    viewModel { LibraryViewModel(get(), get(), get(), get(), androidApplication()) }
    viewModel { DownloadsViewModel(get()) }
    viewModel { StatsViewModel(get(), get()) }
    viewModel { SettingsViewModel(androidApplication() as DexterApp) }
    viewModel { params -> SeriesViewModel(params.get<String>(0), get(), get(), get(), get(), get(), get(), get(), androidApplication()) }
    viewModel { params -> ReaderViewModel(params.get<String>(0), params.get<String>(1), get(), get(), get(), get(), get(), get(), get(), androidApplication()) }
    viewModel { params -> AuthorViewModel(params.get<String>(0), get(), get(), get()) }
}
