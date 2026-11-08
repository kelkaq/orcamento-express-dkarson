package com.example.jobs

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AppWorkScheduler {
    fun backupNow(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "dkarson_backup_after_save", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BackupWorker>().build()
        )
    }

    fun schedule(context: Context) {
        val manager = WorkManager.getInstance(context.applicationContext)
        manager.enqueueUniquePeriodicWork(
            "dkarson_local_backup_daily",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS).build()
        )
        manager.enqueueUniquePeriodicWork(
            "dkarson_agenda_reminders",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build()
        )
    }
}
