package com.webtoonclone.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.webtoonclone.WebtoonApp
import com.webtoonclone.data.LibraryData
import com.webtoonclone.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val app: WebtoonApp) : ViewModel() {
    val settings: StateFlow<Settings> = app.settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    val library: StateFlow<LibraryData> = app.libraryStore.data
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryData())

    private val _cacheBytes = MutableStateFlow<Long?>(null)

    /** Size of the API and image caches, or null while it is being measured. */
    val cacheBytes: StateFlow<Long?> = _cacheBytes

    fun update(change: (Settings) -> Settings) {
        viewModelScope.launch { app.settingsStore.update(change) }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch { app.libraryStore.setNotifications(enabled) }
    }

    fun refreshCacheSize() {
        viewModelScope.launch { _cacheBytes.value = withContext(Dispatchers.IO) { measureCache() } }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                app.apiClient.cache?.evictAll()
                val loader = SingletonImageLoader.get(app)
                loader.diskCache?.clear()
                loader.memoryCache?.clear()
            }
            _cacheBytes.value = withContext(Dispatchers.IO) { measureCache() }
        }
    }

    private fun measureCache(): Long =
        (app.apiClient.cache?.size() ?: 0L) + (SingletonImageLoader.get(app).diskCache?.size ?: 0L)
}
