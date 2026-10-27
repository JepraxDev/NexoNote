package com.example.nexonote.domain.repository

import com.example.nexonote.domain.model.CalendarEvent
import com.example.nexonote.domain.model.EventInput
import com.example.nexonote.domain.model.EventOccurrence
import kotlinx.coroutines.flow.Flow

interface EventRepository {
    fun observeAll(): Flow<List<CalendarEvent>>
    suspend fun save(input: EventInput, id: Long? = null): Long
    suspend fun saveOccurrence(seriesId: Long, originalStart: Long, input: EventInput): Long
    suspend fun delete(id: Long)
    suspend fun deleteOccurrence(seriesId: Long, originalStart: Long)
    suspend fun rescheduleReminders()
    suspend fun deliverReminder(id: Long, revision: Long, start: Long, notify: (EventOccurrence) -> Unit)
}

interface EventReminderScheduler {
    fun schedule(id: Long, revision: Long, start: Long, reminderAt: Long)
    fun cancel(id: Long)
}
