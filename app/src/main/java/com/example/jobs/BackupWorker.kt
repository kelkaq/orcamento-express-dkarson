package com.example.jobs

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.database.AppDatabase
import com.example.data.repository.QuoteRepository
import com.example.util.BackupManager
import com.example.billing.EntitlementStore
import com.example.cloud.CloudClient
import java.io.File

/** Cópia automática LOCAL diária; usuário deve exportar ou conectar nuvem para proteger contra perda do celular. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val repo = QuoteRepository(AppDatabase.getDatabase(applicationContext))
            val file = BackupManager.exportBackup(applicationContext, repo, isPreventive = true)
            if (file.length() == 0L) return Result.retry()
            val directory = File(applicationContext.filesDir, "preventive_backups")
            directory.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(7)?.forEach { it.delete() }

            // Só envia para a nuvem se o usuário já conectou sua conta e possui Pro Online.
            // Conflitos de revisão NUNCA são resolvidos com sobrescrita automática.
            val cloud = CloudClient(applicationContext)
            if (cloud.configured && cloud.isLoggedIn() &&
                EntitlementStore(applicationContext).isOnlineNow()) {
                runCatching { cloud.uploadBackup(repo) }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
