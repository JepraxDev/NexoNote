package com.example.nexonote.domain.model

import java.util.Calendar
import org.junit.Assert.*
import org.junit.Test

class TasksTest {
    @Test fun validationRejectsMissingTitleAndInvalidSchedule() {
        assertEquals("El título es obligatorio.", validateTaskInput(TaskInput(title = " ")))
        assertEquals(
            "Añade una fecha de vencimiento para activar el recordatorio.",
            validateTaskInput(TaskInput(title = "Tarea", reminderEnabled = true)),
        )
        assertEquals(
            "Añade una fecha de vencimiento para repetir la tarea.",
            validateTaskInput(TaskInput(title = "Tarea", recurrence = TaskRecurrence.DAILY)),
        )
        val due = timestamp(2026, 5, 10, 12, 0)
        assertEquals(
            "La fecha de inicio no puede ser posterior al vencimiento.",
            validateTaskInput(
                TaskInput(title = "Tarea", startAt = due + 1, dueAt = due)
            ),
        )
        assertNull(validateTaskInput(TaskInput(title = "Tarea válida")))
    }

    @Test fun dateOnlyReminderUsesNineOClock() {
        val due = timestamp(2026, 5, 10, 0, 0)
        val reminder = taskReminderAt(due, dueHasTime = false)!!
        val calendar = Calendar.getInstance().apply { timeInMillis = reminder }
        assertEquals(2026, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.JUNE, calendar.get(Calendar.MONTH))
        assertEquals(10, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calendar.get(Calendar.MINUTE))
        assertEquals(due, taskReminderAt(due, dueHasTime = true))
    }

    @Test fun nextOccurrencePreservesTimeAndCopiesStartShift() {
        val due = timestamp(2026, 5, 10, 18, 30)
        val start = timestamp(2026, 5, 9, 9, 0)
        val task = Task(
            id = 1,
            title = "Semanal",
            dueAt = due,
            startAt = start,
            dueHasTime = true,
            reminderEnabled = true,
            recurrence = TaskRecurrence.WEEKLY,
        )
        val next = task.nextOccurrence()!!
        val nextCalendar = Calendar.getInstance().apply { timeInMillis = next.dueAt }
        assertEquals(17, nextCalendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(18, nextCalendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, nextCalendar.get(Calendar.MINUTE))
        assertEquals(start + 7L * 24 * 60 * 60 * 1000, next.startAt)
        assertEquals(taskReminderAt(next.dueAt, true), next.reminderAt)
    }

    @Test fun nextOccurrenceStopsAfterEndDateAndWithoutDueDate() {
        val due = timestamp(2026, 5, 10, 9, 0)
        val end = timestamp(2026, 5, 11, 23, 0)
        val task = Task(
            id = 1,
            title = "Diaria",
            dueAt = due,
            dueHasTime = true,
            recurrence = TaskRecurrence.DAILY,
            recurrenceEndAt = end,
        )
        assertNotNull(task.nextOccurrence())
        assertNull(task.copy(recurrenceEndAt = due).nextOccurrence())
        assertNull(task.copy(dueAt = null).nextOccurrence())
        assertNull(task.copy(recurrence = TaskRecurrence.NONE).nextOccurrence())
    }

    @Test fun filteringGroupsMatchesStatusDateAndSearch() {
        val now = timestamp(2026, 5, 10, 12, 0)
        val tasks = listOf(
            Task(1, "Vencida", dueAt = timestamp(2026, 5, 9, 9, 0), dueHasTime = true),
            Task(2, "Hoy", dueAt = timestamp(2026, 5, 10, 0, 0)),
            Task(3, "Próxima importante", dueAt = timestamp(2026, 5, 12, 9, 0), dueHasTime = true),
            Task(4, "Sin fecha", priority = TaskPriority.HIGH, labels = listOf("ideas")),
            Task(5, "Hecha", status = TaskStatus.COMPLETED),
        )
        val all = filterAndGroupTasks(tasks, TaskFilters(), now)
        assertEquals(
            listOf(TaskGroup.OVERDUE, TaskGroup.TODAY, TaskGroup.UPCOMING, TaskGroup.NO_DATE, TaskGroup.COMPLETED),
            all.map { it.group },
        )
        val active = filterAndGroupTasks(
            tasks,
            TaskFilters(status = TaskStatusFilter.ACTIVE),
            now,
        )
        assertEquals(4, active.sumOf { it.tasks.size })
        val search = filterAndGroupTasks(tasks, TaskFilters(query = "ideas"), now)
        assertEquals(listOf(4L), search.flatMap { it.tasks }.map { it.id })
        val overdue = filterAndGroupTasks(tasks, TaskFilters(date = TaskDateFilter.OVERDUE), now)
        assertEquals(listOf(1L), overdue.flatMap { it.tasks }.map { it.id })
        val noDate = filterAndGroupTasks(tasks, TaskFilters(date = TaskDateFilter.NO_DATE), now)
        assertEquals(listOf(4L, 5L), noDate.flatMap { it.tasks }.map { it.id })
    }

    @Test fun overdueDateOnlyTaskExpiresAtEndOfDay() {
        val midday = timestamp(2026, 5, 10, 12, 0)
        val today = Task(1, "Hoy", dueAt = timestamp(2026, 5, 10, 0, 0))
        val yesterday = Task(2, "Ayer", dueAt = timestamp(2026, 5, 9, 0, 0))
        assertFalse(isTaskOverdue(today, midday))
        assertTrue(isTaskOverdue(yesterday, midday))
        assertFalse(isTaskOverdue(today.copy(status = TaskStatus.COMPLETED), midday))
    }

    @Test fun statsSeparateActiveAndCompleted() {
        val stats = taskStats(
            listOf(
                Task(1, "Pendiente"),
                Task(2, "En curso", status = TaskStatus.IN_PROGRESS),
                Task(3, "Hecha", status = TaskStatus.COMPLETED),
            )
        )
        assertEquals(2, stats.pending)
        assertEquals(1, stats.completed)
        assertEquals(3, stats.total)
    }

    private fun timestamp(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis
}
