package com.example.nexonote.domain.model

enum class EntryType { NOTE, TASK, TABLE }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class TaskPriority(val rank: Int, val label: String) {
    UNSPECIFIED(0, "Sin prioridad"),
    LOW(1, "Baja"),
    NORMAL(2, "Media"),
    HIGH(3, "Alta"),
    URGENT(4, "Urgente"),
}
enum class ActivityKind {
    NOTE_CREATED, TASK_CREATED, TABLE_CREATED, TASK_COMPLETED, TASK_REOPENED
}

data class Activity(
    val id: Long,
    val entryId: Long,
    val kind: ActivityKind,
    val occurredAt: Long,
    val title: String?,
)

data class Entry(
    val id: Long,
    val title: String,
    val type: EntryType,
    val completed: Boolean = false,
    val modifiedAt: Long? = null,
    val dueAt: Long? = null,
    val priority: TaskPriority = TaskPriority.UNSPECIFIED,
)
