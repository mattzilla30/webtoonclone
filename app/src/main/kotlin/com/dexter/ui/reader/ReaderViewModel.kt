package com.dexter.ui.reader

import android.app.Application
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
import com.dexter.data.SeriesDetail
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
import com.dexter.ui.LogFailures
import com.dexter.ui.catching
import com.dexter.ui.friendlyError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    /** How far down [startPage] you were, from 0 to 1. */
    val startFraction: Float = 0f,
    /** The series title, when it is known from a saved copy. */
    val seriesTitle: String? = null,
)

private const val RENEW_PAGES_MS = 60_000L

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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), effectiveLook(settingsStore.latest, seriesId))

    /** Whether this series has its own dimming and background. */
    val hasSeriesLook: StateFlow<Boolean> = settingsStore.settings
        .map { seriesId in it.seriesLooks }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setSeriesLook(enabled: Boolean) {
        viewModelScope.launch(LogFailures) { settingsStore.update { withSeriesLook(it, seriesId, enabled) } }
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
        viewModelScope.launch(LogFailures) { settingsStore.update { applyLookChange(it, seriesId, change) } }
    }

    private val _state = MutableStateFlow<Load<ReaderPage>>(Load.Loading)
    val state: StateFlow<Load<ReaderPage>> = _state

    /** The next chapter's first pages are preloaded once per reader session. */
    private var previewed = false

    private var loadJob: Job? = null

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
        return catching { repository.pages(next) }.getOrDefault(emptyList()).take(count)
    }

    fun load(forceRefresh: Boolean = false) {
        // A retry replaces the load before it, so only one result can land.
        loadJob?.cancel()
        _state.value = Load.Loading
        loadJob = viewModelScope.launch(LogFailures) {
            _state.value = try {
                coroutineScope {
                    val preferredGroup = settingsStore.current().preferredGroups[seriesId]
                    val chapters = async {
                        // With no connection, the chapters saved on this device are the list.
                        catching { readableChapters(preferredGroup, fresh = false) }
                            .getOrElse { error -> downloads.chaptersOf(seriesId).ifEmpty { throw error } }
                    }
                    val pages = async { downloads.pagesOf(chapterId) ?: repository.pages(chapterId, forceRefresh) }
                    // The saved copy and the library are each read once, and shared by everything below that needs them.
                    val cached = async { catching { seriesCache.load(seriesId, repository.language) }.getOrNull()?.detail }
                    val known = async { libraryStore.current().knownSeries(seriesId) }
                    val detection = async { detectMode(cached.await()) }
                    val saved = progressStore.observe(seriesId).first()
                    var list = chapters.await()
                    // A chapter newer than the kept list, such as one opened from a notification, needs a fresh list.
                    if (findChapter(list, chapterId) == null) list = catching { readableChapters(preferredGroup, fresh = true) }.getOrDefault(list)
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
                            startFraction = saved?.takeIf { it.chapterId == chapterId }?.fraction ?: 0f,
                            seriesTitle = cached.await()?.summary?.title ?: known.await()?.title,
                        ),
                    ).also {
                        recordRecent(chapter, known.await())
                        saveNextChapter(list.getOrNull(index + 1), known.await())
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Error(friendlyError(e, "Could not load chapter"))
            }
        }
    }

    /** The chapters that open in the reader, oldest first. */
    private suspend fun readableChapters(preferredGroup: String?, fresh: Boolean): List<Chapter> =
        repository.allChapters(seriesId, preferredGroup, fresh).filter { it.externalUrl == null }

    /** Reads the series' tags and language from the saved copy [cached], or from MangaDex when there is none. */
    private suspend fun detectMode(cached: SeriesDetail?): ReadingMode {
        val detail = cached ?: catching { repository.series(seriesId) }.getOrNull()
        return if (detail != null) detectReadingMode(detail.tags, detail.originalLanguage) else ReadingMode.Vertical
    }

    /** With the setting on, queues the next chapter for saving, unless it is already saved or on its way. */
    private fun saveNextChapter(next: Chapter?, known: SavedSeries?) {
        if (next == null || known == null) return
        viewModelScope.launch(LogFailures) {
            val current = settingsStore.current()
            if (!current.autoDownloadNext || downloads.isSaved(next.id) || next.id in downloads.active.value) return@launch
            DownloadWorker.enqueue(context, downloads, seriesId, known.title, known.coverUrl, next, current.downloadWifiOnly)
        }
    }

    private fun recordRecent(chapter: Chapter, known: SavedSeries?) {
        viewModelScope.launch(LogFailures) {
            // Reuse the title and cover you already saved. Only a first read asks MangaDex for them.
            val (title, cover) = if (known != null) {
                known.title to known.coverUrl
            } else {
                val summary = catching { repository.series(seriesId).summary }.getOrNull() ?: return@launch
                summary.title to summary.coverUrl
            }
            libraryStore.recordRecent(SavedSeries(seriesId, title, cover, chapterId, chapter.number))
            runCatching { stats.recordRead(chapterId, seriesId, title) }
        }
    }

    fun saveProgress(page: Int, fraction: Float = 0f) {
        viewModelScope.launch(LogFailures) { progressStore.save(seriesId, chapterId, page, fraction) }
    }

    /** When page addresses were last renewed, so a run of failing pages asks once, not once each. */
    private var pagesRenewedAt = 0L

    /**
     * Page addresses expire after about fifteen minutes. When a page still fails after its quiet
     * retries, this asks MangaDex for new ones and swaps them in, keeping your place.
     */
    fun renewPages() {
        val ready = (_state.value as? Load.Ready)?.value ?: return
        val now = System.currentTimeMillis()
        if (now - pagesRenewedAt < RENEW_PAGES_MS) return
        pagesRenewedAt = now
        viewModelScope.launch(LogFailures) {
            // A chapter saved on the device reads from files, which do not expire.
            if (downloads.isSaved(chapterId)) return@launch
            val fresh = catching { repository.pages(chapterId, forceRefresh = true) }.getOrNull() ?: return@launch
            val current = (_state.value as? Load.Ready)?.value ?: return@launch
            if (current === ready && fresh.size == ready.pages.size) _state.value = Load.Ready(ready.copy(pages = fresh))
        }
    }
}
