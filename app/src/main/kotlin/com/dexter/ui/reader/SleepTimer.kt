package com.dexter.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Sleep timer lengths offered in the dialog, in minutes. 0 is off. */
private val SLEEP_CHOICES = listOf(0, 15, 30, 45, 60)

/**
 * Fires [onFire] once [minutes] elapse. Keyed on [minutes], so changing the length restarts the
 * countdown. Does nothing when [minutes] is 0.
 */
@Composable
internal fun SleepTimer(
    minutes: Int,
    onFire: () -> Unit,
) {
    if (minutes <= 0) return
    LaunchedEffect(minutes) {
        delay(minutes * 60_000L)
        onFire()
    }
}

/**
 * Picks the sleep timer length. The countdown runs while the reader is open and restarts whenever
 * the length changes; firing stops auto-scroll, stops narration, and dims the screen.
 */
@Composable
internal fun SleepTimerDialog(
    currentMinutes: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                SLEEP_CHOICES.forEach { minutes ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(minutes) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = minutes == currentMinutes, onClick = null)
                        Text(
                            if (minutes == 0) "Off" else "$minutes minutes",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
                Text(
                    "When the time is up, auto-scroll stops, narration stops, and the screen dims. Changing the length restarts the countdown.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
