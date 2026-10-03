package com.dexter.ui.reader

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dexter.data.Bookmark
import com.dexter.data.Chapter
import com.dexter.data.DownloadStore
import com.dexter.data.Genres
import com.dexter.data.ImageExport
import com.dexter.data.LibraryStore
import com.dexter.data.LocalChapter
import com.dexter.data.MangaDexAccount
import com.dexter.data.MangaDexRepository
import com.dexter.data.PageSource
import com.dexter.data.ProgressStore
import com.dexter.data.QolPrefs
import com.dexter.data.ReaderOrientation
import com.dexter.data.ReadingMode
import com.dexter.data.SavedSeries
import com.dexter.data.SeriesCacheStore
import com.dexter.data.SeriesDetail
import com.dexter.data.Settings
import com.dexter.data.SettingsStore
import com.dexter.data.StatsStore
import com.dexter.data.Trackers
import com.dexter.data.applyLookChange
import com.dexter.data.detectReadingMode
import com.dexter.data.effectiveLook
import com.dexter.data.findChapter
import com.dexter.data.isLocal
import com.dexter.data.localChapters
import com.dexter.data.nextChapterIndex
import com.dexter.data.resolveMode
import com.dexter.data.resolvePages
import com.dexter.data.scanLocalRoot
import com.dexter.data.toLocalChapter
import com.dexter.data.toReaderChapter
import com.dexter.data.withSeriesLook
import com.dexter.notify.DownloadWorker
import com.dexter.ui.Load
import com.dexter.ui.LogFailures
import com.dexter.ui.catching
import com.dexter.ui.friendlyError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import java.io.File

/** One chapter in the reader: its pages, and where it sits in the series. */
data class ChapterSegment(
    val chapter: Chapter,
    val pages: List<String>,
    /** Its place in [ReaderPage.chapters], oldest first. */
    val index: Int,
    val prevId: String?,
    val nextId: String?,
)

/**
 * What the reader shows. [segments] starts with the chapter you opened. In the vertical strip with
 * continuous reading on, the chapters after it join the end as you reach it.
 */
data class ReaderPage(
    val segments: List<ChapterSegment>,
    /** Every chapter you can read here, oldest first, for the chapter list. */
    val chapters: List<Chapter>,
    /** First page to show. Non-zero when you left this chapter partway through. */
    val startPage: Int,
    /** How far down [startPage] you were, from 0 to 1. */
    val startFraction: Float = 0f,
    /** The series title, when it is known from a saved copy. */
    val seriesTitle: String? = null,
) {
    /** The chapter you opened. */
    val first: ChapterSegment get() = segments.first()
}

private const val RENEW_PAGES_MS = 60_000L

/** How many chapters continuous reading joins into one strip. Past this, the end card offers the next one. */
internal const val MAX_SEGMENTS = 12

