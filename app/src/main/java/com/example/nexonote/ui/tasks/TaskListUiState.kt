package com.example.nexonote.ui.tasks

import com.example.nexonote.domain.model.Task
import com.example.nexonote.domain.model.TaskFilters
import com.example.nexonote.domain.model.TaskSection
import com.example.nexonote.domain.model.filterAndGroupTasks

data class TaskListUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val filters: TaskFilters = TaskFilters(),
    val sections: List<TaskSection> = emptyList(),
    val totalTasks: Int = 0,
    val activeTasks: Int = 0,
    val completedTasks: Int = 0,
    val busy: Boolean = false,
    val operationError: String? = null,
    val now: Long = 0,
) {
    val matchCount: Int get() = sections.sumOf { it.tasks.size }
    val hasTasks: Boolean get() = totalTasks > 0
}

internal fun taskListState(
    tasks: List<Task>,
    filters: TaskFilters,
    now: Long,
    loading: Boolean = false,
    error: String? = null,
): TaskListUiState = TaskListUiState(
    loading = loading,
    error = error,
    filters = filters,
    sections = filterAndGroupTasks(tasks, filters, now),
    totalTasks = tasks.size,
    activeTasks = tasks.count { !it.isCompleted },
    completedTasks = tasks.count { it.isCompleted },
    now = now,
)
