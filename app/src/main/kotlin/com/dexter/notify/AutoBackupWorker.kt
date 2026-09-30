package com.dexter.notify

import android.content.Context
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dexter.data.BackupService
import com.dexter.data.SettingsStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "auto-backup"

/** Writes a backup file into the folder chosen in Settings, once a day. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val settings: SettingsStore by inject()
    private val backups: BackupService by inject()

    override suspend fun doWork(): Result {
        val folder = settings.current().autoBackupFolder ?: return Result.success()
        return try {
            backups.writeToFolder(folder.toUri())
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        /** Schedules the daily backup when a folder is set, and cancels it when not. Safe to call on every launch. */
        fun sync(context: Context, folder: String?) {
            val manager = WorkManager.getInstance(context)
            if (folder == null) {
                manager.cancelUniqueWork(WORK_NAME)
            } else {
                val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).build()
                manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
            }
        }
    }
}
