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
import com.dexter.data.LockMode

/** How long the app may sit in the background before it locks again, unless settings say otherwise. */
const val DEFAULT_RELOCK_MS = 30_000L

/**
 * Whether the app is locked, and the prompt that unlocks it. The lock itself shows only while the
 * setting is on. [lockMode] and [relockTimeoutMs] are pushed from Settings; the UI reads them live.
 */
object AppLock {
    /** Starts locked, so a cold start with the lock on asks first. */
    var locked by mutableStateOf(true)

    /** Which secret the lock asks for. */
    var lockMode by mutableStateOf(LockMode.BiometricOrDeviceCredential)

    /** How long the app may sit in the background before it locks again. */
    var relockTimeoutMs = DEFAULT_RELOCK_MS

    private var stoppedAt = 0L

    fun onStop() {
        stoppedAt = SystemClock.elapsedRealtime()
    }

    /** Locks again after the app spent more than [relockTimeoutMs] in the background. */
    fun onStart() {
        if (stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt > relockTimeoutMs) locked = true
    }

    /** What came back from asking for the unlock secret. */
    sealed interface LockAuth {
        /** The secret checked out. */
        data object Passed : LockAuth

        /** The secret was wrong or the prompt was dismissed. */
        data object Failed : LockAuth

        /** The app-PIN mode is on, so show [PinEntryScreen] instead of the system prompt. */
        data object NeedsAppPin : LockAuth
    }

    /**
     * Unlocks with whatever [lockMode] is set. In app-PIN mode no prompt shows; [onResult] gets
     * [LockAuth.NeedsAppPin] so the caller can show [PinEntryScreen] instead of the system prompt.
     */
    fun authenticate(context: Context, title: String, onResult: (LockAuth) -> Unit) {
        if (lockMode == LockMode.AppPin) {
            onResult(LockAuth.NeedsAppPin)
            return
        }
        authenticateBiometric(context, title) { passed ->
            onResult(if (passed) LockAuth.Passed else LockAuth.Failed)
        }
    }

    /**
     * Asks for a fingerprint, a face, or the phone's PIN, pattern, or password, and calls [onResult]
     * with whether it passed. With nothing set up on the phone, it fails at once. This is the
     * strong-factor path: settings use it directly so flipping the lock switch always proves the
     * phone's own credential, whatever [lockMode] is set.
     */
    fun authenticate(context: Context, title: String, onResult: (Boolean) -> Unit) {
        authenticateBiometric(context, title, onResult)
    }

    /** The platform prompt itself: fingerprint, face, or the phone's PIN, pattern, or password. */
    fun authenticateBiometric(context: Context, title: String, onResult: (Boolean) -> Unit) {
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

/**
 * Covers the app until you unlock it. The app-PIN mode shows its own keypad; otherwise the phone's
 * prompt opens by itself, and the button opens it again.
 */
@Composable
fun LockScreen() {
    if (AppLock.lockMode == LockMode.AppPin) {
        PinEntryScreen(onUnlocked = { AppLock.locked = false })
    } else {
        BiometricLockScreen()
    }
}

/** The prompt opens by itself, and the button opens it again. */
@Composable
private fun BiometricLockScreen() {
    val context = LocalContext.current
    val unlock = {
        AppLock.authenticate(context, "Unlock Dexter") { result ->
            when (result) {
                AppLock.LockAuth.Passed -> AppLock.locked = false
                AppLock.LockAuth.Failed, AppLock.LockAuth.NeedsAppPin -> Unit
            }
        }
    }
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
