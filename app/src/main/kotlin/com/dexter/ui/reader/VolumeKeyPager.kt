package com.dexter.ui.reader

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Carries volume key presses from the activity to the reader. The reader sets [active] while it is
 * on screen and the setting is on. Up scrolls back a page (-1) and down scrolls forward (+1).
 */
object VolumeKeyPager {
    @Volatile var active = false

    val events = MutableSharedFlow<Int>(extraBufferCapacity = 4)

    /** True when this key press was taken. Only the key-down event scrolls. */
    fun handle(event: KeyEvent): Boolean {
        val direction = volumeDirection(event.keyCode) ?: return false
        if (!active) return false
        if (event.action == KeyEvent.ACTION_DOWN) events.tryEmit(direction)
        return true
    }
}

/** -1 for volume up, +1 for volume down, null for any other key. */
fun volumeDirection(keyCode: Int): Int? = when (keyCode) {
    KeyEvent.KEYCODE_VOLUME_UP -> -1
    KeyEvent.KEYCODE_VOLUME_DOWN -> 1
    else -> null
}

/** How far one volume key press scrolls: most of the visible height, so a little overlap remains. */
fun pageScrollAmount(viewportHeight: Int, direction: Int): Float = direction * viewportHeight * 0.85f
