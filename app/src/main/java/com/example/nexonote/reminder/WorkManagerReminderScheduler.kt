package com.example.nexonote.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.nexonote.domain.reminder.ReminderScheduler
import java.util.concurrent.TimeUnit

class WorkManagerReminderScheduler(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : ReminderScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun schedule(taskId: Long, reminderAt: Long) {
        val delay = (reminderAt - clock()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(TaskReminderWorker.input(taskId, reminderAt))
            .addTag(TaskReminderWorker.workName(taskId))
            .build()
        workManager.enqueueUniqueWork(
            TaskReminderWorker.workName(taskId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun cancel(taskId: Long) {
        workManager.cancelUniqueWork(TaskReminderWorker.workName(taskId))
    }
}
