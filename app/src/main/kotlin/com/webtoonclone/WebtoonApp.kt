package com.webtoonclone

import android.app.Application
import com.webtoonclone.data.LibraryStore
import com.webtoonclone.data.MangaDexRepository
import com.webtoonclone.data.ProgressStore
import com.webtoonclone.notify.NewChaptersWorker
import okhttp3.OkHttpClient

class WebtoonApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewChaptersWorker.schedule(this)
    }

    val repository by lazy { MangaDexRepository(OkHttpClient()) }
    val progressStore by lazy { ProgressStore(this) }
    val libraryStore by lazy { LibraryStore(this) }
}
