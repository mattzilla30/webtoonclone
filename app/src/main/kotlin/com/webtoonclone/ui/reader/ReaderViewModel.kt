package com.webtoonclone.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webtoonclone.data.Chapter
import com.webtoonclone.data.LibraryStore
import com.webtoonclone.data.MangaDexRepository
import com.webtoonclone.data.ProgressStore
import com.webtoonclone.data.SavedSeries
import com.webtoonclone.data.Settings
import com.webtoonclone.data.SettingsStore
import com.webtoonclone.ui.Load
import com.webtoonclone.ui.friendlyError
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReaderPage(
    val chapter: Chapter,
    /** Every chapter you can read here, oldest first, for the chapter list. */
    val chapters: List<Chapter>,
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
    private val settingsStore: SettingsStore,
) : ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    fun updateSettings(change: (Settings) -> Settings) {
        viewModelScope.launch { settingsStore.update(change) }
    }

    private val _state = MutableStateFlow<Load<ReaderPage>>(Load.Loading)
    val state: StateFlow<Load<ReaderPage>> = _state

    /** The next chapter's first pages are preloaded once per reader session. */
    private var previewed = false

    init { load() }

    /** Reloads after an error and asks for fresh page addresses, which may have expired. */
    fun retry() = load(forceRefresh = true)

    /**
     * The first [count] page URLs of the next chapter, for the screen to preload near the end of
     * this one. Returns nothing after the first call or when there is no next chapter.
     */
    suspend fun nextChapterPreview(count: Int): List<String> {
        if (previewed) return emptyList()
        val next = (_state.value as? Load.Ready)?.value?.nextId ?: return emptyList()
        previewed = true
        return runCatching { repository.pages(next) }.getOrDefault(emptyList()).take(count)
    }

    fun load(forceRefresh: Boolean = false) {
        _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = try {
                coroutineScope {
                    val chapters = async { repository.allChapters(seriesId) }
                    val pages = async { repository.pages(chapterId, forceRefresh) }
                    val saved = progressStore.observe(seriesId).first()
                    val list = chapters.await().filter { it.externalUrl == null }
                    val index = list.indexOfFirst { it.id == chapterId }
                    if (index == -1) error("Chapter not found")
                    Load.Ready(
                        ReaderPage(
                            chapter = list[index],
                            chapters = list,
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
            // Reuse the title and cover you already saved. Only a first read asks MangaDex for them.
            val known = libraryStore.data.first().knownSeries(seriesId)
            val (title, cover) = if (known != null) {
                known.title to known.coverUrl
            } else {
                val summary = runCatching { repository.series(seriesId).summary }.getOrNull() ?: return@launch
                summary.title to summary.coverUrl
            }
            libraryStore.recordRecent(SavedSeries(seriesId, title, cover, chapterId, chapter.number))
        }
    }

    fun saveProgress(page: Int) {
        viewModelScope.launch { progressStore.save(seriesId, chapterId, page) }
    }
}
