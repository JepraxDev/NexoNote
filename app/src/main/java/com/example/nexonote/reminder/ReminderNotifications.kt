package com.example.nexonote.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.nexonote.MainActivity
import com.example.nexonote.R
import com.example.nexonote.domain.model.Task
import com.example.nexonote.domain.model.EventOccurrence
import java.text.DateFormat
import java.util.Date
import java.util.Locale

object ReminderNotifications {
    const val CHANNEL_ID = "task_reminders"
    private val locale = Locale.forLanguageTag("es")

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recordatorios",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Avisos de tareas y eventos con recordatorio activo."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun show(context: Context, task: Task) {
        if (!canNotify(context)) return
        val openTask = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_TASK_ID, task.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            task.id.hashCode(),
            openTask,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val due = task.dueAt?.let {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
                .format(Date(it))
        }
        val text = if (due == null) "Tienes una tarea pendiente." else "Vence: $due"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.displayTitle)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId(task.id), notification)
        } catch (_: SecurityException) {
        }
    }

    private fun canNotify(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun showEvent(context: Context, occurrence: EventOccurrence) {
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.example.nexonote.EVENT_${occurrence.event.id}"
            putExtra(MainActivity.EXTRA_EVENT_DATE, occurrence.startAt)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(context, occurrence.event.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(occurrence.startAt))
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(occurrence.event.input.title)
            .setContentText(text).setContentIntent(pending).setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT).build()
        try { NotificationManagerCompat.from(context).notify("event:${occurrence.event.id}", 0, notification) }
        catch (_: SecurityException) { }
    }

    private fun notificationId(taskId: Long): Int = (taskId xor (taskId ushr 32)).toInt()
}
