package com.webtoonclone.di

import com.webtoonclone.WebtoonApp
import com.webtoonclone.data.LibraryStore
import com.webtoonclone.data.MangaDexRepository
import com.webtoonclone.data.OfflineStore
import com.webtoonclone.data.ProgressStore
import com.webtoonclone.data.SeriesCacheStore
import com.webtoonclone.data.SettingsStore
import com.webtoonclone.data.cachingClient
import com.webtoonclone.ui.author.AuthorViewModel
import com.webtoonclone.ui.home.HomeViewModel
import com.webtoonclone.ui.library.LibraryViewModel
import com.webtoonclone.ui.reader.ReaderViewModel
import com.webtoonclone.ui.search.SearchViewModel
import com.webtoonclone.ui.series.SeriesViewModel
import com.webtoonclone.ui.settings.SettingsViewModel
import com.webtoonclone.ui.updates.UpdatesViewModel
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.io.File

/**
 * Everything the app builds once, and how each screen's view model is made. Series, reader, and
 * author view models take their ids as parameters when a screen asks for them.
 */
val appModule = module {
    single<OkHttpClient> { cachingClient(File(androidContext().cacheDir, "api")) }
    single { MangaDexRepository(get()) }
    single { SettingsStore(androidContext()) }
    single { ProgressStore(androidContext()) }
    single { LibraryStore(androidContext()) }
    single { SeriesCacheStore(androidContext()) }
    single { OfflineStore(androidContext()) }

    viewModel { HomeViewModel(get(), get(), get()) }
    viewModel { SearchViewModel(get(), get(), get()) }
    viewModel { UpdatesViewModel(get(), get()) }
    viewModel { LibraryViewModel(get()) }
    viewModel { SettingsViewModel(androidApplication() as WebtoonApp) }
    viewModel { params -> SeriesViewModel(params.get<String>(0), get(), get(), get(), get()) }
    viewModel { params -> ReaderViewModel(params.get<String>(0), params.get<String>(1), get(), get(), get(), get(), get()) }
    viewModel { params -> AuthorViewModel(params.get<String>(0), get()) }
}
