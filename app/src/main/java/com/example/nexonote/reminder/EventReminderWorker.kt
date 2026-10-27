package com.example.nexonote.reminder

import android.content.Context
import androidx.work.*
import com.example.nexonote.NexoNoteApplication
import com.example.nexonote.domain.repository.EventReminderScheduler
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class WorkManagerEventScheduler(context: Context) : EventReminderScheduler {
    private val work = WorkManager.getInstance(context.applicationContext)
    override fun schedule(id: Long, revision: Long, start: Long, reminderAt: Long) {
        val request = OneTimeWorkRequestBuilder<EventReminderWorker>()
            .setInitialDelay((reminderAt - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("id" to id, "revision" to revision, "start" to start))
            .build()
        work.enqueueUniqueWork("event_reminder_$id", ExistingWorkPolicy.REPLACE, request)
    }
    override fun cancel(id: Long) { work.cancelUniqueWork("event_reminder_$id") }
}

class EventReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val app = applicationContext as NexoNoteApplication
        app.container.eventRepository.deliverReminder(inputData.getLong("id", 0),
            inputData.getLong("revision", 0), inputData.getLong("start", 0)) {
            ReminderNotifications.showEvent(applicationContext, it)
        }
        Result.success()
    } catch (cancelled: CancellationException) { throw cancelled }
      catch (_: Exception) { Result.retry() }
}

class EventRescheduleWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        (applicationContext as NexoNoteApplication).container.eventRepository.rescheduleReminders()
        Result.success()
    } catch (cancelled: CancellationException) { throw cancelled }
      catch (_: Exception) { Result.retry() }

    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("reschedule_events", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<EventRescheduleWorker>().build())
        }
    }
}
