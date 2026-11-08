package com.example.jobs

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.database.AppDatabase

/** Agendamento aproximado por WorkManager: o Android pode adiar o aviso em economia de bateria. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext
            if (Build.VERSION.SDK_INT >= 33 &&
                app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return Result.success()
            }
            val dao = AppDatabase.getDatabase(app).agendaEventDao()
            val due = dao.getDueReminders(System.currentTimeMillis())
            if (due.isEmpty()) return Result.success()
            val notifications = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notifications.createNotificationChannel(
                    NotificationChannel("dkarson_agenda", "Lembretes de serviços",
                        NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            val intent = Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val open = PendingIntent.getActivity(
                app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            due.forEach { event ->
                val notification = NotificationCompat.Builder(app, "dkarson_agenda")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(if (event.kind == "SERVICE") "Serviço agendado" else "Retorno de orçamento")
                    .setContentText(event.title.take(120))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(
                        event.title + if (event.clientName.isNotBlank()) " — " + event.clientName else ""
                    ))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(app).notify(event.id.toInt(), notification)
                dao.markReminded(event.id, System.currentTimeMillis())
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
