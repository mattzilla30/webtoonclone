package com.dexter.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dexter.data.ChapterIntegrity
import com.dexter.data.ComicInfo
import com.dexter.data.DownloadIntegrity
import com.dexter.data.DownloadStore
import com.dexter.data.MangaDexRepository
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

class DownloadsViewModel(
    private val store: DownloadStore,
    private val settingsStore: SettingsStore,
    private val checker: DownloadIntegrity,
    private val repository: MangaDexRepository,
) : ViewModel() {
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

    /**
     * Saves edited metadata for a downloaded chapter. The pages on disk are untouched; the next
     * CBZ export embeds the new values in its ComicInfo.xml.
     */
    fun updateMetadata(chapterId: String, info: ComicInfo) {
        viewModelScope.launch(LogFailures) {
            val row = store.row(chapterId) ?: return@launch
            store.updateRow(
                row.copy(
                    seriesTitle = info.series,
                    number = info.number,
                    title = info.title,
                    volume = info.volume,
                    groupName = info.translator,
                ),
            )
            _toast.value = "Metadata updated"
        }
    }

    fun deleteSeries(seriesId: String) {
        viewModelScope.launch(LogFailures) { store.deleteSeries(seriesId) }
    }

    fun deleteAll() {
        viewModelScope.launch(LogFailures) { store.deleteAll() }
    }

    /** Chapters with problems per series, after a verify; null while a verify runs. */
    private val _integrity = MutableStateFlow<Map<String, List<ChapterIntegrity>>?>(emptyMap())
    val integrity: StateFlow<Map<String, List<ChapterIntegrity>>?> = _integrity

    /** Checks every saved chapter of [seriesId]; only chapters with problems are reported. */
    fun verifySeries(seriesId: String) {
        viewModelScope.launch(LogFailures) {
            _integrity.value = null
            val bad = catching { checker.verifySeries(seriesId) }.getOrDefault(emptyList())
            _integrity.value = (_integrity.value ?: emptyMap()) + (seriesId to bad)
            _toast.value = if (bad.isEmpty()) "All chapters verified" else "${bad.size} ${if (bad.size == 1) "chapter" else "chapters"} need repair"
        }
    }

    /** Re-downloads a chapter's bad pages, then re-verifies. */
    fun repairChapter(seriesId: String, report: ChapterIntegrity) {
        viewModelScope.launch(LogFailures) {
            _toast.value = "Repairing..."
            val urls = catching { repository.pages(report.chapterId) }.getOrNull()
            if (urls == null) {
                _toast.value = "Could not fetch fresh page addresses"
                return@launch
            }
            val fresh = catching { checker.repairChapter(report.chapterId, report, urls) }.getOrNull()
            if (fresh == null) {
                _toast.value = "Repair failed"
                return@launch
            }
            val current = (_integrity.value ?: emptyMap()).toMutableMap()
            val remaining = current.getOrDefault(seriesId, emptyList()).filter { it.chapterId != report.chapterId } +
                listOfNotNull(fresh.takeUnless { it.ok })
            current[seriesId] = remaining
            _integrity.value = current
            _toast.value = if (fresh.ok) "Repaired" else "Still broken after repair"
        }
    }

    fun clearIntegrity() {
        _integrity.value = emptyMap()
    }
}
