package com.dexter.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri

/**
 * Opens [url] in the browser or the app that handles it. A phone with no browser, or one turned off,
 * gets a short message instead of a crash.
 */
fun Context.openLink(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "No app on this phone can open that link.", Toast.LENGTH_LONG).show()
    }
}
