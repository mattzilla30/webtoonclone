package com.dexter.ui.reader

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Carries Bluetooth clicker and gamepad presses from the activity to the reader, mirroring
 * [VolumeKeyPager]. The reader sets [active] while it is on screen and the gamepad setting is on.
 * Cheap Bluetooth page-turn clickers usually send volume or media keys, which are covered here too.
 */
object GamepadKeys {
    @Volatile var active = false

    val events = MutableSharedFlow<Int>(extraBufferCapacity = 4)

    /** True when this key press was taken. Only the first key-down scrolls; holds do not repeat. */
    fun handle(event: KeyEvent): Boolean {
        val direction = gamepadDirection(event.keyCode) ?: return false
        if (!active) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) events.tryEmit(direction)
        return true
    }
}

/**
 * -1 for back, +1 for forward, null for any other key. A and D-pad right/down turn forward; B and
 * D-pad left/up turn back. Media next/previous cover clickers that pose as headsets.
 */
fun gamepadDirection(keyCode: Int): Int? = when (keyCode) {
    KeyEvent.KEYCODE_BUTTON_A,
    KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_MEDIA_NEXT,
    KeyEvent.KEYCODE_PAGE_DOWN -> 1
    KeyEvent.KEYCODE_BUTTON_B,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_MEDIA_PREVIOUS,
    KeyEvent.KEYCODE_PAGE_UP -> -1
    else -> null
}
