package com.webtoonclone.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webtoonclone.data.LibraryData
import com.webtoonclone.data.LibraryList
import com.webtoonclone.data.LibraryStore
import com.webtoonclone.data.SavedSeries
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val store: LibraryStore) : ViewModel() {
    val library: StateFlow<LibraryData> = store.data
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryData())

    fun restore(list: LibraryList, snapshot: List<SavedSeries>) {
        viewModelScope.launch { store.restore(list, snapshot) }
    }

    fun setSortAlphabetical(alphabetical: Boolean) {
        viewModelScope.launch { store.setSortAlphabetical(alphabetical) }
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
}
