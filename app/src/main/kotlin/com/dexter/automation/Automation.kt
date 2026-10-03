package com.dexter.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.dexter.data.PowerPrefs
import com.dexter.notify.NewChaptersWorker
import kotlinx.coroutines.runBlocking

/**
 * Public automation actions. Send these as explicit broadcasts to Dexter (for example from Tasker,
 * MacroDroid, or Automate) to script the app. Dexter only honours them while the "Automation
 * intents" power-user setting is on, so nothing can drive the app behind your back by default.
 *
 * Examples (Tasker "Send Intent" action, target Broadcast Receiver):
 * - Check the library now:
 *   action=com.dexter.automation.LIBRARY_UPDATE, extra force:boolean=true
 * - Open where you left off:
 *   action=com.dexter.automation.OPEN_CONTINUE_READING
 * - Open a chapter directly:
 *   action=com.dexter.automation.OPEN_READER, extra series_id:string=..., extra chapter_id:string=...
 */
object DexterAutomation {
    /** Run a library update check now. Boolean extra [EXTRA_FORCE] skips per-series intervals. */
    const val ACTION_LIBRARY_UPDATE = "com.dexter.automation.LIBRARY_UPDATE"

    /** Open the app on whatever you were reading. No extras. */
    const val ACTION_OPEN_CONTINUE_READING = "com.dexter.automation.OPEN_CONTINUE_READING"

    /** Open the reader on a chapter. Requires [EXTRA_SERIES_ID] and [EXTRA_CHAPTER_ID]. */
    const val ACTION_OPEN_READER = "com.dexter.automation.OPEN_READER"

    /** The series id, for [ACTION_OPEN_READER]. */
    const val EXTRA_SERIES_ID = "series_id"

    /** The chapter id, for [ACTION_OPEN_READER]. */
    const val EXTRA_CHAPTER_ID = "chapter_id"

    /** Skip per-series update intervals on [ACTION_LIBRARY_UPDATE]. Boolean, defaults to false. */
    const val EXTRA_FORCE = "force"

    /** Deep-link routes the app opens for the two "open" actions, mirroring [com.dexter.notify.openRoutes]. */
    fun openRoute(seriesId: String, chapterId: String?): String =
        if (chapterId == null) "series/$seriesId" else "series/$seriesId/$chapterId"
}

/**
 * Receives [DexterAutomation] broadcasts. Manifest entry and the MainActivity deep-link handling
 * are in the batch report; the receiver itself only builds an explicit intent for the launcher
 * activity and lets the navigation graph do the rest.
 */
class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val enabled = runBlocking { PowerPrefs(context).current().taskerEnabled }
        if (!enabled) return
        when (intent.action) {
            DexterAutomation.ACTION_LIBRARY_UPDATE -> {
                // A forced check runs outside the schedule; the worker still honours quiet hours.
                NewChaptersWorker.checkNow(context)
            }
            DexterAutomation.ACTION_OPEN_CONTINUE_READING,
            DexterAutomation.ACTION_OPEN_READER,
            -> {
                val seriesId = intent.getStringExtra(DexterAutomation.EXTRA_SERIES_ID)
                val chapterId = intent.getStringExtra(DexterAutomation.EXTRA_CHAPTER_ID)
                val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                launch.action = intent.action
                if (seriesId != null) launch.putExtra(DexterAutomation.EXTRA_SERIES_ID, seriesId)
                if (chapterId != null) launch.putExtra(DexterAutomation.EXTRA_CHAPTER_ID, chapterId)
                ContextCompat.startActivity(context, launch, null)
            }
        }
    }
}
