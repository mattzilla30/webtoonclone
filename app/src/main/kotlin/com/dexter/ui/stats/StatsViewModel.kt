package com.dexter.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dexter.data.ReadingStats
import com.dexter.data.SettingsStore
import com.dexter.data.StatsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class StatsViewModel(store: StatsStore, settings: SettingsStore) : ViewModel() {
    /** The chapters-per-day goal from Settings. 0 means no goal. */
    val goal: StateFlow<Int> = settings.settings.map { it.dailyGoal }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val stats: StateFlow<ReadingStats?> = store.stats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