class ReaderViewModel(
    val seriesId: String,
    private val chapterId: String,
    /** A page to open at, such as a bookmark's, or -1 for where you left off. */
    private val openAtPage: Int,
    private val repository: MangaDexRepository,
    private val progressStore: ProgressStore,
    private val libraryStore: LibraryStore,
    private val settingsStore: SettingsStore,
    private val seriesCache: SeriesCacheStore,
    private val downloads: DownloadStore,
    private val stats: StatsStore,
    private val imageExport: ImageExport,
    private val account: MangaDexAccount,
    private val trackers: Trackers,
    private val context: Application,
    /** Where pages come from. Defaults to the MangaDex path, so existing call sites keep working. */
    private val pageSource: PageSource = PageSource.MangaDex(chapterId),
    private val qol: QolPrefs,
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

    /** The reading mode in use: this series' own choice, then your default, then the detected one. */
    val mode: StateFlow<ReadingMode> = combine(settingsStore.settings, detected) { settings, detected ->
        resolveMode(settings.seriesReadingModes[seriesId], detected, settings.defaultReadingMode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingMode.Vertical)

    /** What the options dialog shows as selected. Auto means the default or detected mode is in use. */
    val chosenMode: StateFlow<ReadingMode> = settingsStore.settings
        .map { it.seriesReadingModes[seriesId] ?: ReadingMode.Auto }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingMode.Auto)

    /** Bookmarked pages in this series, so the bookmark button shows whether the current page has one. */
    val bookmarks: StateFlow<List<Bookmark>> = libraryStore.stateOf(viewModelScope) { lib -> lib.bookmarks.filter { it.seriesId == seriesId } }

    /** Saves a reading mode for this series. Auto removes the choice so the default or detection applies again. */
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

    private val _zen = MutableStateFlow(false)

    /**
     * Zen reading mode: hides all chrome, shields taps, and routes navigation to the volume keys.
     * A session toggle only, never saved.
     */
    val zen: StateFlow<Boolean> = _zen

    fun setZen(on: Boolean) {
        _zen.value = on
    }

    /** Saves a screen orientation for this series. Auto removes the choice so the global setting applies. */
    fun setSeriesOrientation(orientation: ReaderOrientation) {
        updateSettings { settings ->
            settings.copy(
                seriesOrientations = if (orientation == ReaderOrientation.Auto) {
                    settings.seriesOrientations - seriesId
                } else {
                    settings.seriesOrientations + (seriesId to orientation)
                },
            )
        }
    }

    private val _state = MutableStateFlow<Load<ReaderPage>>(Load.Loading)
    val state: StateFlow<Load<ReaderPage>> = _state

    private val _toast = MutableStateFlow<String?>(null)

    /** A short confirmation, such as after saving a page. */
    val toast: StateFlow<String?> = _toast

    fun clearToast() {
        _toast.value = null
    }

    /** Shows a short message. */
    fun toast(message: String) {
        _toast.value = message
    }

    /** The next chapter's first pages are preloaded once per reader session. */
    private var previewed = false

    /** The scanned chapters of a local series by id, so a joined chapter reads its own files. */
    private var localById: Map<String, LocalChapter> = emptyMap()

    private var loadJob: Job? = null
    private var appendJob: Job? = null

    private val _appendFailed = MutableStateFlow(false)

    /**
     * The next chapter failed to join the strip. The end card shows a retry instead of a stuck
     * "Loading the next episode...".
     */
    val appendFailed: StateFlow<Boolean> = _appendFailed

    /** The series as the library knows it, and its saved copy, read once when the chapter loads. */
    private var known: SavedSeries? = null
    private var cachedDetail: SeriesDetail? = null

    /** Skip-read advance: chapters at or below [lastReadNumber] are jumped over. Read once per load. */
    private var skipRead = false
    private var lastReadNumber: String? = null

    /** The chapter the reader advances to from [index], skipping read chapters when that is on. */
    private fun nextIndex(chapters: List<Chapter>, index: Int) = nextChapterIndex(chapters, index, lastReadNumber, skipRead)

    /** Chapters already recorded as read this session, so scrolling back and forth records each once. */
    private val entered = HashSet<String>()

    init { load() }

    /** Reloads after an error and asks for fresh page addresses, which may have expired. */
    fun retry() = load(forceRefresh = true)

    /**
     * The first [count] page URLs of the chapter after [segment], for the screen to preload near its end.
     * Returns nothing after the first call or when there is no next chapter.
     */
    suspend fun nextChapterPreview(count: Int, segment: ChapterSegment): List<String> {
        if (previewed) return emptyList()
        // Local pages are files on disk; there is nothing to preload over the network.
        if (pageSource.isLocal()) return emptyList()
        val next = segment.nextId ?: return emptyList()
        previewed = true
        // A saved chapter opens from the device, so there is nothing to preload.
        if (downloads.isSaved(next)) return emptyList()
        return catching { repository.pages(next) }.getOrDefault(emptyList()).take(count)
    }

    fun load(forceRefresh: Boolean = false) {
        // A retry replaces the load before it, so only one result can land.
        loadJob?.cancel()
        appendJob?.cancel()
        _state.value = Load.Loading
        loadJob = viewModelScope.launch(LogFailures) {
            _state.value = try {
                coroutineScope {
                    val preferredGroup = settingsStore.current().preferredGroups[seriesId]
                    val chapters = async {
                        // Local content lists the scanned chapters; a bare archive is one chapter alone.
                        if (pageSource.isLocal()) localChapterList()
                        else catching { readableChapters(preferredGroup, fresh = false) }
                            .getOrElse { error -> downloads.chaptersOf(seriesId).ifEmpty { throw error } }
                    }
                    val pages = async { pagesFor(chapterId, forceRefresh) }
                    // The saved copy and the library are each read once, and shared by everything below that needs them.
                    val cached = async { catching { seriesCache.load(seriesId, repository.language) }.getOrNull()?.detail }
                    val knownSeries = async { libraryStore.current().knownSeries(seriesId) }
                    val detection = async { detectMode(cached.await()) }
                    val saved = progressStore.observe(seriesId).first()
                    var list = chapters.await()
                    // A chapter newer than the kept list, such as one opened from a notification, needs a fresh list.
                    if (findChapter(list, chapterId) == null) list = catching { readableChapters(preferredGroup, fresh = true) }.getOrDefault(list)
                    // The chapter may be another group's upload of one in the list.
                    val (index, chapter) = findChapter(list, chapterId) ?: error("Chapter not found")
                    detected.value = detection.await()
                    cachedDetail = cached.await()
                    known = knownSeries.await()
                    val lib = libraryStore.current()
                    skipRead = settingsStore.current().skipReadChapters
                    lastReadNumber = lib.recent.firstOrNull { it.id == seriesId }?.chapterNumber
                    val resume = saved?.takeIf { it.chapterId == chapterId }
                    Load.Ready(
                        ReaderPage(
                            segments = listOf(segment(list, index, chapter, pages.await())),
                            chapters = list,
                            startPage = if (openAtPage >= 0) openAtPage else resume?.page ?: 0,
                            startFraction = if (openAtPage >= 0) 0f else resume?.fraction ?: 0f,
                            seriesTitle = cachedDetail?.summary?.title ?: known?.title,
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Error(friendlyError(e, "Could not load chapter"))
            }
        }
    }

    private fun segment(list: List<Chapter>, index: Int, chapter: Chapter, pages: List<String>) =
        ChapterSegment(chapter, pages, index, list.getOrNull(index - 1)?.id, list.getOrNull(nextIndex(list, index))?.id)

    /**
     * The pages of a chapter: local files for local content (an empty result becomes a load error),
     * the saved files when a MangaDex chapter is saved, the image server's addresses when not.
     */
    private suspend fun pagesFor(id: String, forceRefresh: Boolean = false): List<String> =
        if (pageSource.isLocal()) resolvePages(localSourceFor(id), context.cacheDir).ifEmpty { error("Could not read the chapter's pages") }
        else downloads.pagesOf(id) ?: repository.pages(id, forceRefresh)

    /**
     * Joins the chapter after the last one in the strip onto its end, for continuous reading. Does nothing
     * when one is already on its way, when there is no next chapter, or when the strip is long enough.
     */
    fun appendNext() {
        val page = (_state.value as? Load.Ready)?.value ?: return
        if (appendJob?.isActive == true || page.segments.size >= MAX_SEGMENTS) return
        val last = page.segments.last()
        val nextIndex = nextIndex(page.chapters, last.index)
        val next = page.chapters.getOrNull(nextIndex) ?: return
        _appendFailed.value = false
        appendJob = viewModelScope.launch(LogFailures) {
            val pages = catching { pagesFor(next.id) }.getOrNull()
            if (pages == null) {
                _appendFailed.value = true
                return@launch
            }
            val current = (_state.value as? Load.Ready)?.value ?: return@launch
            if (current.segments.last().index != last.index) return@launch
            _state.value = Load.Ready(current.copy(segments = current.segments + segment(current.chapters, nextIndex, next, pages)))
        }
    }

    /**
     * Called when [segment] comes on screen, the first one included. Records it as read, saves the chapter
     * after it when that is on, and deletes the chapter before it when that is on. Each chapter counts once.
     */
    fun enterSegment(segment: ChapterSegment) {
        if (!entered.add(segment.chapter.id)) return
        viewModelScope.launch(LogFailures) {
            val settings = settingsStore.current()
            if (!settings.incognito) {
                recordRecent(segment.chapter)
                lastReadNumber = segment.chapter.number
                if (!pageSource.isLocal()) {
                    // A read marker on MangaDex too, when you are signed in with them on.
                    catching { account.markRead(seriesId, listOf(segment.chapter.id)) }
                    // AniList and MyAnimeList progress, for the trackers you signed in to.
                    catching { trackers.pushProgress(seriesId, segment.chapter.number) }
                }
            }
            // Local chapters are already files: nothing to auto-download.
            if (!pageSource.isLocal()) saveNextChapter(segment, settings)
            // With the setting on, opening a chapter deletes the saved copy of the one before it.
            if (settings.deleteAfterRead) segment.prevId?.let { prev -> if (downloads.isSaved(prev)) downloads.delete(prev) }
        }
    }

    /** Reads the series' tags and language from the saved copy [cached], or from MangaDex when there is none. */
    private suspend fun detectMode(cached: SeriesDetail?): ReadingMode {
        // Local series carry no tags or language; skip the doomed network lookup.
        if (pageSource.isLocal()) return ReadingMode.Vertical
        val detail = cached ?: catching { repository.series(seriesId) }.getOrNull()
        return if (detail != null) detectReadingMode(detail.tags, detail.originalLanguage) else ReadingMode.Vertical
    }

    /** With the setting on, queues the chapter after [segment] for saving, unless it is already saved. */
    private suspend fun saveNextChapter(segment: ChapterSegment, settings: Settings) {
        val chapters = (_state.value as? Load.Ready)?.value?.chapters ?: return
        val next = chapters.getOrNull(nextIndex(chapters, segment.index)) ?: return
        val series = known ?: return
        // The queue ignores a chapter it already holds, so there is no need to check for one here.
        if (!settings.autoDownloadNext || downloads.isSaved(next.id)) return
        DownloadWorker.enqueue(context, downloads, seriesId, series.title, series.coverUrl, next, settings.downloadWifiOnly)
    }

    private suspend fun recordRecent(chapter: Chapter) {
        // Reuse the title and cover you already saved. Only a first read asks MangaDex for them.
        val (title, cover) = known?.let { it.title to it.coverUrl }
            ?: catching { repository.series(seriesId).summary }.getOrNull()?.let { it.title to it.coverUrl }
            ?: return
        libraryStore.recordRecent(SavedSeries(seriesId, title, cover, chapter.id, chapter.number))
        // The first genre tag feeds the genre breakdown in the stats.
        val genre = cachedDetail?.tags?.firstOrNull { tag -> Genres.any { it.name == tag } }
        runCatching { stats.recordRead(chapter.id, seriesId, title, genre) }
    }

    /** Saves where you are: [page] of [chapterId], [fraction] of the way down it, out of [total] pages. Incognito saves nothing. */
    fun saveProgress(chapterId: String, page: Int, fraction: Float, total: Int) {
        viewModelScope.launch(LogFailures) {
            if (settingsStore.current().incognito) return@launch
            progressStore.save(seriesId, chapterId, page, fraction, total)
        }
    }

    /** Adds time spent on a chapter to the reading stats. Incognito adds nothing. */
    fun addReadingTime(chapterId: String, ms: Long) {
        viewModelScope.launch(LogFailures) {
            if (settingsStore.current().incognito) return@launch
            stats.addReadingTime(chapterId, ms)
        }
    }

    /** Bookmarks [page] of [chapter], or removes the bookmark when it has one. */
    fun toggleBookmark(chapter: Chapter, page: Int) {
        viewModelScope.launch(LogFailures) {
            val title = (_state.value as? Load.Ready)?.value?.seriesTitle ?: known?.title.orEmpty()
            libraryStore.toggleBookmark(Bookmark(seriesId, title, chapter.id, chapter.number, page, System.currentTimeMillis()))
        }
    }

    /** When page addresses were last renewed, so a run of failing pages asks once, not once each. */
    private var pagesRenewedAt = 0L

    /**
     * Page addresses expire after about fifteen minutes. When a page still fails after its quiet retries,
     * this asks MangaDex for new ones for that page's chapter and swaps them in, keeping your place.
     */
    fun renewPages(segmentChapterId: String) {
        // File addresses do not expire.
        if (pageSource.isLocal()) return
        val now = System.currentTimeMillis()
        if (now - pagesRenewedAt < RENEW_PAGES_MS) return
        pagesRenewedAt = now
        viewModelScope.launch(LogFailures) {
            // A chapter saved on the device reads from files, which do not expire.
            if (downloads.isSaved(segmentChapterId)) return@launch
            val fresh = catching { repository.pages(segmentChapterId, forceRefresh = true) }.getOrNull() ?: return@launch
            val current = (_state.value as? Load.Ready)?.value ?: return@launch
            val updated = current.segments.map { seg ->
                if (seg.chapter.id == segmentChapterId && seg.pages.size == fresh.size) seg.copy(pages = fresh) else seg
            }
            _state.value = Load.Ready(current.copy(segments = updated))
        }
    }

    /** Opens the chapter's discussion thread through [open], or says there is none yet. */
    fun openComments(chapter: Chapter, open: (String) -> Unit) {
        if (pageSource.isLocal()) {
            _toast.value = "No comments for local chapters"
            return
        }
        viewModelScope.launch(LogFailures) {
            val url = catching { repository.chapterCommentsUrl(chapter.id) }
            when {
                url.isFailure -> _toast.value = "Could not look up the comments"
                url.getOrNull() == null -> _toast.value = "No comments on Ep. ${chapter.number} yet"
                else -> open(url.getOrNull()!!)
            }
        }
    }

    private fun pageName(chapter: Chapter, page: Int): String {
        val title = (_state.value as? Load.Ready)?.value?.seriesTitle ?: known?.title ?: "Dexter"
        return "$title Ep ${chapter.number} page ${page + 1}"
    }

    /** Saves one page image to Pictures/Dexter. */
    fun savePage(url: String, chapter: Chapter, page: Int) {
        viewModelScope.launch(LogFailures) {
            _toast.value = catching {
                imageExport.saveToGallery(imageExport.bytes(url, pageCacheKey(url)), pageName(chapter, page))
                "Saved to Pictures/Dexter"
            }.getOrElse { "Could not save the page" }
        }
    }

    /** Opens the share sheet with one page image, through [start]. */
    fun sharePage(url: String, chapter: Chapter, page: Int, start: (Intent) -> Unit) {
        viewModelScope.launch(LogFailures) {
            val shared = catching { imageExport.shareable(imageExport.bytes(url, pageCacheKey(url)), pageName(chapter, page)) }.getOrNull()
            if (shared == null) {
                _toast.value = "Could not share the page"
                return@launch
            }
            val (uri, mime) = shared
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            start(Intent.createChooser(send, null))
        }
    }

    /** Puts one page image on the clipboard, for pasting into another app. */
    fun copyPage(url: String, chapter: Chapter, page: Int) {
        viewModelScope.launch(LogFailures) {
            val uri: Uri? = catching { imageExport.shareable(imageExport.bytes(url, pageCacheKey(url)), pageName(chapter, page)).first }.getOrNull()
            _toast.value = if (uri == null) {
                "Could not copy the page"
            } else {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newUri(context.contentResolver, "Page", uri))
                "Page copied"
            }
        }
    }

    /** The chapters that open in the reader, oldest first. */
    private suspend fun readableChapters(preferredGroup: String?, fresh: Boolean): List<Chapter> =
        repository.allChapters(seriesId, preferredGroup, fresh).filter { it.externalUrl == null }

    /** The page source for local chapter [id]: the opened one, or another chapter of the same series for continuous reading. */
    private fun localSourceFor(id: String): PageSource = localById[id]?.let { PageSource.Local(it) } ?: pageSource

    /**
     * The chapter list for local content: every chapter of the series the opened chapter belongs
     * to, oldest first, so the chapter drawer and continuous reading work. A bare archive is one
     * chapter alone. Always contains the opened chapter, so the lookup below cannot miss.
     */
    private suspend fun localChapterList(): List<Chapter> {
        val src = pageSource
        if (src is PageSource.Archive) return listOf(src.file.toLocalChapter().toReaderChapter())
        val local = (src as? PageSource.Local)?.localChapter ?: error("Chapter not found")
        val root = qol.localFolder.first()?.let(::File)?.takeIf { it.isDirectory }
            ?: return listOf(local.toReaderChapter())
        // Scanning walks the folder tree: off the main thread, or a big comics folder freezes the reader.
        val dir = withContext(Dispatchers.IO) { runCatching { scanLocalRoot(root) }.getOrDefault(emptyList()) }
            .firstOrNull { it.id == local.seriesId }?.dir
            ?: return listOf(local.toReaderChapter())
        val chapters = withContext(Dispatchers.IO) { runCatching { localChapters(dir) }.getOrDefault(emptyList()) }
        localById = chapters.associateBy { it.id }
        return chapters.map { it.toReaderChapter() }.ifEmpty { listOf(local.toReaderChapter()) }
    }
}
