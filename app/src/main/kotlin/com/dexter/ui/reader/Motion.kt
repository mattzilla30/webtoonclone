package com.dexter.ui.reader

import android.content.Context
import com.dexter.data.Settings
import android.provider.Settings as SystemSettings

/**
 * True when page-turn animations and camera eases should be instant cuts instead: the reader's own
 * reduce-motion setting, e-ink mode, or the system's animator duration scale turned down to zero.
 */
fun reduceMotionEnabled(context: Context, settings: Settings): Boolean {
    if (settings.reduceMotion || settings.eInkMode) return true
    return runCatching {
        SystemSettings.Global.getFloat(context.contentResolver, SystemSettings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f) == 0f
}
