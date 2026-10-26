package com.example.nexonote.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.nexonote.NexoNoteApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> Unit
            else -> return
        }
        val pending = goAsync()
        val app = context.applicationContext as NexoNoteApplication
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                app.container.taskRepository.rescheduleReminders()
            } finally {
                pending.finish()
            }
        }
    }
}
