package com.example.nexonote.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.nexonote.NexoNoteApplication
import com.example.nexonote.domain.model.TaskStatus
import kotlinx.coroutines.CancellationException

class TaskReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, 0L)
        val expectedReminder = inputData.getLong(KEY_REMINDER_AT, 0L)
        if (taskId <= 0L) return Result.success()
        return try {
            val container = (applicationContext as NexoNoteApplication).container
            val task = container.taskRepository.findTask(taskId)
            if (task != null &&
                task.status != TaskStatus.COMPLETED &&
                task.reminderEnabled &&
                task.reminderAt == expectedReminder
            ) {
                ReminderNotifications.show(applicationContext, task)
            }
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_REMINDER_AT = "reminder_at"
        fun workName(taskId: Long): String = "task-reminder-$taskId"
        fun input(taskId: Long, reminderAt: Long) = workDataOf(
            KEY_TASK_ID to taskId,
            KEY_REMINDER_AT to reminderAt,
        )
    }
}
