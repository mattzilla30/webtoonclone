package com.dexter.notify

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.dexter.DexterApp
import com.dexter.MainActivity
import com.dexter.R
import com.dexter.data.LibraryData
import com.dexter.ui.series.hasUnreadChapters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** One row of the updates widget: a series and its newest chapter, and whether you have read that far. */
data class WidgetUpdate(val seriesId: String, val title: String, val chapterNumber: String?, val unread: Boolean)

private const val ROWS = 4

/** Your subscriptions whose newest chapter changed most recently, newest first, unread ones before read ones. */
fun widgetUpdates(library: LibraryData, rows: Int = ROWS): List<WidgetUpdate> {
    val lastRead = library.recent.associate { it.id to it.chapterNumber }
    return library.subscribed
        .filter { it.knownChapterNumber != null }
        .map { WidgetUpdate(it.id, it.title, it.knownChapterNumber, hasUnreadChapters(it.knownChapterNumber, lastRead[it.id])) to it.at }
        .sortedWith(compareByDescending<Pair<WidgetUpdate, Long>> { it.first.unread }.thenByDescending { it.second })
        .take(rows)
        .map { it.first }
}

private val rowIds = listOf(
    Triple(R.id.updates_row_1, R.id.updates_title_1, R.id.updates_chapter_1),
    Triple(R.id.updates_row_2, R.id.updates_title_2, R.id.updates_chapter_2),
    Triple(R.id.updates_row_3, R.id.updates_title_3, R.id.updates_chapter_3),
    Triple(R.id.updates_row_4, R.id.updates_title_4, R.id.updates_chapter_4),
)

private fun opens(context: Context, requestCode: Int, extras: Intent.() -> Unit): PendingIntent = PendingIntent.getActivity(
    context,
    requestCode,
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        extras()
    },
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

private fun updatesViews(context: Context, updates: List<WidgetUpdate>): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_updates)
    views.setOnClickPendingIntent(R.id.updates_heading, opens(context, "widget-updates".hashCode()) { putExtra(EXTRA_ROUTE, "updates") })
    views.setViewVisibility(R.id.updates_empty, if (updates.isEmpty()) View.VISIBLE else View.GONE)
    rowIds.forEachIndexed { index, (row, title, chapter) ->
        val update = updates.getOrNull(index)
        if (update == null) {
            views.setViewVisibility(row, View.GONE)
        } else {
            views.setViewVisibility(row, View.VISIBLE)
            views.setTextViewText(title, if (update.unread) "● ${update.title}" else update.title)
            views.setTextViewText(chapter, update.chapterNumber?.let { "Ep. $it" }.orEmpty())
            views.setOnClickPendingIntent(row, opens(context, ("widget:" + update.seriesId).hashCode()) { putExtra(EXTRA_SERIES_ID, update.seriesId) })
        }
    }
    return views
}

/** Redraws every updates widget on the home screen with [updates]. */
fun refreshUpdatesWidgets(context: Context, updates: List<WidgetUpdate>) {
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(ComponentName(context, UpdatesWidget::class.java))
    if (ids.isEmpty()) return
    val views = updatesViews(context, updates)
    ids.forEach { manager.updateAppWidget(it, views) }
}

/** Home screen widget with your subscriptions' newest chapters. A dot marks one you have not read. */
class UpdatesWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val views = updatesViews(context, widgetUpdates((context.applicationContext as DexterApp).libraryStore.current()))
                appWidgetIds.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }
}
