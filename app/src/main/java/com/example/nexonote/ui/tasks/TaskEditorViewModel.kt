package com.example.nexonote.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nexonote.di.AppContainer
import com.example.nexonote.domain.model.SubtaskDraft
import com.example.nexonote.domain.model.TaskInput
import com.example.nexonote.domain.model.TaskPriority
import com.example.nexonote.domain.model.TaskRecurrence
import com.example.nexonote.domain.model.TaskStatus
import com.example.nexonote.domain.model.startOfDay
import com.example.nexonote.domain.model.taskReminderAt
import com.example.nexonote.domain.model.validateTaskInput
import com.example.nexonote.domain.repository.TaskRepository
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TaskSubtaskRow(
    val key: Long,
    val text: String,
    val completed: Boolean = false,
)

data class TaskEditorUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val taskId: Long? = null,
    val title: String = "",
    val description: String = "",
    val status: TaskStatus = TaskStatus.PENDING,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val startAt: Long? = null,
    val dueAt: Long? = null,
    val dueHasTime: Boolean = false,
    val reminderEnabled: Boolean = false,
    val recurrence: TaskRecurrence = TaskRecurrence.NONE,
    val recurrenceEndAt: Long? = null,
    val labels: List<String> = emptyList(),
    val availableLabels: List<String> = emptyList(),
    val subtasks: List<TaskSubtaskRow> = emptyList(),
    val saving: Boolean = false,
    val savedAt: Long? = null,
    val error: String? = null,
    val dirty: Boolean = false,
)

