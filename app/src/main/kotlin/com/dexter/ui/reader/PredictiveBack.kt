package com.dexter.ui.reader

import android.os.Build
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Back navigation with the Android 14 predictive-back animation: as the back gesture progresses, the
 * content shrinks and slides toward the gesture edge, then [onBack] runs when the gesture commits.
 * Below Android 14 it is a plain back handler. Needs
 * `android:enableOnBackInvokedCallback="true"` in the manifest to animate on 14+.
 */
@Composable
internal fun PredictiveBack(
    enabled: Boolean,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    var progress by remember { mutableFloatStateOf(-1f) }
    var fromRight by remember { mutableStateOf(false) }
    // With the setting off, back is a plain handler; the system still pops the screen as before.
    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        PredictiveBackHandler(enabled = true) { events ->
            try {
                events.collect { event ->
                    progress = event.progress
                    fromRight = event.swipeEdge == BackEventCompat.EDGE_RIGHT
                }
            } finally {
                progress = -1f
            }
            onBack()
        }
    } else {
        BackHandler(enabled = enabled, onBack = onBack)
    }
    val shiftPx = with(LocalDensity.current) { 64.dp.toPx() }
    Box(
        Modifier.graphicsLayer {
            val p = progress
            if (p >= 0f) {
                // The window shrinking back, the way the system animates back-to-home.
                val scale = 1f - 0.12f * p
                scaleX = scale
                scaleY = scale
                translationX = (if (fromRight) -1f else 1f) * shiftPx * p
                alpha = 1f - 0.25f * p
            }
        },
    ) {
        content()
    }
}
