package com.dexter.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.serialization.Serializable

/** The reader's dimming and background for one series. */
@Serializable
data class SeriesLook(val dim: Int = 0, val background: ReaderBackground = ReaderBackground.Dark)

/** Every rating MangaDex uses, mildest first. */
val ContentRatings = listOf("safe", "suggestive", "erotica", "pornographic")

/** The ratings to request for [chosen], in MangaDex order. An empty choice falls back to safe so lists never go blank. */
fun ratingsFor(chosen: Set<String>): List<String> = ContentRatings.filter { it in chosen }.ifEmpty { listOf("safe") }

@Serializable
enum class ThemeMode { Dark, Light, System, Black }

@Serializable
enum class ReaderBackground { Dark, Black, White }

/** How the reader holds the screen. Auto follows the phone. */
@Serializable
enum class ReaderOrientation { Auto, Portrait, Landscape }

/** How the reader turns pages. Auto picks from the series' tags and original language. */
@Serializable
enum class ReadingMode { Auto, Vertical, PagedLtr, PagedRtl }

/** How a page fills the screen in paged mode. */
@Serializable
enum class PageFit { Screen, Width, Height }

/** A colour filter over the pages. */
@Serializable
enum class ReaderFilter { None, Warm, Sepia, Grayscale, Invert }

/** How paged mode moves from one page to the next. */
@Serializable
enum class PageTransition { Slide, Fade, None }

/** Everything the Settings screen and the reader options change. Defaults match the app before settings existed. */
@Serializable
data class Settings(
    val theme: ThemeMode = ThemeMode.Dark,
    /** Material You surfaces. Accents stay green. */
    val dynamicColor: Boolean = false,
    /** Load the smaller MangaDex image set to use less data. */
    val dataSaver: Boolean = false,
    /** Show the romanized original title instead of the English one. */
    val originalTitles: Boolean = false,
    /** Tell MangaDex whether each page image loaded, which it asks clients to do. */
    val reportImageLoads: Boolean = true,
    val quietHours: Boolean = false,
    val quietStartHour: Int = 22,
    val quietEndHour: Int = 7,
    val readerBackground: ReaderBackground = ReaderBackground.Dark,
    /** Reader dimming from 0 to 70 percent. */
    val readerDim: Int = 0,
    /** 0 is off, 1 to 5 scroll faster. */
    val autoScrollLevel: Int = 0,
    /** Volume keys scroll the reader by a page. */
    val volumeKeys: Boolean = false,
    /** Content ratings to show, using MangaDex names. All four by default. */
    val contentRatings: Set<String> = ContentRatings.toSet(),
    /** Tags that never appear in lists or search, unless you search the tag itself. */
    val blockedTags: Set<String> = emptySet(),
    /** Scanlation groups whose chapters are left out of chapter lists. */
    val blockedGroups: Set<String> = emptySet(),
    /** Series hidden from browse and search results. They still open from your library. */
    val hiddenSeries: Set<String> = emptySet(),
    /** Whether the reader locks the screen to portrait or landscape. */
    val readerOrientation: ReaderOrientation = ReaderOrientation.Auto,
    /** Save the next chapter in the background while you read, so it is ready offline. */
    val autoDownloadNext: Boolean = false,
    /** Chapters you aim to read each day. 0 turns the goal off. */
    val dailyGoal: Int = 0,
    /** Keep the screen on while a chapter is open. */
    val keepScreenOn: Boolean = true,
    /** Space between pages in the vertical strip, in dp. */
    val pageGap: Int = 0,
    /** Pages of the next chapter to load ahead: 0 is off, 3 is a preview, 100 loads the whole chapter. */
    val prefetchPages: Int = 3,
    /** One summary notification per check instead of one per series. */
    val notificationDigest: Boolean = false,
    /** A folder (tree address) that gets a daily backup file, or null for no automatic backup. */
    val autoBackupFolder: String? = null,
    /** Save chapters only on an unmetered connection. */
    val downloadWifiOnly: Boolean = true,
    /** MangaDex language code for chapters, titles, and descriptions. */
    val language: String = "en",
    /** The scanlation group to prefer for each series, by series id. */
    val preferredGroups: Map<String, String> = emptyMap(),
    /** A separate dimming and background for one series. A series with no entry uses the global look. */
    val seriesLooks: Map<String, SeriesLook> = emptyMap(),
    /** A reading mode chosen for one series. A series with no entry uses Auto. */
    val seriesReadingModes: Map<String, ReadingMode> = emptyMap(),
    /** True once you closed the tip that a long press on a cover subscribes. */
    val longPressTipSeen: Boolean = false,
    /** Delete a saved chapter once you open the chapter after it. */
    val deleteAfterRead: Boolean = false,
    /** Most space saved chapters may use, in megabytes. The oldest go first. 0 means no limit. */
    val downloadCapMb: Long = 0,
    /** Search and author results as rows with details instead of a grid of covers. */
    val resultsAsList: Boolean = false,
    /** In the vertical strip, the next chapter follows on below instead of an end card. */
    val continuousScroll: Boolean = true,
    /** The reader's bars hide on their own a few seconds after they show. */
    val autoHideBars: Boolean = true,
    /** In the vertical strip, a tap near the top or bottom scrolls by most of a screen. */
    val tapToScroll: Boolean = false,
    val pageFit: PageFit = PageFit.Screen,
    /** In paged mode with the phone sideways, two pages side by side. */
    val spreads: Boolean = false,
    /** Trim plain white or black margins from page images. */
    val cropBorders: Boolean = false,
    /** Screen brightness in the reader, 1 to 100, or -1 to follow the phone. */
    val readerBrightness: Int = -1,
    val readerFilter: ReaderFilter = ReaderFilter.None,
    /** A clock and the battery level next to the page counter while the bars are hidden. */
    val showClock: Boolean = true,
    /** The reading mode for series with no choice of their own. Auto picks from tags and language. */
    val defaultReadingMode: ReadingMode = ReadingMode.Auto,
    val pageTransition: PageTransition = PageTransition.Slide,
    /** Read without recording history, positions, or stats. */
    val incognito: Boolean = false,
    /** Minutes between background checks for new chapters. Android allows 15 at the least. */
    val checkIntervalMinutes: Int = 30,
    /** Reading statuses whose series never notify, such as Dropped. */
    val mutedStatuses: Set<ReadingStatus> = emptySet(),
    /** Collections whose series never notify. */
    val mutedCollections: Set<String> = emptySet(),
)