class TaskEditorViewModel(
    private val repository: TaskRepository,
    private val requestedId: Long?,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        TaskEditorUiState(loading = requestedId != null, taskId = requestedId)
    )
    val uiState: StateFlow<TaskEditorUiState> = _uiState.asStateFlow()
    private var nextSubtaskKey = 0L

    init {
        viewModelScope.launch {
            repository.labels.collect { labels ->
                _uiState.update { it.copy(availableLabels = labels) }
            }
        }
        if (requestedId == null) {
            _uiState.update { it.copy(loading = false) }
        } else {
            viewModelScope.launch {
                val task = repository.observeTask(requestedId).first()
                if (task == null) {
                    _uiState.update { it.copy(loading = false, missing = true) }
                } else {
                    _uiState.value = TaskEditorUiState(
                        loading = false,
                        taskId = task.id,
                        title = task.title,
                        description = task.description,
                        status = task.status,
                        priority = task.priority,
                        startAt = task.startAt,
                        dueAt = task.dueAt,
                        dueHasTime = task.dueHasTime,
                        reminderEnabled = task.reminderEnabled,
                        recurrence = task.recurrence,
                        recurrenceEndAt = task.recurrenceEndAt,
                        labels = task.labels,
                        availableLabels = _uiState.value.availableLabels,
                        subtasks = task.subtasks.map {
                            TaskSubtaskRow(++nextSubtaskKey, it.text, it.completed)
                        },
                        savedAt = task.modifiedAt,
                    )
                }
            }
        }
    }

    fun setTitle(value: String) {
        if (value.length > 120) return
        _uiState.update { it.copy(title = value, dirty = true) }
    }

    fun setDescription(value: String) {
        if (value.length > 5_000) return
        _uiState.update { it.copy(description = value, dirty = true) }
    }

    fun setStatus(value: TaskStatus) {
        _uiState.update { state ->
            state.copy(
                status = value,
                subtasks = if (value == TaskStatus.COMPLETED) {
                    state.subtasks.map { it.copy(completed = true) }
                } else {
                    state.subtasks
                },
                dirty = true,
            )
        }
    }

    fun setPriority(value: TaskPriority) {
        _uiState.update { it.copy(priority = value, dirty = true) }
    }

    fun setStartDate(value: Long?) {
        _uiState.update { it.copy(startAt = value, dirty = true) }
    }

    fun setDueDate(value: Long?) {
        _uiState.update { state ->
            if (value == null) {
                state.copy(
                    dueAt = null,
                    dueHasTime = false,
                    reminderEnabled = false,
                    recurrence = TaskRecurrence.NONE,
                    recurrenceEndAt = null,
                    dirty = true,
                )
            } else {
                val currentDue = state.dueAt
                val adjusted = if (state.dueHasTime && currentDue != null) {
                    val current = Calendar.getInstance().apply { timeInMillis = currentDue }
                    Calendar.getInstance().apply {
                        timeInMillis = value
                        set(Calendar.HOUR_OF_DAY, current.get(Calendar.HOUR_OF_DAY))
                        set(Calendar.MINUTE, current.get(Calendar.MINUTE))
                    }.timeInMillis
                } else {
                    startOfDay(value)
                }
                state.copy(
                    dueAt = adjusted,
                    recurrenceEndAt = state.recurrenceEndAt?.takeIf {
                        startOfDay(it) >= startOfDay(adjusted)
                    },
                    dirty = true,
                )
            }
        }
    }

    fun setDueHasTime(enabled: Boolean) {
        _uiState.update { state ->
            val due = state.dueAt ?: return@update state
            val adjusted = if (enabled) {
                taskReminderAt(startOfDay(due), dueHasTime = false) ?: due
            } else {
                startOfDay(due)
            }
            state.copy(dueAt = adjusted, dueHasTime = enabled, dirty = true)
        }
    }

    fun setDueTime(hour: Int, minute: Int) {
        _uiState.update { state ->
            val due = state.dueAt ?: return@update state
            state.copy(
                dueAt = Calendar.getInstance().apply {
                    timeInMillis = due
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis,
                dueHasTime = true,
                dirty = true,
            )
        }
    }

    fun setReminderEnabled(enabled: Boolean) {
        _uiState.update {
            if (enabled && it.dueAt == null) it
            else it.copy(reminderEnabled = enabled, dirty = true)
        }
    }

    fun setRecurrence(value: TaskRecurrence) {
        _uiState.update {
            if (value != TaskRecurrence.NONE && it.dueAt == null) it
            else it.copy(
                recurrence = value,
                recurrenceEndAt = if (value == TaskRecurrence.NONE) null else it.recurrenceEndAt,
                dirty = true,
            )
        }
    }

    fun setRecurrenceEnd(value: Long?) {
        _uiState.update { it.copy(recurrenceEndAt = value, dirty = true) }
    }

    fun addLabel(value: String) {
        val label = value.trim().lowercase(Locale.ROOT).take(30)
        if (label.isEmpty()) return
        _uiState.update {
            if (it.labels.size >= 20 || label in it.labels) it
            else it.copy(labels = (it.labels + label).sorted(), dirty = true)
        }
    }

    fun removeLabel(label: String) {
        _uiState.update { it.copy(labels = it.labels - label, dirty = true) }
    }

    fun addSubtask() {
        _uiState.update {
            it.copy(
                subtasks = it.subtasks + TaskSubtaskRow(++nextSubtaskKey, ""),
                status = if (it.status == TaskStatus.COMPLETED) TaskStatus.IN_PROGRESS else it.status,
                dirty = true,
            )
        }
    }

    fun setSubtaskText(key: Long, value: String) {
        if (value.length > 200) return
        _uiState.update { state ->
            state.copy(
                subtasks = state.subtasks.map {
                    if (it.key == key) it.copy(text = value) else it
                },
                dirty = true,
            )
        }
    }

    fun setSubtaskCompleted(key: Long, completed: Boolean) {
        _uiState.update { state ->
            state.copy(
                subtasks = state.subtasks.map {
                    if (it.key == key) it.copy(completed = completed) else it
                },
                status = if (!completed && state.status == TaskStatus.COMPLETED) {
                    TaskStatus.IN_PROGRESS
                } else {
                    state.status
                },
                dirty = true,
            )
        }
    }

    fun removeSubtask(key: Long) {
        _uiState.update {
            it.copy(subtasks = it.subtasks.filterNot { row -> row.key == key }, dirty = true)
        }
    }

    fun moveSubtask(key: Long, direction: Int) {
        _uiState.update { state ->
            val index = state.subtasks.indexOfFirst { it.key == key }
            val target = index + direction
            if (index < 0 || target !in state.subtasks.indices) return@update state
            val reordered = state.subtasks.toMutableList()
            val item = reordered.removeAt(index)
            reordered.add(target, item)
            state.copy(subtasks = reordered, dirty = true)
        }
    }

    fun save(onResult: (Boolean) -> Unit) {
        if (_uiState.value.saving) return
        if (_uiState.value.missing) {
            onResult(true)
            return
        }
        val input = _uiState.value.toInput()
        val validationError = validateTaskInput(input)
        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            onResult(false)
            return
        }
        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val id = repository.save(_uiState.value.taskId?.takeIf { it > 0 }, input)
                _uiState.update {
                    it.copy(
                        taskId = id,
                        saving = false,
                        savedAt = clock(),
                        dirty = false,
                        error = null,
                    )
                }
                onResult(true)
            } catch (error: CancellationException) {
                _uiState.update { it.copy(saving = false) }
                throw error
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        saving = false,
                        error = "No se pudo guardar la tarea. Revisa los datos e inténtalo de nuevo.",
                    )
                }
                onResult(false)
            }
        }
    }

    fun saveAndExit(after: () -> Unit) {
        save { saved -> if (saved) after() }
    }

    fun delete(after: () -> Unit) {
        val id = _uiState.value.taskId
        if (id == null) {
            after()
            return
        }
        if (_uiState.value.saving) return
        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                repository.delete(id)
                after()
            } catch (error: CancellationException) {
                _uiState.update { it.copy(saving = false) }
                throw error
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        saving = false,
                        error = "No se pudo eliminar la tarea. Inténtalo de nuevo.",
                    )
                }
            }
        }
    }

    fun stopRecurrence() {
        val id = _uiState.value.taskId ?: return
        if (_uiState.value.recurrence == TaskRecurrence.NONE || _uiState.value.saving) return
        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                repository.stopRecurrence(id)
                _uiState.update {
                    it.copy(
                        saving = false,
                        recurrence = TaskRecurrence.NONE,
                        recurrenceEndAt = null,
                    )
                }
            } catch (error: CancellationException) {
                _uiState.update { it.copy(saving = false) }
                throw error
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        saving = false,
                        error = "No se pudo finalizar la repetición. Inténtalo de nuevo.",
                    )
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        fun factory(container: AppContainer, taskId: Long?): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    TaskEditorViewModel(container.taskRepository, taskId)
                }
            }
    }
}

private fun TaskEditorUiState.toInput() = TaskInput(
    title = title,
    description = description,
    status = status,
    priority = priority,
    startAt = startAt,
    dueAt = dueAt,
    dueHasTime = dueHasTime,
    reminderEnabled = reminderEnabled,
    recurrence = recurrence,
    recurrenceEndAt = recurrenceEndAt,
    labels = labels,
    subtasks = subtasks.map { SubtaskDraft(it.text, it.completed) },
)
