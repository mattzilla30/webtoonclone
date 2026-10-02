package com.dexter.platform

import android.app.Activity
import android.app.PictureInPictureParams
import android.util.Rational

/**
 * Picture-in-picture for the reader. Entering PiP is always an explicit user action from the
 * reader menu; the app never enters PiP on its own when leaving, so nothing surprises the reader.
 */
object PiPHelper {
    /** Phone-portrait window; the receiver scales the reader strip to fit. */
    fun params(aspect: Rational = Rational(9, 16)): PictureInPictureParams =
        PictureInPictureParams.Builder().setAspectRatio(aspect).build()
}

/**
 * Enters picture-in-picture with a 9:16 window. Returns false, doing nothing, on devices
 * without PiP support.
 */
fun Activity.enterReaderPiP(): Boolean = runCatching {
    enterPictureInPictureMode(PiPHelper.params())
}.getOrDefault(false)
