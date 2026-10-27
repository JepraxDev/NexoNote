package com.example.nexonote.domain.model

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class EventsTest {
    private fun date(year: Int, month: Int, day: Int, hour: Int = 9, zone: String = "UTC") =
        Calendar.getInstance(TimeZone.getTimeZone(zone)).apply { clear(); set(year, month - 1, day, hour, 0) }.timeInMillis

    @Test fun validatesTitleDatesAndReminderChoices() {
        val input = EventInput("Evento", 100, 200)
        assertNull(validateEvent(input))
        assertNotNull(validateEvent(input.copy(title = " ")))
        assertNotNull(validateEvent(input.copy(endAt = 99)))
        assertNotNull(validateEvent(input.copy(reminderMinutes = 3)))
        assertNotNull(validateEvent(input.copy(zoneId = "Not/AZone")))
        assertNotNull(validateEvent(input.copy(allDay = true, endAt = 100)))
    }

    @Test fun monthlyClampsWithoutLosingOriginalDay() {
        val start = date(2026, 1, 31)
        val input = EventInput("Mensual", start, start + 3600000, zoneId = "UTC", recurrence = TaskRecurrence.MONTHLY)
        assertEquals(date(2026, 2, 28), occurrenceStart(input, 1))
        assertEquals(date(2026, 3, 31), occurrenceStart(input, 2))
        val leap = input.copy(startAt = date(2028, 1, 31))
        assertEquals(date(2028, 2, 29), occurrenceStart(leap, 1))
    }

    @Test fun dailyMaintainsLocalHourAcrossDst() {
        val zone = "America/New_York"
        val start = date(2026, 3, 7, zone = zone)
        val input = EventInput("Diario", start, start + 3600000, zoneId = zone, recurrence = TaskRecurrence.DAILY)
        assertEquals(date(2026, 3, 8, zone = zone), occurrenceStart(input, 1))
        assertEquals(23 * 3600000L, occurrenceStart(input, 1) - start)
        assertEquals(date(2026, 3, 9, zone = zone), occurrenceStart(input, 2))
    }

    @Test fun allDayUsesCivilDaysAcrossDst() {
        val zone = "America/New_York"
        val start = date(2026, 3, 7, 0, zone)
        val end = date(2026, 3, 8, 0, zone)
        val event = CalendarEvent(1, EventInput("Día", start, end, allDay = true, zoneId = zone, recurrence = TaskRecurrence.DAILY))
        val next = eventOccurrences(listOf(event), end, date(2026, 3, 9, 0, zone)).single()
        assertEquals(23 * 3600000L, next.endAt - next.startAt)
    }

    @Test fun repetitionEndIsInclusiveAndWeeklyHasSevenDays() {
        val start = date(2026, 1, 1)
        val event = CalendarEvent(1, EventInput("Semana", start, start + 1000, zoneId = "UTC",
            recurrence = TaskRecurrence.WEEKLY, repeatUntil = date(2026, 1, 15, 0)))
        assertEquals(listOf(1, 8, 15), eventOccurrences(listOf(event), start, date(2026, 2, 1)).map {
            eventCalendar(it.startAt, "UTC").get(Calendar.DAY_OF_MONTH)
        })
    }

    @Test fun exceptionsReplaceAndCancelWithoutDuplicateOccurrences() {
        val start = date(2026, 1, 1)
        val input = EventInput("Serie", start, start + 3600000, zoneId = "UTC", recurrence = TaskRecurrence.DAILY)
        val original = CalendarEvent(1, input)
        val moved = CalendarEvent(2, input.copy(startAt = date(2026, 1, 4), endAt = date(2026, 1, 4, 10), recurrence = TaskRecurrence.NONE), 1, date(2026, 1, 2))
        val cancelled = CalendarEvent(3, input, 1, date(2026, 1, 3), true)
        val items = eventOccurrences(listOf(original, moved, cancelled), start, date(2026, 1, 4, 0))
        assertEquals(listOf(start), items.map { it.startAt })
        assertEquals(2, eventOccurrences(listOf(original, moved, cancelled), date(2026, 1, 4, 0), date(2026, 1, 5, 0)).size)
    }

    @Test fun localizedWeekStart() {
        val time = date(2026, 1, 7, 12)
        assertEquals(Calendar.SUNDAY, eventCalendar(calendarWeek(time, Locale.US)).get(Calendar.DAY_OF_WEEK))
        assertEquals(Calendar.MONDAY, eventCalendar(calendarWeek(time, Locale.FRANCE)).get(Calendar.DAY_OF_WEEK))
    }

    @Test fun tasksAreProjectedOnceAndKeepStatusAndPriority() {
        val start = date(2026, 1, 1)
        val task = Task(10, "Pendiente", dueAt = start, dueHasTime = true, status = TaskStatus.IN_PROGRESS)
        val items = calendarActivities(emptyList(), listOf(task, task.copy(id = 11, dueAt = null)), start - 1, start + 1)
        assertEquals(1, items.size)
        assertEquals(task, items.single().task)
        assertNull(items.single().event)
    }

    @Test fun overlapsUseSeparateLanesAndReleaseAfterGroup() {
        fun item(id: String, start: Long, end: Long) = CalendarActivity(id, id, start, end, false, "#6750A4")
        val lanes = calendarLanes(listOf(item("a", 10, 30), item("b", 20, 40), item("c", 40, 50)))
        assertEquals(listOf(0, 1, 0), lanes.map { it.lane })
        assertEquals(listOf(2, 2, 1), lanes.map { it.lanes })
    }

    @Test fun eventZoneIsStableWhenDeviceZoneChanges() {
        val old = TimeZone.getDefault()
        try {
            val start = date(2026, 10, 31, zone = "America/New_York")
            val input = EventInput("Zona", start, start + 1000, zoneId = "America/New_York", recurrence = TaskRecurrence.DAILY)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals(date(2026, 11, 1, zone = "America/New_York"), occurrenceStart(input, 1))
        } finally { TimeZone.setDefault(old) }
    }
}
