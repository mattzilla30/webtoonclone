package com.dexter.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dexter.data.DownloadStore
import com.dexter.data.db.DownloadEntity
import kotlinx.coroutines.Dispatchers
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

class DownloadsViewModel(private val store: DownloadStore) : ViewModel() {
    val groups: StateFlow<List<SavedSeriesGroup>?> = store.saved
        .map { groupDownloads(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(chapterId: String) {
        viewModelScope.launch { store.delete(chapterId) }
    }

    fun deleteSeries(seriesId: String) {
        viewModelScope.launch { store.deleteSeries(seriesId) }
    }

    fun deleteAll() {
        viewModelScope.launch { store.deleteAll() }
    }
}
