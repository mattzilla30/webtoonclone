package com.dexter.ui.reader

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dexter.data.Chapter
import com.dexter.data.DownloadStore
import com.dexter.data.LibraryStore
import com.dexter.data.MangaDexRepository
import com.dexter.data.ProgressStore
import com.dexter.data.ReadingMode
import com.dexter.data.SavedSeries
import com.dexter.data.SeriesCacheStore
import com.dexter.data.Settings
import com.dexter.data.SettingsStore
import com.dexter.data.StatsStore
import com.dexter.data.applyLookChange
import com.dexter.data.detectReadingMode
import com.dexter.data.effectiveLook
import com.dexter.data.findChapter
import com.dexter.data.resolveMode
import com.dexter.data.withSeriesLook
import com.dexter.notify.DownloadWorker
import com.dexter.ui.Load
import com.dexter.ui.friendlyError
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    /** The series title, when it is known from a saved copy. */
    val seriesTitle: String? = null,
)

class ReaderViewModel(
    private val seriesId: String,
    private val chapterId: String,
    private val repository: MangaDexRepository,
    private val progressStore: ProgressStore,
    private val libraryStore: LibraryStore,
    private val settingsStore: SettingsStore,
    private val seriesCache: SeriesCacheStore,
    private val downloads: DownloadStore,
    private val stats: StatsStore,
    private val context: Application,
) : ViewModel() {
    /** The settings as this series' reader sees them, with its own dimming and background when it has them. */
    val settings: StateFlow<Settings> = settingsStore.settings
        .map { effectiveLook(it, seriesId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    /** Whether this series has its own dimming and background. */
    val hasSeriesLook: StateFlow<Boolean> = settingsStore.settings
        .map { seriesId in it.seriesLooks }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setSeriesLook(enabled: Boolean) {
        viewModelScope.launch { settingsStore.update { withSeriesLook(it, seriesId, enabled) } }
    }

    /** The mode detected from the series' tags and original language. */
    private val detected = MutableStateFlow(ReadingMode.Vertical)

    /** The reading mode in use: this series' own choice, or the detected one when the choice is Auto. */
    val mode: StateFlow<ReadingMode> = combine(settingsStore.settings, detected) { settings, detected ->
        resolveMode(settings.seriesReadingModes[seriesId], detected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingMode.Vertical)

    /** What the options dialog shows as selected. Auto means the detected mode is in use. */
    val chosenMode: StateFlow<ReadingMode> = settingsStore.settings
        .map { it.seriesReadingModes[seriesId] ?: ReadingMode.Auto }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingMode.Auto)

    /** Saves a reading mode for this series. Auto removes the choice so detection applies again. */
    fun setMode(mode: ReadingMode) {
        updateSettings { settings ->
            settings.copy(
                seriesReadingModes = if (mode == ReadingMode.Auto) {
                    settings.seriesReadingModes - seriesId
                } else {
                    settings.seriesReadingModes + (seriesId to mode)
                },
            )
        }
    }

    fun updateSettings(change: (Settings) -> Settings) {
        viewModelScope.launch { settingsStore.update { applyLookChange(it, seriesId, change) } }
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
        // A saved chapter opens from the device, so there is nothing to preload.
        if (downloads.isSaved(next)) return emptyList()
        return runCatching { repository.pages(next) }.getOrDefault(emptyList()).take(count)
    }

    fun load(forceRefresh: Boolean = false) {
        _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = try {
                coroutineScope {
                    val preferredGroup = settingsStore.current().preferredGroups[seriesId]
                    val chapters = async {
                        // With no connection, the chapters saved on this device are the list.
                        runCatching { repository.allChapters(seriesId, preferredGroup) }
                            .getOrElse { error -> downloads.chaptersOf(seriesId).ifEmpty { throw error } }
                    }
                    val pages = async { downloads.pagesOf(chapterId) ?: repository.pages(chapterId, forceRefresh) }
                    val detection = async { detectMode() }
                    val saved = progressStore.observe(seriesId).first()
                    val list = chapters.await().filter { it.externalUrl == null }
                    // The chapter may be another group's upload of one in the list.
                    val (index, chapter) = findChapter(list, chapterId) ?: error("Chapter not found")
                    detected.value = detection.await()
                    Load.Ready(
                        ReaderPage(
                            chapter = chapter,
                            chapters = list,
                            pages = pages.await(),
                            prevId = list.getOrNull(index - 1)?.id,
                            nextId = list.getOrNull(index + 1)?.id,
                            index = index,
                            total = list.size,
                            startPage = saved?.takeIf { it.chapterId == chapterId }?.page ?: 0,
                            seriesTitle = seriesCache.load(seriesId, repository.language)?.detail?.summary?.title
                                ?: libraryStore.current().knownSeries(seriesId)?.title,
                        ),
                    ).also {
                        recordRecent(chapter)
                        saveNextChapter(list.getOrNull(index + 1))
                    }
                }
            } catch (e: Exception) {
                Load.Error(friendlyError(e, "Could not load chapter"))
            }
        }
    }

    /** Reads the series' tags and language from the saved copy, or from MangaDex when there is none. */
    private suspend fun detectMode(): ReadingMode {
        val detail = seriesCache.load(seriesId, repository.language)?.detail
            ?: runCatching { repository.series(seriesId) }.getOrNull()
        return if (detail != null) detectReadingMode(detail.tags, detail.originalLanguage) else ReadingMode.Vertical
    }

    /** With the setting on, queues the next chapter for saving, unless it is already saved or on its way. */
    private fun saveNextChapter(next: Chapter?) {
        if (next == null) return
        viewModelScope.launch {
            val current = settingsStore.current()
            if (!current.autoDownloadNext || downloads.isSaved(next.id) || next.id in downloads.active.value) return@launch
            val known = libraryStore.current().knownSeries(seriesId) ?: return@launch
            DownloadWorker.enqueue(context, downloads, seriesId, known.title, known.coverUrl, next, current.downloadWifiOnly)
        }
    }

    private fun recordRecent(chapter: Chapter) {
        viewModelScope.launch {
            // Reuse the title and cover you already saved. Only a first read asks MangaDex for them.
            val known = libraryStore.current().knownSeries(seriesId)
            val (title, cover) = if (known != null) {
                known.title to known.coverUrl
            } else {
                val summary = runCatching { repository.series(seriesId).summary }.getOrNull() ?: return@launch
                summary.title to summary.coverUrl
            }
            libraryStore.recordRecent(SavedSeries(seriesId, title, cover, chapterId, chapter.number))
            runCatching { stats.recordRead(chapterId, seriesId, title) }
        }
    }

    fun saveProgress(page: Int) {
        viewModelScope.launch { progressStore.save(seriesId, chapterId, page) }
    }
}
