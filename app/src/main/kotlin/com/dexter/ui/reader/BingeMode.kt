package com.dexter.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Netflix-style binge watching for chapters: a countdown card at the end of a chapter that opens
 * [onAdvance] when it reaches zero. [onCancel] dismisses it for this chapter; "Play now" skips the
 * wait. The countdown restarts when the chapter changes.
 */
@Composable
internal fun BingeCountdown(
    seconds: Int,
    nextLabel: String,
    onAdvance: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = seconds.coerceIn(1, 30)
    var left by remember(total) { mutableIntStateOf(total) }
    val advance by rememberUpdatedState(onAdvance)
    LaunchedEffect(total) {
        while (left > 0) {
            delay(1_000)
            left--
        }
        advance()
    }
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Up next: $nextLabel", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { left / total.toFloat() },
                    modifier = Modifier.weight(1f),
                )
                Text("$left", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 12.dp))
            }
            Row(Modifier.align(Alignment.End).padding(top = 4.dp)) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Button(onClick = { advance() }, modifier = Modifier.padding(start = 8.dp)) { Text("Play now") }
            }
        }
    }
}
