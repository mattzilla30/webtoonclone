package com.webtoonclone.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webtoonclone.data.LibraryData
import com.webtoonclone.data.LibraryStore
import com.webtoonclone.data.SavedSeries
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val store: LibraryStore) : ViewModel() {

    val library: StateFlow<LibraryData> = store.data
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryData())

    fun restore(subscribedTab: Boolean, snapshot: List<SavedSeries>) {
        viewModelScope.launch { store.restore(subscribedTab, snapshot) }
    }

    fun setSortAlphabetical(alphabetical: Boolean) {
        viewModelScope.launch { store.setSortAlphabetical(alphabetical) }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch { store.setNotifications(enabled) }
    }

    fun delete(subscribedTab: Boolean, ids: Set<String>) {
        viewModelScope.launch {
            if (subscribedTab) store.removeSubscribed(ids) else store.removeRecent(ids)
        }
    }
}
