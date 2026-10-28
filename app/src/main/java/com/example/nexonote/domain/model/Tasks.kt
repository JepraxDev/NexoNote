package com.example.nexonote.domain.model

import java.util.Calendar

enum class TaskStatus { PENDING, IN_PROGRESS, COMPLETED }

enum class TaskRecurrence { NONE, DAILY, WEEKLY, MONTHLY }

enum class TaskStatusFilter { ALL, ACTIVE, PENDING, IN_PROGRESS, COMPLETED }

enum class TaskDateFilter { ALL, TODAY, WEEK, OVERDUE, NO_DATE }

enum class TaskSort { DUE_DATE, PRIORITY, MODIFIED, TITLE }

enum class TaskGroup(val label: String) {
    OVERDUE("Vencidas"),
    TODAY("Hoy"),
    UPCOMING("Próximas"),
    NO_DATE("Sin fecha"),
    COMPLETED("Completadas"),
}

data class Subtask(
    val id: Long = 0,
    val text: String,
    val completed: Boolean = false,
)

data class SubtaskDraft(
    val text: String,
    val completed: Boolean = false,
)

data class Task(
    val id: Long,
    val title: String,
    val description: String = "",
    val status: TaskStatus = TaskStatus.PENDING,
    val priority: TaskPriority = TaskPriority.UNSPECIFIED,
    val startAt: Long? = null,
    val dueAt: Long? = null,
    val dueHasTime: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderAt: Long? = null,
    val recurrence: TaskRecurrence = TaskRecurrence.NONE,
    val recurrenceEndAt: Long? = null,
    val seriesId: Long? = null,
    val occurrenceAt: Long? = null,
    val createdAt: Long? = null,
    val modifiedAt: Long? = null,
    val labels: List<String> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),
    val favorite: Boolean = false,
    val folderId: Long? = null,
    val categoryId: Long? = null,
) {
    val displayTitle: String get() = title.ifBlank { "Tarea sin título" }
    val isCompleted: Boolean get() = status == TaskStatus.COMPLETED
    val completedSubtasks: Int get() = subtasks.count { it.completed && it.text.isNotBlank() }
    val totalSubtasks: Int get() = subtasks.count { it.text.isNotBlank() }
    val subtaskProgress: Float
        get() = if (totalSubtasks == 0) 0f else completedSubtasks.toFloat() / totalSubtasks
}

data class TaskInput(
    val title: String,
    val description: String = "",
    val status: TaskStatus = TaskStatus.PENDING,
    val priority: TaskPriority = TaskPriority.UNSPECIFIED,
    val startAt: Long? = null,
    val dueAt: Long? = null,
    val dueHasTime: Boolean = false,
    val reminderEnabled: Boolean = false,
    val recurrence: TaskRecurrence = TaskRecurrence.NONE,
    val recurrenceEndAt: Long? = null,
    val labels: List<String> = emptyList(),
    val subtasks: List<SubtaskDraft> = emptyList(),
    val favorite: Boolean = false,
    val folderId: Long? = null,
    val categoryId: Long? = null,
)

data class TaskFilters(
    val query: String = "",
    val status: TaskStatusFilter = TaskStatusFilter.ALL,
    val date: TaskDateFilter = TaskDateFilter.ALL,
    val sort: TaskSort = TaskSort.DUE_DATE,
)

data class TaskSection(
    val group: TaskGroup,
    val tasks: List<Task>,
)

data class TaskStats(
    val pending: Int,
    val completed: Int,
) {
    val total: Int get() = pending + completed
}

data class RecurringOccurrence(
    val dueAt: Long,
    val startAt: Long?,
    val reminderAt: Long?,
)

fun validateTaskInput(input: TaskInput): String? {
    if (input.title.isBlank()) return "El título es obligatorio."
    if (input.title.trim().length > 120) return "El título no puede superar 120 caracteres."
    if (input.description.length > 5_000) return "La descripción es demasiado larga."
    if (input.startAt != null && input.dueAt != null && input.startAt > input.dueAt) {
        return "La fecha de inicio no puede ser posterior al vencimiento."
    }
    if (input.reminderEnabled && input.dueAt == null) {
        return "Añade una fecha de vencimiento para activar el recordatorio."
    }
    if (input.recurrence != TaskRecurrence.NONE && input.dueAt == null) {
        return "Añade una fecha de vencimiento para repetir la tarea."
    }
    if (input.recurrence != TaskRecurrence.NONE &&
        input.recurrenceEndAt != null &&
        input.dueAt != null &&
        startOfDay(input.recurrenceEndAt) < startOfDay(input.dueAt)
    ) {
        return "La fecha final de la repetición debe ser posterior al vencimiento."
    }
    if (input.labels.size > 20) return "Hay demasiadas etiquetas."
    if (input.subtasks.size > 100) return "Hay demasiadas subtareas."
    if (input.subtasks.any { it.text.trim().length > 200 }) {
        return "Cada subtarea admite como máximo 200 caracteres."
    }
    return null
}

