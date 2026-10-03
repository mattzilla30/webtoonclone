package com.dexter.ui.reader

import android.os.SystemClock
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Sleep timer lengths offered in the dialog, in minutes. 0 is off. */
private val SLEEP_CHOICES = listOf(0, 15, 30, 45, 60)

/**
 * The sleep timer's deadline, kept outside composition. Opening the next episode replaces the whole
 * reader destination, which would otherwise restart the countdown on every chapter.
 */
internal object SleepTimerClock {
    /** Elapsed-realtime millis when the countdown ends, or 0 when none is running. */
    var deadlineMs: Long = 0L

    /** (Re)starts the countdown from now. 0 minutes clears it. */
    fun start(minutes: Int) {
        deadlineMs = if (minutes > 0) SystemClock.elapsedRealtime() + minutes * 60_000L else 0L
    }

    /** Forgets any running countdown, e.g. when the reader closes. */
    fun clear() {
        deadlineMs = 0L
    }
}

/**
 * Fires [onFire] once [minutes] elapse. Changing the length restarts the countdown; a chapter change
 * keeps it: the deadline is process-wide and reused while it is still in the future. Does nothing
 * when [minutes] is 0.
 */
@Composable
internal fun SleepTimer(
    minutes: Int,
    onFire: () -> Unit,
) {
    // No side effects here: turning the timer off goes through the dialog's onSelect,
    // which already clears the clock.
    if (minutes <= 0) return
    val deadline = remember(minutes) {
        val now = SystemClock.elapsedRealtime()
        // A new chapter recreates this composition; keep the running countdown instead of restarting it.
        if (SleepTimerClock.deadlineMs <= now) SleepTimerClock.start(minutes)
        SleepTimerClock.deadlineMs
    }
    LaunchedEffect(deadline) {
        val wait = deadline - SystemClock.elapsedRealtime()
        if (wait > 0) delay(wait)
        SleepTimerClock.start(0)
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

