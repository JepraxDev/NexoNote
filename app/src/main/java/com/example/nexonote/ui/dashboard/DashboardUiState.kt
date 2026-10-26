package com.example.nexonote.ui.dashboard

import com.example.nexonote.domain.model.Activity
import com.example.nexonote.domain.model.CustomTable
import com.example.nexonote.domain.model.Entry
import com.example.nexonote.domain.model.EntryType
import com.example.nexonote.domain.model.Note
import com.example.nexonote.domain.model.RecentTableRow
import com.example.nexonote.domain.model.Task
import kotlin.math.roundToInt

data class DashboardUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val displayName: String = "",
    val now: Long = 0,
    val noteCount: Int = 0,
    val pendingCount: Int = 0,
    val completedCount: Int = 0,
    val tableCount: Int = 0,
    val recentNotes: List<Note> = emptyList(),
    val upcomingTasks: List<Task> = emptyList(),
    val recentTableRows: List<RecentTableRow> = emptyList(),
    val recentActivity: List<Activity> = emptyList(),
    val savingName: Boolean = false,
    val nameError: String? = null,
) {
    val taskCount: Int get() = pendingCount + completedCount
    val progress: Float get() = if (taskCount == 0) 0f else completedCount.toFloat() / taskCount
    val percentage: Int get() = (progress * 100).roundToInt()
}

internal fun dashboardState(
    entries: List<Entry>,
    notes: List<Note>,
    tasks: List<Task>,
    activities: List<Activity>,
    name: String,
    now: Long,
    tables: List<CustomTable> = emptyList(),
    recentTableRows: List<RecentTableRow> = emptyList(),
): DashboardUiState {
    val activeNotes = notes.filter { it.isActive }
    return DashboardUiState(
        loading = false,
        displayName = name,
        now = now,
        noteCount = activeNotes.size,
        pendingCount = tasks.count { !it.isCompleted },
        completedCount = tasks.count { it.isCompleted },
        tableCount = if (tables.isEmpty()) entries.count { it.type == EntryType.TABLE } else tables.size,
        recentNotes = activeNotes.sortedWith(
            compareByDescending<Note> { it.modifiedAt }.thenByDescending { it.id }
        ).take(5),
        upcomingTasks = tasks.filterNot { it.isCompleted }.sortedWith(
            compareBy<Task> { it.dueAt == null }
                .thenBy { it.dueAt }
                .thenByDescending { it.priority.rank }
                .thenByDescending { it.id }
        ).take(5),
        recentTableRows = recentTableRows.take(5),
        recentActivity = activities.sortedWith(
            compareByDescending<Activity> { it.occurredAt }.thenByDescending { it.id }
        ).take(5),
    )
}
