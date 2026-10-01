package com.dexter.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
)

private val Context.settingsDataStore by preferencesDataStore(name = "settings")
private val SETTINGS = stringPreferencesKey("settings")

class SettingsStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<Settings> = context.settingsDataStore.data.map { prefs -> decode(prefs[SETTINGS]) }.distinctUntilChanged()

    suspend fun current(): Settings = settings.first()

    suspend fun update(change: (Settings) -> Settings) {
        context.settingsDataStore.edit { prefs ->
            prefs[SETTINGS] = json.encodeToString(Settings.serializer(), change(decode(prefs[SETTINGS])))
        }
    }

    /** The last text decoded and its result. Many screens watch the settings, and each change reaches all of them. */
    @Volatile
    private var lastDecoded: Pair<String?, Settings>? = null

    private fun decode(raw: String?): Settings {
        lastDecoded?.let { (text, settings) -> if (text == raw) return settings }
        val settings = raw?.let { runCatching { json.decodeFromString<Settings>(it) }.getOrNull() } ?: Settings()
        lastDecoded = raw to settings
        return settings
    }
}