private val Context.settingsDataStore by preferencesDataStore(name = "settings")
private val SETTINGS = stringPreferencesKey("settings")

/** Settings text that could not be read, kept aside before the first save replaces it. */
private val SETTINGS_UNREADABLE = stringPreferencesKey("settings_unreadable")

class SettingsStore(private val context: Context) {
    private val fresh: Flow<Settings> = context.settingsDataStore.data.map { prefs -> decode(prefs[SETTINGS]) }.distinctUntilChanged().flowOn(Dispatchers.Default)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The settings as they change, shared by every screen that watches them. */
    val settings: SharedFlow<Settings> = fresh.shareIn(scope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), replay = 1)

    /** The last settings the app saw, or the defaults before the first read. Screens start from it so the theme and reader do not flash the defaults. */
    val latest: Settings get() = settings.replayCache.firstOrNull() ?: Settings()

    /** One fresh read, straight from storage. */
    suspend fun current(): Settings = fresh.first()

    suspend fun update(change: (Settings) -> Settings) {
        context.settingsDataStore.edit { prefs ->
            val raw = prefs[SETTINGS]
            if (isUnreadable(Settings.serializer(), raw)) prefs[SETTINGS_UNREADABLE] = raw!!
            prefs[SETTINGS] = StoredJson.encodeToString(Settings.serializer(), change(decode(raw)))
        }
    }

    /** The last text decoded and its result. Many screens watch the settings, and each change reaches all of them. */
    @Volatile
    private var lastDecoded: Pair<String?, Settings>? = null

    private fun decode(raw: String?): Settings {
        lastDecoded?.let { (text, settings) -> if (text == raw) return settings }
        val settings = decodeStored(Settings.serializer(), raw) ?: Settings()
        lastDecoded = raw to settings
        return settings
    }
}

/** True when [seriesId] sits in a reading status or collection you muted, so its new chapters stay quiet. */
fun isMuted(seriesId: String, library: LibraryData, settings: Settings): Boolean {
    if (settings.mutedStatuses.isNotEmpty()) {
        val status = library.lists.firstOrNull { it.id == seriesId }?.status
        if (status != null && status in settings.mutedStatuses) return true
    }
    return settings.mutedCollections.any { name -> library.collections[name]?.any { it.id == seriesId } == true }
}
