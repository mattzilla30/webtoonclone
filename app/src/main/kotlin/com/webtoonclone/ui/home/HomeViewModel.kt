package com.webtoonclone.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webtoonclone.data.MangaDexRepository
import com.webtoonclone.data.SeriesSummary
import com.webtoonclone.ui.Load
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: MangaDexRepository) : ViewModel() {

    private val _state = MutableStateFlow<Load<List<SeriesSummary>>>(Load.Loading)
    val state: StateFlow<Load<List<SeriesSummary>>> = _state

    private var query = ""

    init { load() }

    fun search(text: String) {
        query = text.trim()
        load()
    }

    fun load() {
        _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = try {
                Load.Ready(if (query.isEmpty()) repository.popular(0) else repository.search(query, 0))
            } catch (e: Exception) {
                Load.Error(e.message ?: "Could not load series")
            }
        }
    }
}
