package com.example.nexonote.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nexonote.di.AppContainer
import com.example.nexonote.domain.model.Task
import com.example.nexonote.domain.model.TaskDateFilter
import com.example.nexonote.domain.model.TaskFilters
import com.example.nexonote.domain.model.TaskSort
import com.example.nexonote.domain.model.TaskStatusFilter
import com.example.nexonote.domain.repository.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class TaskOperation(
    val busy: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TaskListViewModel(
    private val repository: TaskRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val filters = MutableStateFlow(TaskFilters())
    private val retry = MutableStateFlow(0)
    private val operation = MutableStateFlow(TaskOperation())
    private val time = flow {
        while (true) {
            emit(clock())
            delay(30_000)
        }
    }

    private val content = retry.flatMapLatest {
        combine(repository.tasks, filters, time) { tasks, selected, now ->
            taskListState(tasks, selected, now)
        }.onStart {
            emit(TaskListUiState(filters = filters.value, now = clock()))
        }.catch {
            emit(
                TaskListUiState(
                    loading = false,
                    error = "No pudimos cargar tus tareas. Comprueba el almacenamiento e inténtalo de nuevo.",
                    filters = filters.value,
                    now = clock(),
                )
            )
        }
    }

    val uiState: StateFlow<TaskListUiState> = combine(content, operation) { data, status ->
        data.copy(busy = status.busy, operationError = status.error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListUiState())

    fun setQuery(value: String) {
        filters.update { it.copy(query = value) }
    }

    fun setStatusFilter(value: TaskStatusFilter) {
        filters.update { it.copy(status = value) }
    }

    fun setDateFilter(value: TaskDateFilter) {
        filters.update { it.copy(date = value) }
    }

    fun setSort(value: TaskSort) {
        filters.update { it.copy(sort = value) }
    }

    fun clearFilters() {
        filters.value = TaskFilters()
    }

    fun retry() {
        retry.update { it + 1 }
    }

    fun dismissError() {
        operation.update { it.copy(error = null) }
    }

    fun toggleCompleted(task: Task) = perform {
        repository.setCompleted(task.id, !task.isCompleted)
    }

    fun delete(task: Task) = perform {
        repository.delete(task.id)
    }

    fun stopRecurrence(task: Task) = perform {
        repository.stopRecurrence(task.id)
    }

    private fun perform(action: suspend () -> Unit) {
        if (operation.value.busy) return
        viewModelScope.launch {
            operation.value = TaskOperation(busy = true)
            try {
                action()
                operation.value = TaskOperation()
            } catch (error: CancellationException) {
                operation.value = TaskOperation()
                throw error
            } catch (_: Exception) {
                operation.value = TaskOperation(
                    error = "No se pudo completar la acción. Inténtalo de nuevo."
                )
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TaskListViewModel(container.taskRepository)
            }
        }
    }
}
