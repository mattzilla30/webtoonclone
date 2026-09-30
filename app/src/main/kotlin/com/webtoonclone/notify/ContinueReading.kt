package com.webtoonclone.notify

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.webtoonclone.MainActivity
import com.webtoonclone.R
import com.webtoonclone.WebtoonApp
import com.webtoonclone.data.SavedSeries
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private const val CONTINUE_SHORTCUT = "continue"

/** The intent that opens the reader where [series] was left, or the series page when no chapter is known. */
private fun openIntent(context: Context, series: SavedSeries): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra(EXTRA_SERIES_ID, series.id)
        series.chapterId?.let { putExtra(EXTRA_CHAPTER_ID, it) }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

/** Keeps the launcher shortcut and the home screen widget pointing at the series you read last. */
fun updateContinueReading(context: Context, last: SavedSeries?) {
    if (last == null) {
        ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(CONTINUE_SHORTCUT))
    } else {
        val label = last.chapterNumber?.let { "Continue ${last.title} Ep. $it" } ?: "Continue ${last.title}"
        val shortcut = ShortcutInfoCompat.Builder(context, CONTINUE_SHORTCUT)
            .setShortLabel(last.title.take(24))
            .setLongLabel(label)
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(openIntent(context, last))
            .build()
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
    }
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(ComponentName(context, ContinueWidget::class.java))
    if (ids.isNotEmpty()) ids.forEach { manager.updateAppWidget(it, widgetViews(context, last)) }
}

private fun widgetViews(context: Context, last: SavedSeries?): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_continue)
    if (last == null) {
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_empty))
        views.setTextViewText(R.id.widget_chapter, "")
        views.setOnClickPendingIntent(R.id.widget_root, launchIntent(context, Intent(context, MainActivity::class.java)))
    } else {
        views.setTextViewText(R.id.widget_title, last.title)
        views.setTextViewText(R.id.widget_chapter, last.chapterNumber?.let { "Continue at Ep. $it" }.orEmpty())
        views.setOnClickPendingIntent(R.id.widget_root, launchIntent(context, openIntent(context, last)))
    }
    return views
}

private fun launchIntent(context: Context, intent: Intent): PendingIntent =
    PendingIntent.getActivity(context, intent.getStringExtra(EXTRA_SERIES_ID).hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

/** Home screen widget with the series you read last. Tapping it opens the reader at that chapter. */
class ContinueWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        // The widget redraws from the saved library. The read is short and local.
        val last = runBlocking { (context.applicationContext as WebtoonApp).libraryStore.data.first() }
            .recent.firstOrNull { it.chapterId != null }
        appWidgetIds.forEach { manager.updateAppWidget(it, widgetViews(context, last)) }
    }
}
