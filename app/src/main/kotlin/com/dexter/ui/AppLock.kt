package com.dexter.ui

import android.content.Context
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** How long the app may sit in the background before it locks again. */
private const val RELOCK_MS = 30_000L

/** Whether the app is locked, and the prompt that unlocks it. The lock itself shows only while the setting is on. */
object AppLock {
    /** Starts locked, so a cold start with the lock on asks first. */
    var locked by mutableStateOf(true)

    private var stoppedAt = 0L

    fun onStop() {
        stoppedAt = SystemClock.elapsedRealtime()
    }

    /** Locks again after the app spent more than half a minute in the background. */
    fun onStart() {
        if (stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt > RELOCK_MS) locked = true
    }

    /**
     * Asks for a fingerprint, a face, or the phone's PIN, pattern, or password, and calls [onResult] with
     * whether it passed. With nothing set up on the phone, it fails at once.
     */
    fun authenticate(context: Context, title: String, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(
            CancellationSignal(),
            context.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            },
        )
    }
}

/** Covers the app until you unlock it. The prompt opens by itself, and the button opens it again. */
@Composable
fun LockScreen() {
    val context = LocalContext.current
    val unlock = { AppLock.authenticate(context, "Unlock Dexter") { passed -> if (passed) AppLock.locked = false } }
    LaunchedEffect(Unit) { unlock() }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Touches stop here, so nothing under the lock can be tapped.
            .clickable(interactionSource = null, indication = null) {}
            .systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Dexter is locked", style = MaterialTheme.typography.titleLargeEmphasized)
            Button(onClick = unlock, modifier = Modifier.padding(top = 8.dp)) { Text("Unlock") }
        }
    }
}
