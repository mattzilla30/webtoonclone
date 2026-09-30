package com.dexter.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dexter.data.ReadingStats
import com.dexter.data.StatsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class StatsViewModel(store: StatsStore) : ViewModel() {
    val stats: StateFlow<ReadingStats?> = store.stats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
