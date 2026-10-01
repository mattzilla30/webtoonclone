package com.dexter.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dexter.data.DownloadStore
import com.dexter.data.Settings
import com.dexter.data.SettingsStore
import com.dexter.data.db.DownloadEntity
import com.dexter.data.db.QueuedDownloadEntity
import com.dexter.ui.LogFailures
import com.dexter.ui.catching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The chapters saved for one series, with the space they use. */
data class SavedSeriesGroup(val seriesId: String, val title: String, val chapters: List<DownloadEntity>) {
    val bytes: Long get() = chapters.sumOf { it.bytes }
}

/** Groups saved chapters by series, series with the most recent save first, chapters in reading order. */
fun groupDownloads(rows: List<DownloadEntity>): List<SavedSeriesGroup> = rows
    .groupBy { it.seriesId }
    .map { (id, chapters) ->
        SavedSeriesGroup(
            id,
            chapters.first().seriesTitle,
            chapters.sortedBy { it.number.toDoubleOrNull() ?: Double.MAX_VALUE },
        )
    }
    .sortedByDescending { group -> group.chapters.maxOf { it.savedAt } }

class DownloadsViewModel(private val store: DownloadStore, private val settingsStore: SettingsStore) : ViewModel() {
    /** Chapters waiting to be saved, in order. */
    val queue: StateFlow<List<QueuedDownloadEntity>> = store.queue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The chapter being saved, with its progress from 0 to 1. */
    val active: StateFlow<Map<String, Float>> = store.active

    val settings: StateFlow<Settings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settingsStore.latest)

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    fun clearToast() {
        _toast.value = null
    }

    fun cancel(chapterId: String) {
        viewModelScope.launch(LogFailures) { store.cancel(chapterId) }
    }

    fun cancelAll() {
        viewModelScope.launch(LogFailures) { store.cancelAll() }
    }

    fun moveToTop(chapterId: String) {
        viewModelScope.launch(LogFailures) { store.moveToTop(chapterId) }
    }

    fun setDeleteAfterRead(on: Boolean) {
        viewModelScope.launch(LogFailures) { settingsStore.update { it.copy(deleteAfterRead = on) } }
    }

    /** Sets the most space saved chapters may use, and deletes the oldest past it right away. */
    fun setCap(megabytes: Long) {
        viewModelScope.launch(LogFailures) {
            settingsStore.update { it.copy(downloadCapMb = megabytes) }
            store.enforceCap(megabytes * 1024 * 1024, keep = null)
        }
    }

    /** Writes [chapterIds] as CBZ files to Downloads/Dexter. */
    fun exportCbz(chapterIds: List<String>) {
        viewModelScope.launch(LogFailures) {
            _toast.value = "Exporting ${chapterIds.size} chapters..."
            val written = catching { store.exportCbz(chapterIds) }.getOrDefault(0)
            _toast.value = if (written == 0) "Could not export" else "Saved $written CBZ files to Downloads/Dexter"
        }
    }

    val groups: StateFlow<List<SavedSeriesGroup>?> = store.saved
        .map { groupDownloads(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(chapterId: String) {
        viewModelScope.launch(LogFailures) { store.delete(chapterId) }
    }

    fun deleteSeries(seriesId: String) {
        viewModelScope.launch(LogFailures) { store.deleteSeries(seriesId) }
    }

    fun deleteAll() {
        viewModelScope.launch(LogFailures) { store.deleteAll() }
    }
}
