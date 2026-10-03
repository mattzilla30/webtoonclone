package com.dexter.platform

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.dexter.DexterApp
import com.dexter.MainActivity
import com.dexter.R
import com.dexter.notify.EXTRA_CHAPTER_ID
import com.dexter.notify.EXTRA_SERIES_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile that jumps back into whatever you were reading: the most recent library
 * entry opens in the reader at its saved chapter, or the series page when no chapter is known.
 *
 * Manifest entry (see the integration snippet in the task report):
 * `<service android:name=".platform.ContinueReadingTile" ...><intent-filter><action android:name="android.service.quicksettings.action.QS_TILE" /></intent-filter></service>`
 */
class ContinueReadingTile : TileService() {
    // Main: the tile hands its PendingIntent to the system from the main thread, as TileService calls expect.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        qsTile?.let {
            it.label = "Continue reading"
            it.icon = Icon.createWithResource(this, R.mipmap.ic_launcher)
            it.state = Tile.STATE_INACTIVE
            it.updateTile()
        }
    }

    override fun onClick() {
        unlockAndRun {
            scope.launch {
                runCatching {
                    val app = applicationContext as DexterApp
                    val last = app.libraryStore.current().recent.firstOrNull()
                    val intent = Intent(applicationContext, MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        last?.let {
                            putExtra(EXTRA_SERIES_ID, it.id)
                            it.chapterId?.let { chapter -> putExtra(EXTRA_CHAPTER_ID, chapter) }
                        }
                    }
                    // The tile hands the system a PendingIntent, the form Android 14 and later require.
                    val pending = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    startActivityAndCollapse(pending)
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

/**
 * Quick Settings tile that runs a library update check on demand: it enqueues
 * [com.dexter.notify.NewChaptersWorker.checkNow] and shows active while the request is sent.
 *
 * Manifest entry mirrors [ContinueReadingTile] with `android:name=".platform.CheckUpdatesTile"`.
 */
class CheckUpdatesTile : TileService() {
    override fun onStartListening() {
        qsTile?.let {
            it.label = "Check for updates"
            it.icon = Icon.createWithResource(this, R.mipmap.ic_launcher)
            it.state = Tile.STATE_INACTIVE
            it.updateTile()
        }
    }

    override fun onClick() {
        unlockAndRun {
            qsTile?.let {
                it.state = Tile.STATE_ACTIVE
                it.updateTile()
            }
            runCatching { com.dexter.notify.NewChaptersWorker.checkNow(applicationContext) }
            qsTile?.let {
                it.state = Tile.STATE_INACTIVE
                it.updateTile()
            }
        }
    }
}

/** True on Android 7.0+, where Quick Settings tiles exist at all. */
fun quickSettingsSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
