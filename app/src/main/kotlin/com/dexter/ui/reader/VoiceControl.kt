package com.dexter.ui.reader

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow

/** Voice commands the reader understands. */
enum class VoiceCommand { NextPage, PreviousPage, ScrollDown, ScrollUp }

/**
 * Carries recognised voice commands from the recogniser to the reader, mirroring [VolumeKeyPager].
 * The reader sets [active] while it is on screen and voice control is on.
 */
object VoiceCommands {
    @Volatile var active = false

    val events = MutableSharedFlow<VoiceCommand>(extraBufferCapacity = 4)

    fun emit(command: VoiceCommand) {
        if (active) events.tryEmit(command)
    }
}

/**
 * Matches a recognition result against the supported commands. Returns null for anything else, so
 * stray speech never turns a page.
 */
fun parseVoiceCommand(text: String): VoiceCommand? {
    val words = text.lowercase()
    return when {
        "previous" in words || "go back" in words || ("back" in words && "page" in words) -> VoiceCommand.PreviousPage
        "next" in words -> VoiceCommand.NextPage
        "scroll down" in words || "down" in words -> VoiceCommand.ScrollDown
        "scroll up" in words || "up" in words -> VoiceCommand.ScrollUp
        else -> null
    }
}

/**
 * A one-shot speech recogniser: start it, speak a command, and it stops itself on the first result.
 * Create it once per reader session and call [destroy] when the reader leaves.
 */
class VoiceRecognizer(context: Context) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    /** False on devices with no recognition service, where the mic toggle hides itself. */
    val available: Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    /** True while the recogniser is listening. Observed by the mic toggle. */
    var onListeningChange: (Boolean) -> Unit = {}

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = setListening(true)
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onError(error: Int) = setListening(false)
        override fun onPartialResults(partialResults: Bundle?) {
            val heard = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            heard.firstOrNull()?.let { parseVoiceCommand(it) }?.let { command ->
                VoiceCommands.emit(command)
                stop()
            }
        }
        override fun onResults(results: Bundle?) {
            val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            heard.firstOrNull()?.let { parseVoiceCommand(it) }?.let(VoiceCommands::emit)
            setListening(false)
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun setListening(value: Boolean) {
        listening = value
        onListeningChange(value)
    }

    /** Start listening for one command. No-op when already listening or unavailable. */
    fun start() {
        if (listening || !available) return
        val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).also { this.recognizer = it }
        recognizer.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        recognizer.startListening(intent)
    }

    fun stop() {
        recognizer?.stopListening()
        recognizer?.cancel()
        setListening(false)
    }

    fun destroy() {
        stop()
        recognizer?.destroy()
        recognizer = null
    }
}

/**
 * The reader's mic toggle. Asks for the microphone on first tap, then starts and stops [recognizer].
 * Hidden entirely when [recognizer] is unavailable on this device.
 */
@Composable
fun VoiceControlButton(recognizer: VoiceRecognizer, modifier: Modifier = Modifier) {
    if (!recognizer.available) return
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }
    DisposableEffect(recognizer) {
        recognizer.onListeningChange = { listening = it }
        onDispose { recognizer.onListeningChange = {} }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recognizer.start()
    }
    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Microphone for voice control") },
            text = { Text("Dexter listens for page commands like \"next page\" and \"scroll down\" while the mic is on. Nothing is recorded or sent anywhere.") },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text("Not now") }
            },
        )
    }
    IconButton(
        modifier = modifier,
        onClick = {
            if (listening) {
                recognizer.stop()
                return@IconButton
            }
            when {
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> recognizer.start()
                ActivityCompat.shouldShowRequestPermissionRationale(
                    context as Activity, Manifest.permission.RECORD_AUDIO,
                ) -> showRationale = true
                else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
    ) {
        Icon(
            if (listening) Icons.Filled.Mic else Icons.Filled.MicOff,
            contentDescription = if (listening) "Stop voice control" else "Voice control",
            tint = if (listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
