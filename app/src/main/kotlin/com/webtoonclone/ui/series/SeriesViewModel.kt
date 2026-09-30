package com.webtoonclone.ui.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webtoonclone.data.Chapter
import com.webtoonclone.data.MangaDexRepository
import com.webtoonclone.data.ProgressStore
import com.webtoonclone.data.ReadingProgress
import com.webtoonclone.data.SeriesDetail
import com.webtoonclone.ui.Load
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SeriesPage(val detail: SeriesDetail, val chapters: List<Chapter>)

class SeriesViewModel(
    private val seriesId: String,
    private val repository: MangaDexRepository,
    progressStore: ProgressStore,
) : ViewModel() {

    private val _state = MutableStateFlow<Load<SeriesPage>>(Load.Loading)
    val state: StateFlow<Load<SeriesPage>> = _state

    val progress: StateFlow<ReadingProgress?> = progressStore.observe(seriesId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init { load() }

    fun load() {
        _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = try {
                coroutineScope {
                    val detail = async { repository.series(seriesId) }
                    val chapters = async { repository.chapters(seriesId) }
                    Load.Ready(SeriesPage(detail.await(), chapters.await()))
                }
            } catch (e: Exception) {
                Load.Error(e.message ?: "Could not load series")
            }
        }
    }
}
