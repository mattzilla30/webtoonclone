package com.dexter.ui.settings

import androidx.compose.runtime.Composable
import com.dexter.data.Accent
import com.dexter.data.ReaderBackground
import com.dexter.data.Settings
import com.dexter.data.ThemeMode

/** Theme and Material You colors. */
@Composable
internal fun AppearanceSection(settings: Settings, update: ((Settings) -> Settings) -> Unit) {
    SectionTitle("Appearance")
    ChoiceRow(
        "Theme",
        listOf(
            ThemeMode.Dark to "Dark",
            ThemeMode.Black to "True black",
            ThemeMode.Light to "Light",
            ThemeMode.System to "System",
        ),
        settings.theme,
    ) { choice -> update { it.copy(theme = choice) } }
    SwitchRow("Material You colors", "Use your wallpaper colors, accents included.", settings.dynamicColor) { on ->
        update { it.copy(dynamicColor = on) }
    }
    if (!settings.dynamicColor) {
        ChoiceRow("Accent color", Accent.entries.map { it to it.name }, settings.accent) { choice -> update { it.copy(accent = choice) } }
    }
    SwitchRow("Haptics", "A light vibration on taps, switches, and long presses.", settings.haptics) { on ->
        update { it.copy(haptics = on) }
    }
}

/** Data saver, the reader's background and keys, and reporting page loads. */
@Composable
internal fun ReadingSection(settings: Settings, update: ((Settings) -> Settings) -> Unit) {
    SectionTitle("Reading")
    SwitchRow("Data saver", "Load smaller page images. Applies to chapters you open next.", settings.dataSaver) { on ->
        update { it.copy(dataSaver = on) }
    }
    ChoiceRow(
        "Reader background",
        listOf(ReaderBackground.Dark to "Dark", ReaderBackground.Black to "Black", ReaderBackground.White to "White"),
        settings.readerBackground,
    ) { choice -> update { it.copy(readerBackground = choice) } }
    SwitchRow("Volume keys scroll", "Volume up and down move the reader by a page.", settings.volumeKeys) { on ->
        update { it.copy(volumeKeys = on) }
    }
    SwitchRow(
        "Report image loads to MangaDex",
        "MangaDex asks apps to say whether page images loaded. A report holds the image address, its size, and how long it took.",
        settings.reportImageLoads,
    ) { on -> update { it.copy(reportImageLoads = on) } }
}
