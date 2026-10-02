package com.dexter.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
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

    SectionTitle("Narration")
    SwitchRow(
        "Read aloud",
        "Narrate chapters with text-to-speech, even with the screen off. Headset buttons pause and resume.",
        state.ttsEnabled,
    ) { on -> scope.launch { prefs.setTtsEnabled(on) } }
    if (state.ttsEnabled) {
        SwitchRow(
            "Turn pages while narrating",
            "The reader follows along as each page is announced.",
            state.ttsAutoAdvance,
        ) { on -> scope.launch { prefs.setTtsAutoAdvance(on) } }
        SpeechRateRow(state.ttsSpeed) { speed -> scope.launch { prefs.setTtsSpeed(speed) } }
    }

    SectionTitle("Hearing and voice")
    SwitchRow(
        "Voice control",
        "Say \"next page\", \"scroll down\", or \"go back\" in the reader. The mic only listens while you hold it on.",
        state.voiceControl,
    ) { on -> scope.launch { prefs.setVoiceControl(on) } }

    SectionTitle("Reading type")
    ChoiceRow(
        "App typeface",
        listOf(
            AppFont.System to "System",
            AppFont.OpenDyslexicStyle to "Dyslexia-friendly",
            AppFont.CustomFile to "Custom font file",
        ),
        state.font,
    ) { choice -> scope.launch { prefs.setFont(choice) } }
    if (state.font == AppFont.CustomFile) {
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) scope.launch {
                if (installCustomFont(context, uri) != null) prefs.setCustomFontUri(uri.toString())
            }
        }
        InfoRow(
            "Font file",
            if (state.customFontUri.isBlank()) "Pick a .ttf or .otf, such as the free OpenDyslexic from opendyslexic.org." else "Installed. Pick again to replace it.",
            onClick = { picker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream")) },
        )
    } else if (state.font == AppFont.OpenDyslexicStyle) {
        InfoRow(
            "About the dyslexia-friendly typeface",
            "Dexter widens letter spacing for legibility. For the real OpenDyslexic letterforms, download them free from opendyslexic.org and pick the file under \"Custom font file\".",
        )
    }

    SectionTitle("Colour vision")
    ChoiceRow(
        "Colour-blind-safe theme",
        listOf(
            CvdTheme.None to "Off",
            CvdTheme.DeuteranopiaSafe to "Deuteranopia",
            CvdTheme.ProtanopiaSafe to "Protanopia",
            CvdTheme.HighContrast to "High contrast",
        ),
        state.cvdTheme,
    ) { choice -> scope.launch { prefs.setCvdTheme(choice) } }
    if (state.cvdTheme != CvdTheme.None) {
        CvdPreview(state.cvdTheme)
    }

    SectionTitle("Tall pages")
    SwitchRow(
        "Split tall pages",
        "Cut oversized vertical pages into screen-sized segments so text stays legible.",
        state.tallPageSplit,
    ) { on -> scope.launch { prefs.setTallPageSplit(on) } }
    if (state.tallPageSplit) {
        SplitThresholdRow(state.tallSplitScreens) { screens -> scope.launch { prefs.setTallSplitScreens(screens) } }
    }
}

/** A minus/plus stepper for the narration speed, 0.5x to 2.0x in 0.1 steps. */
@Composable
private fun SpeechRateRow(speed: Float, onChange: (Float) -> Unit) {
    if (!matchesQuery(LocalSettingsQuery.current, "speech rate", "narration speed", "read aloud")) return
    CardRow {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Speech rate", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            FilledTonalIconButton(onClick = { onChange((speed - 0.1f).coerceAtLeast(0.5f)) }) { Text("\u2212") }
            Text("%.1fx".format(speed), style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(horizontal = 12.dp))
            FilledTonalIconButton(onClick = { onChange((speed + 0.1f).coerceAtLeast(0.5f).coerceAtMost(2f)) }) { Text("+") }
        }
    }
}

/** A minus/plus stepper for the tall-page split threshold, in screen heights. */
@Composable
private fun SplitThresholdRow(screens: Float, onChange: (Float) -> Unit) {
    if (!matchesQuery(LocalSettingsQuery.current, "split threshold", "tall pages")) return
    CardRow {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Split pages taller than", style = MaterialTheme.typography.bodyLarge)
                Text("Screen heights", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalIconButton(onClick = { onChange((screens - 0.5f).coerceAtLeast(1.5f)) }) { Text("\u2212") }
            Text("%.1f".format(screens), style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(horizontal = 12.dp))
            FilledTonalIconButton(onClick = { onChange((screens + 0.5f).coerceAtMost(8f)) }) { Text("+") }
        }
    }
}

/**
 * What the colour-blind-safe theme looks like, and what someone with that colour vision sees: the
 * same swatches run through the CVD simulation matrix. If a pair still collides on the right, the
 * theme needs work.
 */
@Composable
private fun CvdPreview(theme: CvdTheme) {
    if (!matchesQuery(LocalSettingsQuery.current, "preview", "colour-blind", "simulation")) return
    val scheme = remember(theme) { cvdColorScheme(theme, dark = true) } ?: return
    val swatches = listOf(
        "Primary" to scheme.primary,
        "Secondary" to scheme.secondary,
        "Tertiary" to scheme.tertiary,
        "Error" to scheme.error,
    )
    CardRow {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Simulation preview", style = MaterialTheme.typography.bodyLarge)
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
}
