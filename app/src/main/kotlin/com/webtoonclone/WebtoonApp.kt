package com.webtoonclone

import android.app.Application
import com.webtoonclone.data.LibraryStore
import com.webtoonclone.data.MangaDexRepository
import com.webtoonclone.data.ProgressStore
import com.webtoonclone.data.SeriesCacheStore
import com.webtoonclone.data.cachingClient
import com.webtoonclone.notify.NewChaptersWorker

class WebtoonApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewChaptersWorker.schedule(this)
    }

    val repository by lazy { MangaDexRepository(cachingClient(java.io.File(cacheDir, "api"))) }
    val progressStore by lazy { ProgressStore(this) }
    val libraryStore by lazy { LibraryStore(this) }
    val seriesCache by lazy { SeriesCacheStore(this) }
}
