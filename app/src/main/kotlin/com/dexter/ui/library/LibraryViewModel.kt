package com.dexter.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dexter.data.LibraryData
import com.dexter.data.LibraryList
import com.dexter.data.LibraryStore
import com.dexter.data.SavedSeries
import com.dexter.ui.series.hasUnreadChapters
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val store: LibraryStore) : ViewModel() {
    val library: StateFlow<LibraryData> = store.data
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), store.latest)

    fun restore(list: LibraryList, snapshot: List<SavedSeries>) {
        viewModelScope.launch { store.restore(list, snapshot) }
    }

    fun setSort(mode: LibrarySort) {
        viewModelScope.launch { store.setSort(alphabetical = mode == LibrarySort.Alphabetical, unreadFirst = mode == LibrarySort.UnreadFirst) }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch { store.setNotifications(enabled) }
    }

    fun delete(list: LibraryList, ids: Set<String>) {
        viewModelScope.launch {
            when (list) {
                LibraryList.Recent -> store.removeRecent(ids)
                LibraryList.Subscribed -> store.removeSubscribed(ids)
                LibraryList.Lists -> store.removeLists(ids)
            }
        }
    }

    fun removeFromCollection(name: String, ids: Set<String>) {
        viewModelScope.launch { store.removeFromCollection(name, ids) }
    }

    fun restoreCollection(name: String, snapshot: List<SavedSeries>) {
        viewModelScope.launch { store.restoreCollection(name, snapshot) }
    }

    fun deleteCollection(name: String) {
        viewModelScope.launch { store.deleteCollection(name) }
    }

    /** Marks every subscribed series with new chapters as read up to its newest known chapter. */
    fun markAllRead() {
        viewModelScope.launch {
            val library = store.current()
            val lastRead = library.recent.associate { it.id to it.chapterNumber }
            library.subscribed
                .filter { it.knownChapterId != null && hasUnreadChapters(it.knownChapterNumber, lastRead[it.id]) }
                .forEach { series ->
                    store.recordRecent(SavedSeries(series.id, series.title, series.coverUrl, series.knownChapterId, series.knownChapterNumber))
                }
        }
    }
}
