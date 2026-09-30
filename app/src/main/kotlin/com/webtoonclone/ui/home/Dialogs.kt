package com.webtoonclone.ui.home

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.webtoonclone.ui.theme.Green

private val steps = listOf(
    "Find something to read" to "Search by title, genre, theme, or format. The Updates tab lists the newest chapters.",
    "Never miss a chapter" to "Tap Subscribe on a series, or long-press a tile on Home. The app checks about every 30 minutes and notifies you when a new chapter comes out.",
    "Read your way" to "Tap a page to show or hide the bars. The gear in the reader sets dimming, background, and auto-scroll. The rest is in Settings, under My Series.",
)

/** A short first-launch walkthrough. Skipping or finishing both mark it done. */
@Composable
fun WelcomeDialog(onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val (title, body) = steps[step]
    Dialog(onDismissRequest = onFinish) {
        Column(Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(20.dp)) {
            Text("${step + 1} of ${steps.size}", fontSize = 11.sp, color = Green, fontWeight = FontWeight.Bold)
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            Text(body, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Skip", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable(onClick = onFinish).padding(8.dp))
                Text(
                    if (step == steps.lastIndex) "Done" else "Next",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Green,
                    modifier = Modifier.clickable { if (step == steps.lastIndex) onFinish() else step++ }.padding(8.dp),
                )
            }
        }
    }
}

/** Offers to share the crash report saved last time. Sharing is the person's choice and goes wherever they pick. */
@Composable
fun CrashReportDialog(report: String, onDone: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("The app closed unexpectedly") },
        text = { Text("Share the crash report? It holds the error, the app version, and your device model, and nothing else.") },
        confirmButton = {
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Webtoon Clone crash report")
                    putExtra(Intent.EXTRA_TEXT, report)
                }
                context.startActivity(Intent.createChooser(send, null))
                onDone()
            }) { Text("Share") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("No thanks") } },
    )
}
