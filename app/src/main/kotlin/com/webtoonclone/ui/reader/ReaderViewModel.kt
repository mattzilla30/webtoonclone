package com.webtoonclone.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webtoonclone.data.Chapter
import com.webtoonclone.data.LibraryStore
import com.webtoonclone.data.SavedSeries
import com.webtoonclone.data.MangaDexRepository
import com.webtoonclone.data.ProgressStore
import com.webtoonclone.ui.Load
import com.webtoonclone.ui.friendlyError
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ReaderPage(
    val chapter: Chapter,
    val pages: List<String>,
    val prevId: String?,
    val nextId: String?,
    val index: Int,
    val total: Int,
    /** First page to show. Non-zero when you left this chapter partway through. */
    val startPage: Int,
)

class ReaderViewModel(
    private val seriesId: String,
    private val chapterId: String,
    private val repository: MangaDexRepository,
    private val progressStore: ProgressStore,
    private val libraryStore: LibraryStore,
) : ViewModel() {

    private val _state = MutableStateFlow<Load<ReaderPage>>(Load.Loading)
    val state: StateFlow<Load<ReaderPage>> = _state

    init { load() }

    fun load() {
        _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = try {
                coroutineScope {
                    val chapters = async { repository.allChapters(seriesId) }
                    val pages = async { repository.pages(chapterId) }
                    val saved = progressStore.observe(seriesId).first()
                    val list = chapters.await().filter { it.externalUrl == null }
                    val index = list.indexOfFirst { it.id == chapterId }
                    if (index == -1) error("Chapter not found")
                    Load.Ready(
                        ReaderPage(
                            chapter = list[index],
                            pages = pages.await(),
                            prevId = list.getOrNull(index - 1)?.id,
                            nextId = list.getOrNull(index + 1)?.id,
                            index = index,
                            total = list.size,
                            startPage = saved?.takeIf { it.chapterId == chapterId }?.page ?: 0,
                        ),
                    ).also { recordRecent(list[index]) }
                }
            } catch (e: Exception) {
                Load.Error(friendlyError(e, "Could not load chapter"))
            }
        }
    }

    private fun recordRecent(chapter: Chapter) {
        viewModelScope.launch {
            // The series title and cover come from a second request so the library list can show them.
            val summary = runCatching { repository.series(seriesId).summary }.getOrNull() ?: return@launch
            libraryStore.recordRecent(
                SavedSeries(seriesId, summary.title, summary.coverUrl, chapterId, chapter.number),
            )
        }
    }

    fun saveProgress(page: Int) {
        viewModelScope.launch { progressStore.save(seriesId, chapterId, page) }
    }
}
