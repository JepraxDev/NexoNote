package com.example.nexonote.domain.model

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

val EVENT_REMINDER_MINUTES = listOf(0, 5, 10, 15, 30, 60, 1440)

data class EventInput(
    val title: String,
    val startAt: Long,
    val endAt: Long,
    val description: String = "",
    val allDay: Boolean = false,
    val color: String = "#6750A4",
    val category: String = "",
    val zoneId: String = TimeZone.getDefault().id,
    val recurrence: TaskRecurrence = TaskRecurrence.NONE,
    val repeatUntil: Long? = null,
    val reminderMinutes: Int? = null,
    val favorite: Boolean = false,
    val folderId: Long? = null,
    val categoryId: Long? = null,
    val tags: List<String> = emptyList(),
)

data class CalendarEvent(
    val id: Long,
    val input: EventInput,
    val parentId: Long? = null,
    val originalStart: Long? = null,
    val cancelled: Boolean = false,
    val revision: Long = 1,
    val notifiedStart: Long? = null,
    val plannedStart: Long? = null,
)

data class EventOccurrence(val event: CalendarEvent, val startAt: Long, val endAt: Long) {
    val key: String get() = "event:${event.id}:$startAt"
}

fun eventCalendar(time: Long, zone: String = TimeZone.getDefault().id): Calendar =
    Calendar.getInstance(TimeZone.getTimeZone(zone)).apply { timeInMillis = time }

fun calendarDay(time: Long, zone: String = TimeZone.getDefault().id): Long =
    eventCalendar(time, zone).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

fun calendarAdd(time: Long, field: Int, amount: Int, zone: String = TimeZone.getDefault().id): Long =
    eventCalendar(time, zone).apply { add(field, amount) }.timeInMillis

fun calendarWeek(time: Long, locale: Locale = Locale.getDefault()): Long {
    val day = Calendar.getInstance(locale).apply { timeInMillis = calendarDay(time) }
    day.add(Calendar.DAY_OF_YEAR, -((day.get(Calendar.DAY_OF_WEEK) - day.firstDayOfWeek + 7) % 7))
    return day.timeInMillis
}

fun validateEvent(input: EventInput): String? = when {
    input.title.isBlank() -> "El título es obligatorio."
    input.title.trim().length > 120 -> "El título admite hasta 120 caracteres."
    input.description.length > 5000 || input.category.length > 80 -> "Descripción o categoría demasiado larga."
    input.endAt < input.startAt -> "El final no puede ser anterior al inicio."
    input.allDay && input.endAt <= input.startAt -> "Selecciona al menos un día completo."
    input.zoneId !in TimeZone.getAvailableIDs() -> "Zona horaria no válida."
    input.allDay && (calendarDay(input.startAt, input.zoneId) != input.startAt || calendarDay(input.endAt, input.zoneId) != input.endAt) ->
        "Los eventos de día completo deben comenzar y terminar a medianoche."
    !input.color.matches(Regex("#[0-9a-fA-F]{6}")) -> "Color no válido."
    input.reminderMinutes != null && input.reminderMinutes !in EVENT_REMINDER_MINUTES -> "Recordatorio no válido."
    input.recurrence != TaskRecurrence.NONE && input.repeatUntil != null &&
        calendarDay(input.repeatUntil, input.zoneId) < calendarDay(input.startAt, input.zoneId) ->
        "La repetición debe terminar en la fecha de inicio o después."
    else -> null
}

