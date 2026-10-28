package com.example.nexonote

import android.app.Application
import com.example.nexonote.di.AppContainer
import com.example.nexonote.reminder.ReminderNotifications

class NexoNoteApplication : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.createChannel(this)
        com.example.nexonote.reminder.EventRescheduleWorker.enqueue(this)
        container.startBackupSchedule()
    }
}
