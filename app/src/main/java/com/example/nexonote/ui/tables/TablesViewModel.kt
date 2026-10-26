package com.example.nexonote.ui.tables

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nexonote.di.AppContainer
import com.example.nexonote.domain.model.TableInput
import com.example.nexonote.domain.repository.TableRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class TablesOperation(
    val busy: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TablesViewModel(
    private val repository: TableRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val retry = MutableStateFlow(0)
    private val operation = MutableStateFlow(TablesOperation())

    private val content = retry.flatMapLatest {
        combine(repository.tables, query) { tables, value ->
            tablesState(tables = tables, query = value)
        }.onStart {
            emit(TablesUiState(query = query.value))
        }.catch {
            emit(
                TablesUiState(
                    loading = false,
                    error = "No pudimos cargar tus tablas. Comprueba el almacenamiento e inténtalo de nuevo.",
                    query = query.value,
                )
            )
        }
    }

    val uiState: StateFlow<TablesUiState> = combine(content, operation) { data, status ->
        data.copy(busy = status.busy, operationError = status.error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TablesUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun retry() {
        retry.update { it + 1 }
    }

    fun dismissError() {
        operation.update { it.copy(error = null) }
    }

    fun createTable(input: TableInput, onCreated: (Long) -> Unit = {}) = perform {
        val id = repository.createTable(input)
        onCreated(id)
    }

    fun updateTable(id: Long, input: TableInput, onDone: () -> Unit = {}) = perform {
        repository.updateTable(id, input)
        onDone()
    }

    fun duplicateTable(id: Long, onCreated: (Long) -> Unit = {}) = perform {
        val newId = repository.duplicateTable(id)
        onCreated(newId)
    }

    fun deleteTable(id: Long, onDeleted: () -> Unit = {}) = perform {
        repository.deleteTable(id)
        onDeleted()
    }

    private fun perform(action: suspend () -> Unit) {
        viewModelScope.launch {
            operation.value = TablesOperation(busy = true)
            try {
                action()
                operation.value = TablesOperation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                operation.value = TablesOperation(
                    error = error.message?.takeIf { it.isNotBlank() }
                        ?: "No se pudo completar la acción. Inténtalo de nuevo."
                )
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TablesViewModel(container.tableRepository)
            }
        }
    }
}
