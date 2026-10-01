package com.dexter.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dexter.MainActivity
import com.dexter.data.SettingsStore
import com.dexter.data.StatsStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "goal-reminder"
private const val REMINDER_CHANNEL_ID = "goal_reminder"
private const val REMINDER_ID = 3

/** How long from [now] until the next [hour] o'clock, today or tomorrow. */
fun delayUntilHour(now: LocalDateTime, hour: Int): Duration {
    var next = now.toLocalDate().atTime(hour, 0)
    if (!next.isAfter(now)) next = next.plusDays(1)
    return Duration.between(now, next)
}

/** Once a day at the hour you chose, reminds you of your reading goal when today's chapters fall short of it. */
class GoalReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params),
    KoinComponent {
    private val settings: SettingsStore by inject()
    private val stats: StatsStore by inject()

    override suspend fun doWork(): Result {
        val current = settings.current()
        if (current.dailyGoal <= 0 || current.goalReminderHour < 0) return Result.success()
        val read = stats.readToday()
        if (read >= current.dailyGoal) return Result.success()
        val context = applicationContext
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(REMINDER_CHANNEL_ID, "Reading goal", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context,
            REMINDER_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_ROUTE, "home")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val left = current.dailyGoal - read
        manager.notify(
            REMINDER_ID,
            Notification.Builder(context, REMINDER_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle(if (left == 1) "1 chapter to your goal" else "$left chapters to your goal")
                .setContentText("You read $read of ${current.dailyGoal} today.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
        return Result.success()
    }

    companion object {
        /** Runs daily at [hour], or cancels the reminder when [hour] is -1. Safe to call on every launch. */
        fun sync(context: Context, hour: Int) {
            val manager = WorkManager.getInstance(context)
            if (hour !in 0..23) {
                manager.cancelUniqueWork(WORK_NAME)
                return
            }
            val delay = delayUntilHour(LocalDateTime.now(), hour)
            val request = PeriodicWorkRequestBuilder<GoalReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build()
            // A new hour replaces the old schedule, so the first run lands on the new hour.
            manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
        }
    }
}