fun taskReminderAt(dueAt: Long?, dueHasTime: Boolean): Long? {
    if (dueAt == null) return null
    if (dueHasTime) return dueAt
    return Calendar.getInstance().apply {
        timeInMillis = dueAt
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

fun Task.nextOccurrence(): RecurringOccurrence? {
    val currentDue = dueAt ?: return null
    if (recurrence == TaskRecurrence.NONE) return null
    val next = Calendar.getInstance().apply { timeInMillis = currentDue }
    when (recurrence) {
        TaskRecurrence.DAILY -> next.add(Calendar.DAY_OF_YEAR, 1)
        TaskRecurrence.WEEKLY -> next.add(Calendar.DAY_OF_YEAR, 7)
        TaskRecurrence.MONTHLY -> next.add(Calendar.MONTH, 1)
        TaskRecurrence.NONE -> return null
    }
    val nextDue = next.timeInMillis
    if (recurrenceEndAt != null && startOfDay(nextDue) > startOfDay(recurrenceEndAt)) return null
    val shift = nextDue - currentDue
    return RecurringOccurrence(
        dueAt = nextDue,
        startAt = startAt?.plus(shift),
        reminderAt = if (reminderEnabled) taskReminderAt(nextDue, dueHasTime) else null,
    )
}

fun isTaskOverdue(task: Task, now: Long): Boolean {
    if (task.isCompleted) return false
    val due = task.dueAt ?: return false
    return if (task.dueHasTime) due < now else endOfDay(due) < now
}

fun taskGroup(task: Task, now: Long): TaskGroup {
    if (task.isCompleted) return TaskGroup.COMPLETED
    val due = task.dueAt ?: return TaskGroup.NO_DATE
    if (isTaskOverdue(task, now)) return TaskGroup.OVERDUE
    return if (startOfDay(due) == startOfDay(now)) TaskGroup.TODAY else TaskGroup.UPCOMING
}

fun filterAndGroupTasks(tasks: List<Task>, filters: TaskFilters, now: Long): List<TaskSection> {
    val query = filters.query.trim()
    val filtered = tasks.asSequence()
        .filter { task ->
            query.isEmpty() ||
                task.title.contains(query, ignoreCase = true) ||
                task.description.contains(query, ignoreCase = true) ||
                task.labels.any { it.contains(query, ignoreCase = true) } ||
                task.subtasks.any { it.text.contains(query, ignoreCase = true) }
        }
        .filter { task ->
            when (filters.status) {
                TaskStatusFilter.ALL -> true
                TaskStatusFilter.ACTIVE -> !task.isCompleted
                TaskStatusFilter.PENDING -> task.status == TaskStatus.PENDING
                TaskStatusFilter.IN_PROGRESS -> task.status == TaskStatus.IN_PROGRESS
                TaskStatusFilter.COMPLETED -> task.isCompleted
            }
        }
        .filter { task ->
            val due = task.dueAt
            when (filters.date) {
                TaskDateFilter.ALL -> true
                TaskDateFilter.TODAY -> due != null && startOfDay(due) == startOfDay(now)
                TaskDateFilter.WEEK -> due != null &&
                    endOfDay(due) >= startOfDay(now) &&
                    startOfDay(due) <= endOfDay(addDays(now, 7))
                TaskDateFilter.OVERDUE -> isTaskOverdue(task, now)
                TaskDateFilter.NO_DATE -> due == null
            }
        }
        .sortedWith(taskComparator(filters.sort))
        .toList()

    return TaskGroup.entries.mapNotNull { group ->
        val grouped = filtered.filter { taskGroup(it, now) == group }
        if (grouped.isEmpty()) null else TaskSection(group, grouped)
    }
}

fun taskStats(tasks: List<Task>): TaskStats = TaskStats(
    pending = tasks.count { !it.isCompleted },
    completed = tasks.count { it.isCompleted },
)

private fun taskComparator(sort: TaskSort): Comparator<Task> = when (sort) {
    TaskSort.DUE_DATE -> compareBy<Task> { it.dueAt == null }
        .thenBy { it.dueAt }
        .thenByDescending { it.priority.rank }
        .thenByDescending { it.id }
    TaskSort.PRIORITY -> compareByDescending<Task> { it.priority.rank }
        .thenBy { it.dueAt == null }
        .thenBy { it.dueAt }
        .thenByDescending { it.id }
    TaskSort.MODIFIED -> compareByDescending<Task> { it.modifiedAt }.thenByDescending { it.id }
    TaskSort.TITLE -> compareBy<Task> { it.title.lowercase() }.thenByDescending { it.id }
}

fun startOfDay(timestamp: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestamp
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun endOfDay(timestamp: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestamp
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 59)
    set(Calendar.MILLISECOND, 999)
}.timeInMillis

fun addDays(timestamp: Long, days: Int): Long = Calendar.getInstance().apply {
    timeInMillis = timestamp
    add(Calendar.DAY_OF_YEAR, days)
}.timeInMillis
