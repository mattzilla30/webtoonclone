package com.dexter.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerUiDataStore by preferencesDataStore(name = "reader_ui")

private val THUMBNAILS = booleanPreferencesKey("thumbnails")
private val SCRUBBER_PREVIEW = booleanPreferencesKey("scrubber_preview")
private val TOP_ACTIONS = stringPreferencesKey("top_actions")
private val BOTTOM_ACTIONS = stringPreferencesKey("bottom_actions")
private val BINGE = booleanPreferencesKey("binge")
private val BINGE_SECONDS = intPreferencesKey("binge_seconds")
private val PREDICTIVE_BACK = booleanPreferencesKey("predictive_back")
private val STYLUS_BUTTON = booleanPreferencesKey("stylus_button")
private val STYLUS_HOVER = booleanPreferencesKey("stylus_hover")
private val DEVICE_CLASS_MODE = booleanPreferencesKey("device_class_mode")
private val TABLET_MODE = stringPreferencesKey("tablet_mode")
private val SPREAD_AWARE = booleanPreferencesKey("spread_aware")
private val STRIP_GAP = intPreferencesKey("strip_gap")
private val STRIP_CORNERS = intPreferencesKey("strip_corners")
private val STRIP_BG = stringPreferencesKey("strip_bg")
private val COLOR_EXEMPT = booleanPreferencesKey("color_exempt")
private val SLEEP_MINUTES = intPreferencesKey("sleep_minutes")
private val SAVER_AUTO_METERED = booleanPreferencesKey("saver_auto_metered")

/**
 * Every reader UI preference added outside the shared [Settings], kept in its own DataStore so the
 * shared settings file stays untouched. A blank action list means "the default layout"; a strip gap
 * of -1 means "follow the reader setting".
 */
data class ReaderUi(
    val thumbnailsEnabled: Boolean = true,
    val scrubberPreview: Boolean = true,
    val topActionsCsv: String = "",
    val bottomActionsCsv: String = "",
    val bingeMode: Boolean = false,
    val bingeSeconds: Int = 5,
    val predictiveBack: Boolean = true,
    val stylusPenButton: Boolean = true,
    val stylusHoverPeek: Boolean = true,
    val deviceClassMode: Boolean = false,
    /** Name of the [ReadingMode] for tablet-sized windows. */
    val tabletMode: String = ReadingMode.PagedLtr.name,
    val spreadAware: Boolean = true,
    /** Strip gap in dp, or -1 to follow the reader setting. */
    val stripGapDp: Int = -1,
    /** Rounded page corners in the strip, in dp. 0 is square. */
    val stripCornerDp: Int = 0,
    /** Name of the strip background choice; see StripBackground in ui.reader. */
    val stripBg: String = "Follow",
    /** Color pages skip the greyscale and tint night filters. */
    val colorPageExempt: Boolean = true,
    /** Sleep timer in minutes. 0 is off. */
    val sleepTimerMinutes: Int = 0,
    /** Load data-saver images automatically on metered connections. */
    val dataSaverAutoMetered: Boolean = false,
)

/**
 * Reads and writes [ReaderUi]. One DataStore file, one small class, the same shape as
 * [UpdateCheckStore]: a Flow of the whole value plus a suspend setter per field.
 */
class ReaderUiPrefs(private val context: Context) {
    val ui: Flow<ReaderUi> = context.readerUiDataStore.data.map { prefs ->
        ReaderUi(
            thumbnailsEnabled = prefs[THUMBNAILS] ?: true,
            scrubberPreview = prefs[SCRUBBER_PREVIEW] ?: true,
            topActionsCsv = prefs[TOP_ACTIONS].orEmpty(),
            bottomActionsCsv = prefs[BOTTOM_ACTIONS].orEmpty(),
            bingeMode = prefs[BINGE] ?: false,
            bingeSeconds = prefs[BINGE_SECONDS] ?: 5,
            predictiveBack = prefs[PREDICTIVE_BACK] ?: true,
            stylusPenButton = prefs[STYLUS_BUTTON] ?: true,
            stylusHoverPeek = prefs[STYLUS_HOVER] ?: true,
            deviceClassMode = prefs[DEVICE_CLASS_MODE] ?: false,
            tabletMode = prefs[TABLET_MODE] ?: ReadingMode.PagedLtr.name,
            spreadAware = prefs[SPREAD_AWARE] ?: true,
            stripGapDp = prefs[STRIP_GAP] ?: -1,
            stripCornerDp = prefs[STRIP_CORNERS] ?: 0,
            stripBg = prefs[STRIP_BG] ?: "Follow",
            colorPageExempt = prefs[COLOR_EXEMPT] ?: true,
            sleepTimerMinutes = prefs[SLEEP_MINUTES] ?: 0,
            dataSaverAutoMetered = prefs[SAVER_AUTO_METERED] ?: false,
        )
    }

    suspend fun setThumbnailsEnabled(on: Boolean) = edit { it[THUMBNAILS] = on }
    suspend fun setScrubberPreview(on: Boolean) = edit { it[SCRUBBER_PREVIEW] = on }
    suspend fun setTopActions(csv: String) = edit { it[TOP_ACTIONS] = csv }
    suspend fun setBottomActions(csv: String) = edit { it[BOTTOM_ACTIONS] = csv }
    suspend fun setBingeMode(on: Boolean) = edit { it[BINGE] = on }
    suspend fun setBingeSeconds(seconds: Int) = edit { it[BINGE_SECONDS] = seconds.coerceIn(1, 30) }
    suspend fun setPredictiveBack(on: Boolean) = edit { it[PREDICTIVE_BACK] = on }
    suspend fun setStylusPenButton(on: Boolean) = edit { it[STYLUS_BUTTON] = on }
    suspend fun setStylusHoverPeek(on: Boolean) = edit { it[STYLUS_HOVER] = on }
    suspend fun setDeviceClassMode(on: Boolean) = edit { it[DEVICE_CLASS_MODE] = on }
    suspend fun setTabletMode(mode: ReadingMode) = edit { it[TABLET_MODE] = mode.name }
    suspend fun setSpreadAware(on: Boolean) = edit { it[SPREAD_AWARE] = on }
    suspend fun setStripGapDp(dp: Int) = edit { it[STRIP_GAP] = dp.coerceIn(-1, 64) }
    suspend fun setStripCornerDp(dp: Int) = edit { it[STRIP_CORNERS] = dp.coerceIn(0, 32) }
    suspend fun setStripBg(name: String) = edit { it[STRIP_BG] = name }
    suspend fun setColorPageExempt(on: Boolean) = edit { it[COLOR_EXEMPT] = on }
    suspend fun setSleepTimerMinutes(minutes: Int) = edit { it[SLEEP_MINUTES] = minutes.coerceAtLeast(0) }
    suspend fun setDataSaverAutoMetered(on: Boolean) = edit { it[SAVER_AUTO_METERED] = on }

    private suspend fun edit(change: (MutablePreferences) -> Unit) {
        context.readerUiDataStore.edit(change)
    }
}

/** The stored tablet mode as a [ReadingMode], falling back to left-to-right pages. */
fun ReaderUi.tabletReadingMode(): ReadingMode =
    runCatching { ReadingMode.valueOf(tabletMode) }.getOrDefault(ReadingMode.PagedLtr)
