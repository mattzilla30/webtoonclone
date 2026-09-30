package com.dexter.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.dexter.DexterApp
import com.dexter.data.Backup
import com.dexter.data.LibraryData
import com.dexter.data.Settings
import com.dexter.data.decodeBackup
import com.dexter.data.encodeBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val app: DexterApp) : ViewModel() {
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

    private val _message = MutableStateFlow<String?>(null)

    /** The result of the last backup or restore, for the screen to show once. */
    val message: StateFlow<String?> = _message

    /** A backup read from a file, waiting for you to confirm before it replaces your data. */
    private val _pending = MutableStateFlow<Backup?>(null)
    val pending: StateFlow<Backup?> = _pending

    fun clearMessage() {
        _message.value = null
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            _message.value = runCatching {
                val backup = Backup(
                    savedAt = System.currentTimeMillis(),
                    library = app.libraryStore.data.first(),
                    settings = app.settingsStore.current(),
                    progress = app.progressStore.export(),
                )
                val text = encodeBackup(backup)
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                }
                "Backup saved"
            }.getOrElse { "Could not save the backup" }
        }
    }

    fun readBackup(uri: Uri) {
        viewModelScope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) { app.contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) } }
            }.getOrNull()
            val backup = text?.let(::decodeBackup)
            if (backup == null) _message.value = "That file is not a backup from this app" else _pending.value = backup
        }
    }

    fun cancelRestore() {
        _pending.value = null
    }

    fun confirmRestore() {
        val backup = _pending.value ?: return
        _pending.value = null
        viewModelScope.launch {
            _message.value = runCatching {
                app.libraryStore.replaceAll(backup.library)
                app.settingsStore.update { backup.settings }
                app.progressStore.replaceAll(backup.progress)
                "Backup restored"
            }.getOrElse { "Could not restore the backup" }
        }
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
