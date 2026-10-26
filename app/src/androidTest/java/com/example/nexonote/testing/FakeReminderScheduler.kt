package com.example.nexonote.testing

import com.example.nexonote.domain.reminder.ReminderScheduler

class FakeReminderScheduler : ReminderScheduler {
    val scheduled = mutableMapOf<Long, Long>()
    val cancelled = mutableListOf<Long>()

    override fun schedule(taskId: Long, reminderAt: Long) {
        scheduled[taskId] = reminderAt
        cancelled.remove(taskId)
    }

    override fun cancel(taskId: Long) {
        scheduled.remove(taskId)
        if (taskId !in cancelled) cancelled += taskId
    }
}
