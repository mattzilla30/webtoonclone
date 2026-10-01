package com.dexter.ui.library

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dexter.data.DownloadStore
import com.dexter.data.LibraryData
import com.dexter.data.LibraryList
import com.dexter.data.LibraryStore
import com.dexter.data.MangaDexRepository
import com.dexter.data.ReadingStatus
import com.dexter.data.SavedSeries
import com.dexter.data.SeriesSummary
import com.dexter.data.SettingsStore
import com.dexter.data.chaptersToDownload
import com.dexter.notify.DownloadWorker
import com.dexter.ui.Load
import com.dexter.ui.LogFailures
import com.dexter.ui.catching
import com.dexter.ui.series.hasUnreadChapters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val store: LibraryStore,
    private val settingsStore: SettingsStore,
    private val repository: MangaDexRepository,
    private val downloads: DownloadStore,
    private val context: Application,
) : ViewModel() {
    val library: StateFlow<LibraryData> = store.data
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), store.latest)

    /** Ids of series hidden from browse and search. */
    val hiddenIds: StateFlow<Set<String>> = settingsStore.settings.map { it.hiddenSeries }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settingsStore.latest.hiddenSeries)

    private val _hidden = MutableStateFlow<Load<List<SeriesSummary>>?>(null)

    /** The hidden series with their titles, loaded when you open the list of them. Null while it is closed. */
    val hidden: StateFlow<Load<List<SeriesSummary>>?> = _hidden

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    fun clearToast() {
        _toast.value = null
    }

    fun restore(list: LibraryList, snapshot: List<SavedSeries>) {
        viewModelScope.launch(LogFailures) { store.restore(list, snapshot) }
    }

    fun setSort(mode: LibrarySort) {
        viewModelScope.launch(LogFailures) { store.setLibrarySort(mode.name) }
    }

    fun setGrid(on: Boolean) {
        viewModelScope.launch(LogFailures) { store.setLibraryGrid(on) }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch(LogFailures) { store.setNotifications(enabled) }
    }

    fun delete(list: LibraryList, ids: Set<String>) {
        viewModelScope.launch(LogFailures) {
            when (list) {
                LibraryList.Recent -> store.removeRecent(ids)
                LibraryList.Subscribed -> store.removeSubscribed(ids)
                LibraryList.Lists -> store.removeLists(ids)
            }
        }
    }

    fun removeFromCollection(name: String, ids: Set<String>) {
        viewModelScope.launch(LogFailures) { store.removeFromCollection(name, ids) }
    }

    fun restoreCollection(name: String, snapshot: List<SavedSeries>) {
        viewModelScope.launch(LogFailures) { store.restoreCollection(name, snapshot) }
    }

    fun deleteCollection(name: String) {
        viewModelScope.launch(LogFailures) { store.deleteCollection(name) }
    }

    fun renameCollection(from: String, to: String) {
        viewModelScope.launch(LogFailures) { store.renameCollection(from, to.trim()) }
    }

    /** Marks every subscribed series with new chapters as read up to its newest known chapter. */
    fun markAllRead() {
        viewModelScope.launch(LogFailures) {
            val library = store.current()
            val lastRead = library.recent.associate { it.id to it.chapterNumber }
            library.subscribed
                .filter { it.knownChapterId != null && hasUnreadChapters(it.knownChapterNumber, lastRead[it.id]) }
                .forEach { series -> markCaughtUp(series) }
        }
    }

    /** Marks one subscribed series read up to its newest known chapter, as a swipe does. */
    fun markRead(series: SavedSeries) {
        viewModelScope.launch(LogFailures) {
            val current = store.current().subscribed.firstOrNull { it.id == series.id } ?: return@launch
            markCaughtUp(current)
            _toast.value = "Marked ${series.title} as read"
        }
    }

    private suspend fun markCaughtUp(series: SavedSeries) {
        val chapterId = series.knownChapterId ?: return
        store.recordRecent(SavedSeries(series.id, series.title, series.coverUrl, chapterId, series.knownChapterNumber))
    }

    /** Puts every one of [series] in the reading list [status], or out of the lists when null. */
    fun setStatus(series: List<SavedSeries>, status: ReadingStatus?) {
        viewModelScope.launch(LogFailures) {
            store.setStatusAll(series, status)
            _toast.value = if (status == null) "Removed ${series.size} from lists" else "Moved ${series.size} to ${status.label}"
        }
    }

    /** Adds every one of [series] to the collection [name]. */
    fun addToCollection(series: List<SavedSeries>, name: String) {
        viewModelScope.launch(LogFailures) {
            store.addToCollection(name.trim(), series)
            _toast.value = "Added ${series.size} to ${name.trim()}"
        }
    }

    /** Queues every unread chapter of each of [series] for saving, after the last one you read. */
    fun downloadUnread(series: List<SavedSeries>) {
        viewModelScope.launch(LogFailures) {
            val settings = settingsStore.current()
            val library = store.current()
            val saved = downloads.saved.first().mapTo(HashSet()) { it.chapterId }
            var queued = 0
            for (item in series) {
                val chapters = catching { repository.allChapters(item.id, settings.preferredGroups[item.id]) }.getOrNull() ?: continue
                val lastRead = library.recent.firstOrNull { it.id == item.id }?.chapterNumber
                chaptersToDownload(chapters.asReversed(), lastRead, saved, count = null).forEach { chapter ->
                    DownloadWorker.enqueue(context, downloads, item.id, item.title, item.coverUrl, chapter, settings.downloadWifiOnly)
                    queued++
                }
            }
            _toast.value = if (queued == 0) "Nothing unread to save" else "Queued $queued chapters"
        }
    }

    /** Loads the titles of the hidden series for the list of them. */
    fun openHidden() {
        val ids = hiddenIds.value.toList()
        _hidden.value = Load.Loading
        viewModelScope.launch(LogFailures) {
            val known = store.current()
            // Titles come from MangaDex when it answers, and from the library otherwise.
            val found = catching { repository.browse(ids = ids.take(100), limit = minOf(ids.size, 100)) }.getOrDefault(emptyList()).associateBy { it.id }
            _hidden.value = Load.Ready(
                ids.map { id ->
                    found[id] ?: known.knownSeries(id)?.let { SeriesSummary(it.id, it.title, it.coverUrl) } ?: SeriesSummary(id, "Unknown series", null)
                },
            )
        }
    }

    fun closeHidden() {
        _hidden.value = null
    }

    /** Shows a hidden series in browse and search again. */
    fun unhide(id: String) {
        viewModelScope.launch(LogFailures) {
            settingsStore.update { it.copy(hiddenSeries = it.hiddenSeries - id) }
            val current = (_hidden.value as? Load.Ready)?.value ?: return@launch
            _hidden.value = Load.Ready(current.filterNot { it.id == id })
        }
    }
}
