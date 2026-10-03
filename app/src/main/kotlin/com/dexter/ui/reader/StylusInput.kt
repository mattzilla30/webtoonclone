package com.dexter.ui.reader

import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

/** Diameter of the hover magnifier, and how far it zooms. */
private val PEEK_DIAMETER = 168.dp
private const val PEEK_ZOOM = 2f

/**
 * S-Pen button page turns. The barrel button is the only stylus button Android reports, and Compose
 * does not surface it, so this reads the raw [MotionEvent]: the primary button turns forward, the
 * secondary button turns back. Returns the event unconsumed otherwise, so taps still work.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.stylusPenButton(
    enabled: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    pointerInteropFilter { event ->
        if (event.pointerCount > 0 && event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS &&
            event.action == MotionEvent.ACTION_BUTTON_PRESS
        ) {
            when {
                event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0 -> {
                    onNext()
                    true
                }
                event.buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY != 0 -> {
                    onPrevious()
                    true
                }
                else -> false
            }
        } else {
            false
        }
    }
}

/**
 * Tracks a hovering stylus for [StylusHoverPeek]: [onHover] gets the pen tip's position while it hovers,
 * and null once it lands or leaves. It watches from the reader itself, in the first pass, and takes
 * nothing, so taps and swipes still reach the pages and the buttons on them.
 */
fun Modifier.stylusHover(enabled: Boolean, onHover: (Offset?) -> Unit): Modifier = if (!enabled) {
    this
} else {
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                onHover(event.changes.firstOrNull { it.type == PointerType.Stylus && !it.pressed }?.position)
            }
        }
    }
}

/**
 * A magnifier that follows a hovering stylus: a circle showing the page under the pen tip at 2x,
 * with the hovered point centered. [hover] comes from [stylusHover]. It draws only, and never takes
 * a touch, so whatever sits beneath it stays tappable.
 *
 * @param containerSize the reader's size, so the zoomed image lines up with the page beneath it.
 */
@Composable
internal fun StylusHoverPeek(
    hover: Offset?,
    pageUrl: String?,
    containerSize: IntSize,
    diameter: Dp = PEEK_DIAMETER,
) {
    val density = LocalDensity.current
    val at = hover ?: return
    val url = pageUrl ?: return
    if (containerSize.width <= 0 || containerSize.height <= 0) return
    // The zoomed page is PEEK_ZOOM times the reader; offset so the hovered point sits centered.
    val zoomedWidth = containerSize.width * PEEK_ZOOM
    val zoomedHeight = containerSize.height * PEEK_ZOOM
    val diameterPx = with(density) { diameter.toPx() }
    val offsetX = diameterPx / 2f - at.x * PEEK_ZOOM
    val offsetY = diameterPx / 2f - at.y * PEEK_ZOOM
    Box(
        Modifier
            .offset { IntOffset((at.x - diameterPx / 2f).roundToInt(), (at.y - diameterPx / 2f).roundToInt()) }
            .size(diameter)
            .clip(CircleShape),
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(
                    with(density) { zoomedWidth.toDp() },
                    with(density) { zoomedHeight.toDp() },
                ),
        )
    }
}
