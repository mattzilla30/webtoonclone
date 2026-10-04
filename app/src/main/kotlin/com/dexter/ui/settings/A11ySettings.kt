package com.dexter.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexter.data.A11yPrefs
import com.dexter.data.A11yState
import com.dexter.data.AppFont
import com.dexter.data.CvdTheme
import com.dexter.ui.theme.cvdColorScheme
import com.dexter.ui.theme.installCustomFont
import com.dexter.ui.theme.simulateCvd
import kotlinx.coroutines.launch

/**
 * Accessibility settings: narration (TTS), voice control, dyslexia-friendly type, colour-blind-safe
 * themes with a simulation preview, and tall-page splitting. Wired into the Settings screen by
 * calling [A11ySection] next to the other sections; see the insertion snippet in the task report.
 */
@Composable
internal fun A11ySection(prefs: A11yPrefs) {
    val state by prefs.state.collectAsStateWithLifecycle(initialValue = A11yState())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    SettingsBlock("Accessibility") {
        SwitchRow(
            "Read aloud",
            "Narrate chapters with text-to-speech, even with the screen off. Headset buttons pause and resume.",
            state.ttsEnabled,
            keywords = listOf("narration", "tts", "text to speech", "speech rate", "voice"),
            more = if (!state.ttsEnabled) {
                null
            } else {
                {
                    SubSwitch("Turn pages while narrating", state.ttsAutoAdvance) { on -> scope.launch { prefs.setTtsAutoAdvance(on) } }
                    Stepper(
                        "Speech rate",
                        "%.1fx".format(state.ttsSpeed),
                        onMinus = { scope.launch { prefs.setTtsSpeed((state.ttsSpeed - 0.1f).coerceAtLeast(0.5f)) } },
                        onPlus = { scope.launch { prefs.setTtsSpeed((state.ttsSpeed + 0.1f).coerceIn(0.5f, 2f)) } },
                    )
                }
            },
        ) { on -> scope.launch { prefs.setTtsEnabled(on) } }

        SwitchRow(
            "Voice control",
            "Say \"next page\", \"scroll down\", or \"go back\" in the reader. The mic only listens while you hold it on.",
            state.voiceControl,
            keywords = listOf("microphone", "speech", "hands free"),
        ) { on -> scope.launch { prefs.setVoiceControl(on) } }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) scope.launch {
                if (installCustomFont(context, uri) != null) prefs.setCustomFontUri(uri.toString())
            }
        }
        ChoiceRow(
            "App typeface",
            listOf(
                AppFont.System to "System",
                AppFont.OpenDyslexicStyle to "Dyslexia-friendly",
                AppFont.CustomFile to "Custom font file",
            ),
            state.font,
            keywords = listOf("font", "dyslexia", "opendyslexic", "text"),
            more = {
                when (state.font) {
                    AppFont.CustomFile -> SubLink(
                        "Font file",
                        if (state.customFontUri.isBlank()) "Pick a .ttf or .otf, such as the free OpenDyslexic from opendyslexic.org." else "Installed. Pick again to replace it.",
                    ) { picker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream")) }
                    AppFont.OpenDyslexicStyle -> Note(
                        "Dexter widens letter spacing for legibility. For the real OpenDyslexic letterforms, download them free from " +
                            "opendyslexic.org and pick the file under \"Custom font file\".",
                    )
                    else -> Unit
                }
            },
        ) { choice -> scope.launch { prefs.setFont(choice) } }

        ChoiceRow(
            "Colour-blind-safe theme",
            listOf(
                CvdTheme.None to "Off",
                CvdTheme.DeuteranopiaSafe to "Deuteranopia",
                CvdTheme.ProtanopiaSafe to "Protanopia",
                CvdTheme.HighContrast to "High contrast",
            ),
            state.cvdTheme,
            keywords = listOf("color vision", "cvd", "contrast"),
            more = { if (state.cvdTheme != CvdTheme.None) CvdPreview(state.cvdTheme) },
        ) { choice -> scope.launch { prefs.setCvdTheme(choice) } }

        SwitchRow(
            "Split tall pages",
            "Cut oversized vertical pages into screen-sized segments so text stays legible.",
            state.tallPageSplit,
            keywords = listOf("long pages", "segments", "threshold"),
            more = if (!state.tallPageSplit) {
                null
            } else {
                {
                    Stepper(
                        "Split pages taller than (screens)",
                        "%.1f".format(state.tallSplitScreens),
                        onMinus = { scope.launch { prefs.setTallSplitScreens((state.tallSplitScreens - 0.5f).coerceAtLeast(1.5f)) } },
                        onPlus = { scope.launch { prefs.setTallSplitScreens((state.tallSplitScreens + 0.5f).coerceAtMost(8f)) } },
                    )
                }
            },
        ) { on -> scope.launch { prefs.setTallPageSplit(on) } }
    }
}

/**
 * What the colour-blind-safe theme looks like, and what someone with that colour vision sees: the
 * same swatches run through the CVD simulation matrix. If a pair still collides on the right, the
 * theme needs work.
 */
@Composable
private fun CvdPreview(theme: CvdTheme) {
    val scheme = remember(theme) { cvdColorScheme(theme, dark = true) } ?: return
    val swatches = listOf(
        "Primary" to scheme.primary,
        "Secondary" to scheme.secondary,
        "Tertiary" to scheme.tertiary,
        "Error" to scheme.error,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Simulation preview", style = MaterialTheme.typography.labelLarge)
        Text(
            "Left: the theme. Right: how it looks with ${theme.name}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        swatches.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Box(Modifier.size(40.dp, 24.dp).clip(CircleShape).background(color))
                Box(Modifier.size(40.dp, 24.dp).clip(CircleShape).background(simulateCvd(color, theme)))
            }
        }
        // The red/green pair the default theme leans on, to show why the CVD theme exists.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Default red/green", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Box(Modifier.size(40.dp, 24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
            Box(Modifier.size(40.dp, 24.dp).clip(CircleShape).background(simulateCvd(MaterialTheme.colorScheme.error, theme)))
        }
        Text(
            "Nothing meaningful in Dexter relies on colour alone; errors always pair with an icon or label.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