// Compute every monthly occurrence from its original anchor (Jan 31 → Feb 28 → Mar 31).
fun occurrenceStart(input: EventInput, index: Int): Long {
    val calendar = eventCalendar(input.startAt, input.zoneId)
    when (input.recurrence) {
        TaskRecurrence.DAILY -> calendar.add(Calendar.DAY_OF_YEAR, index)
        TaskRecurrence.WEEKLY -> calendar.add(Calendar.DAY_OF_YEAR, index * 7)
        TaskRecurrence.MONTHLY -> {
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.add(Calendar.MONTH, index)
            calendar.set(Calendar.DAY_OF_MONTH, day.coerceAtMost(calendar.getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        TaskRecurrence.NONE -> Unit
    }
    return calendar.timeInMillis
}

private fun occurrenceEnd(input: EventInput, start: Long): Long {
    if (!input.allDay) return start + (input.endAt - input.startAt)
    // Civil-day duration, not a fixed number of milliseconds across daylight-saving changes.
    val utc = TimeZone.getTimeZone("UTC")
    fun civil(time: Long): Long {
        val c = eventCalendar(time, input.zoneId)
        return Calendar.getInstance(utc).apply {
            clear(); set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
    val days = ((civil(input.endAt) - civil(input.startAt)) / 86_400_000L).toInt().coerceAtLeast(1)
    return calendarAdd(start, Calendar.DAY_OF_YEAR, days, input.zoneId)
}

fun eventOccurrences(events: List<CalendarEvent>, from: Long, until: Long): List<EventOccurrence> {
    if (until <= from) return emptyList()
    val overrides = events.filter { it.parentId != null }.associateBy { it.parentId to it.originalStart }
    val result = mutableListOf<EventOccurrence>()
    events.filter { it.parentId == null && !it.cancelled }.forEach { event ->
        val input = event.input
        val duration = (input.endAt - input.startAt).coerceAtLeast(1)
        val seek = from - duration - 172_800_000L
        val anchor = eventCalendar(input.startAt, input.zoneId)
        val target = eventCalendar(seek, input.zoneId)
        var index = when (input.recurrence) {
            TaskRecurrence.DAILY -> ((seek - input.startAt) / 86_400_000L).toInt() - 2
            TaskRecurrence.WEEKLY -> ((seek - input.startAt) / (7 * 86_400_000L)).toInt() - 2
            TaskRecurrence.MONTHLY -> (target.get(Calendar.YEAR) - anchor.get(Calendar.YEAR)) * 12 +
                target.get(Calendar.MONTH) - anchor.get(Calendar.MONTH) - 1
            TaskRecurrence.NONE -> 0
        }.coerceAtLeast(0)
        while (true) {
            val start = occurrenceStart(input, index)
            if (start >= until || (input.recurrence != TaskRecurrence.NONE && input.repeatUntil != null &&
                    calendarDay(start, input.zoneId) > calendarDay(input.repeatUntil, input.zoneId))) break
            val end = occurrenceEnd(input, start)
            if (end.coerceAtLeast(start + 1) > from && overrides[event.id to start] == null) {
                result.add(EventOccurrence(event, start, end))
            }
            if (input.recurrence == TaskRecurrence.NONE) break
            index++
        }
    }
    events.filter { it.parentId != null && !it.cancelled }.forEach { event ->
        if (event.input.startAt < until && event.input.endAt.coerceAtLeast(event.input.startAt + 1) > from) {
            result.add(EventOccurrence(event, event.input.startAt, event.input.endAt))
        }
    }
    return result.sortedWith(compareBy<EventOccurrence> { it.startAt }.thenBy { it.event.id })
}

data class CalendarActivity(
    val key: String, val title: String, val startAt: Long, val endAt: Long,
    val allDay: Boolean, val color: String, val event: EventOccurrence? = null, val task: Task? = null,
)

fun calendarActivities(events: List<CalendarEvent>, tasks: List<Task>, from: Long, until: Long): List<CalendarActivity> {
    val eventItems = eventOccurrences(events, from, until).map {
        CalendarActivity(it.key, it.event.input.title, it.startAt, it.endAt, it.event.input.allDay, it.event.input.color, event = it)
    }
    val taskItems = tasks.mapNotNull { task ->
        val due = task.dueAt ?: return@mapNotNull null
        if (due !in from until until) return@mapNotNull null
        CalendarActivity("task:${task.id}", task.displayTitle, due, due + 1_800_000,
            !task.dueHasTime, "#006A6A", task = task)
    }
    return (eventItems + taskItems).sortedWith(compareBy<CalendarActivity> { it.startAt }.thenBy { it.key })
}

data class CalendarLane(val activity: CalendarActivity, val lane: Int, val lanes: Int)

fun calendarLanes(items: List<CalendarActivity>): List<CalendarLane> {
    val result = mutableListOf<CalendarLane>()
    val group = mutableListOf<Pair<CalendarActivity, Int>>()
    val ends = mutableListOf<Long>()
    fun flush() { group.forEach { (item, lane) -> result.add(CalendarLane(item, lane, ends.size)) }; group.clear(); ends.clear() }
    items.sortedBy { it.startAt }.forEach { item ->
        if (ends.isNotEmpty() && ends.all { it <= item.startAt }) flush()
        val free = ends.indexOfFirst { it <= item.startAt }
        val lane = if (free < 0) ends.size.also { ends.add(item.endAt.coerceAtLeast(item.startAt + 1)) }
            else free.also { ends[it] = item.endAt.coerceAtLeast(item.startAt + 1) }
        group.add(item to lane)
    }
    flush()
    return result
}
