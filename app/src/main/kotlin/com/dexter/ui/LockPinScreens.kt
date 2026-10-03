package com.dexter.ui

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dexter.data.LockPinStore
import com.dexter.data.PinAttempt
import kotlinx.coroutines.launch

/** Shortest app PIN the setup screen accepts. */
private const val MIN_PIN_LENGTH = 4

/** Longest app PIN the setup screen accepts, and when the entry screen submits by itself. */
private const val MAX_PIN_LENGTH = 8

/** Sets the app's own lock PIN: type it, then type it again to confirm. Digits only, 4 to 8 long. */
@Composable
fun PinSetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(PinSetupStage.Enter) }
    var pin by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var shakeTrigger by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    val shakeX = rememberShake(shakeTrigger)

    fun typeDigit(digit: Char) {
        if (pin.length < MAX_PIN_LENGTH) {
            pin += digit
            error = null
        }
    }

    fun backspace() {
        if (pin.isNotEmpty()) {
            pin = pin.dropLast(1)
            error = null
        }
    }

    fun confirm() {
        if (pin == first) {
            saving = true
            scope.launch {
                LockPinStore(context).setPin(pin)
                saving = false
                onDone()
            }
        } else {
            error = "Those PINs don't match. Try again."
            shakeTrigger++
            pin = ""
        }
    }

    // The confirmation submits itself once it is as long as the first entry.
    LaunchedEffect(pin, stage) {
        if (stage == PinSetupStage.Confirm && pin.length == first.length && first.isNotEmpty()) confirm()
    }

    PinScaffold(
        icon = Icons.Default.Lock,
        title = if (stage == PinSetupStage.Enter) "Set an app PIN" else "Confirm your PIN",
        subtitle = "Digits only, $MIN_PIN_LENGTH to $MAX_PIN_LENGTH long.",
        pinLength = pin.length,
        shakeX = shakeX,
        error = error,
        onDigit = ::typeDigit,
        onBackspace = ::backspace,
    ) {
        when (stage) {
            PinSetupStage.Enter -> {
                Button(
                    onClick = {
                        first = pin
                        pin = ""
                        error = null
                        stage = PinSetupStage.Confirm
                    },
                    enabled = pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH,
                ) { Text("Continue") }
            }
            PinSetupStage.Confirm -> {
                TextButton(
                    onClick = {
                        pin = ""
                        first = ""
                        error = null
                        stage = PinSetupStage.Enter
                    },
                    enabled = !saving,
                ) { Text("Back") }
            }
        }
    }
}

/** Asks for the app PIN to unlock. Offers the phone's biometrics instead when it has them. */
@Composable
fun PinEntryScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var shakeTrigger by remember { mutableStateOf(0) }
    var checking by remember { mutableStateOf(false) }
    val shakeX = rememberShake(shakeTrigger)
    val biometrics = remember { biometricsAvailable(context) }

    fun typeDigit(digit: Char) {
        if (!checking && pin.length < MAX_PIN_LENGTH) {
            pin += digit
            error = null
        }
    }

    fun backspace() {
        if (!checking && pin.isNotEmpty()) {
            pin = pin.dropLast(1)
            error = null
        }
    }

    fun check() {
        if (checking || pin.length !in MIN_PIN_LENGTH..MAX_PIN_LENGTH) return
        checking = true
        scope.launch {
            val result = LockPinStore(context).attempt(pin)
            checking = false
            when (result) {
                PinAttempt.Passed -> onUnlocked()
                is PinAttempt.Wrong -> {
                    error = if (result.triesLeft <= 2) "Wrong PIN. ${result.triesLeft} more before a pause." else "Wrong PIN. Try again."
                    shakeTrigger++
                    pin = ""
                }
                is PinAttempt.LockedOut -> {
                    val seconds = (result.waitMs + 999) / 1000
                    error = if (seconds < 120) "Too many tries. Try again in $seconds seconds." else "Too many tries. Try again in ${(seconds + 59) / 60} minutes."
                    shakeTrigger++
                    pin = ""
                }
            }
        }
    }

    // A full-length PIN submits itself; shorter ones wait for the button.
    LaunchedEffect(pin) {
        if (pin.length == MAX_PIN_LENGTH) check()
    }

    PinScaffold(
        icon = Icons.Default.Lock,
        title = "Enter your app PIN",
        subtitle = "Dexter is locked.",
        pinLength = pin.length,
        shakeX = shakeX,
        error = error,
        onDigit = ::typeDigit,
        onBackspace = ::backspace,
    ) {
        Button(onClick = ::check, enabled = !checking && pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH) {
            Text(if (checking) "Checking…" else "Unlock")
        }
        if (biometrics) {
            TextButton(
                onClick = {
                    AppLock.authenticateBiometric(context, "Unlock Dexter") { passed ->
                        if (passed) onUnlocked()
                    }
                },
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Use biometrics instead")
            }
        }
    }
}

private enum class PinSetupStage { Enter, Confirm }

/** Whether this phone can do a biometric unlock right now. */
private fun biometricsAvailable(context: Context): Boolean {
    val manager = context.getSystemService(BiometricManager::class.java) ?: return false
    return manager.canAuthenticate(Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
}

/** A horizontal shake driven by [trigger]: bump it to shake the PIN dots once. */
@Composable
private fun rememberShake(trigger: Int): Float {
    val shakeX = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger != 0) {
            shakeX.animateTo(
                0f,
                keyframes {
                    durationMillis = 400
                    -14f at 80
                    14f at 160
                    -10f at 240
                    10f at 320
                    0f at 400
                },
            )
        }
    }
    return shakeX.value
}

/** The shared PIN layout: centered icon, title, dots, error, and numeric pad, like LockScreen. */
@Composable
private fun PinScaffold(
    icon: ImageVector,
    title: String,
    subtitle: String,
    pinLength: Int,
    shakeX: Float,
    error: String?,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    actions: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Touches stop here, so nothing under the lock can be tapped.
            .clickable(interactionSource = null, indication = null) {}
            .systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLargeEmphasized)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            PinDots(
                pinLength,
                modifier = Modifier.graphicsLayer { translationX = shakeX },
            )
            Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                if (error != null) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            PinPad(onDigit = onDigit, onBackspace = onBackspace)
            Spacer(Modifier.height(4.dp))
            actions()
        }
    }
}

/** Filled dots for the digits typed so far. */
@Composable
private fun PinDots(length: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(MAX_PIN_LENGTH) { i ->
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (i < length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

/** A phone-style numeric pad with a backspace key. */
@Composable
private fun PinPad(onDigit: (Char) -> Unit, onBackspace: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in listOf("123", "456", "789")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (digit in row) {
                    PinKey(label = digit.toString()) { onDigit(digit) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(72.dp))
            PinKey(label = "0") { onDigit('0') }
            IconButton(onClick = onBackspace, modifier = Modifier.size(72.dp)) {
                Icon(Icons.Default.Backspace, contentDescription = "Delete last digit")
            }
        }
    }
}

@Composable
private fun PinKey(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}
