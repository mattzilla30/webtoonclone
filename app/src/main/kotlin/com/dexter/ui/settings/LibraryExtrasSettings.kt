package com.dexter.ui.settings

import androidx.compose.runtime.Composable
import com.dexter.data.Settings

/** Library extras: the on-device "Because you read" recommendations on Home. */
@Composable
fun LibraryExtrasSection(settings: Settings, update: ((Settings) -> Settings) -> Unit) {
    SettingsBlock("Library extras") {
        SwitchRow(
            "Recommendations",
            "Show \"Because you read\" picks on Home. Candidates are scored on this device from your library and reading history.",
            settings.recommendations,
            keywords = listOf("because you read", "suggestions", "home"),
        ) { on -> update { it.copy(recommendations = on) } }
    }
}
