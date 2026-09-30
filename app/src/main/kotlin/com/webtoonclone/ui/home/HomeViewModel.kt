package com.webtoonclone.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webtoonclone.data.HomeContent
import com.webtoonclone.data.MangaDexRepository
import com.webtoonclone.ui.Load
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: MangaDexRepository) : ViewModel() {

    private val _state = MutableStateFlow<Load<HomeContent>>(Load.Loading)
    val state: StateFlow<Load<HomeContent>> = _state

    init { load() }

    fun load() {
        _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = try {
                Load.Ready(repository.home())
            } catch (e: Exception) {
                Load.Error(e.message ?: "Could not load series")
            }
        }
    }
}
