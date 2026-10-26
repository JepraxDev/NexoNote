package com.example.nexonote.domain.reminder

interface ReminderScheduler {
    fun schedule(taskId: Long, reminderAt: Long)
    fun cancel(taskId: Long)
}
