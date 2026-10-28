package com.example.nexonote.ui.tables

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nexonote.di.AppContainer
import com.example.nexonote.domain.model.ColumnInput
import com.example.nexonote.domain.model.ColumnOption
import com.example.nexonote.domain.model.ColumnType
import com.example.nexonote.domain.model.CustomColumn
import com.example.nexonote.domain.model.CustomTable
import com.example.nexonote.domain.model.OptionInput
import com.example.nexonote.domain.model.TableFilter
import com.example.nexonote.domain.model.TableInput
import com.example.nexonote.domain.model.TableQuery
import com.example.nexonote.domain.model.TableSort
import com.example.nexonote.domain.repository.TableRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

private data class MetaState(
    val loading: Boolean = true,
    val error: String? = null,
    val table: CustomTable? = null,
    val columns: List<CustomColumn> = emptyList(),
)

private data class RowsState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val rows: List<com.example.nexonote.domain.model.TableRow> = emptyList(),
    val hasMore: Boolean = false,
)

private data class DetailOperation(
    val busy: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TableDetailViewModel(
    private val tableId: Long,
    private val repository: TableRepository,
) : ViewModel() {
    private val queryState = MutableStateFlow(TableQuery())
    private val displayMode = MutableStateFlow(TableDisplayMode.GRID)
    private val rowsState = MutableStateFlow(RowsState())
    private val operation = MutableStateFlow(DetailOperation())
    private val retry = MutableStateFlow(0)
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    private val metaState = retry.flatMapLatest {
        combine(
            repository.observeTable(tableId),
            repository.observeColumns(tableId),
            repository.observeOptions(tableId),
        ) { table, columns, options ->
            val grouped = options.groupBy { it.columnId }
            MetaState(
                loading = false,
                table = table?.copy(columnCount = columns.size),
                columns = columns.map { it.copy(options = grouped[it.id].orEmpty()) },
            )
        }.onStart {
            emit(MetaState())
        }.catch {
            emit(
                MetaState(
                    loading = false,
                    error = "No pudimos cargar la tabla. Inténtalo de nuevo.",
                )
            )
        }
    }

    val uiState: StateFlow<TableDetailUiState> = combine(
        metaState,
        queryState,
        displayMode,
        rowsState,
        operation,
    ) { meta, query, mode, rows, status ->
        TableDetailUiState(
            loading = meta.loading || (rows.loading && meta.table != null),
            missing = !meta.loading && meta.error == null && meta.table == null,
            error = meta.error ?: rows.error,
            table = meta.table,
            columns = meta.columns,
            rows = rows.rows,
            search = query.search,
            filters = query.filters,
            sort = query.sort,
            displayMode = mode,
            hasMore = rows.hasMore,
            loadingMore = rows.loadingMore,
            busy = status.busy,
            operationError = status.error,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TableDetailUiState(),
    )

    init {
        refreshRows(reset = true)
    }

    fun setSearch(value: String) {
        queryState.update { it.copy(search = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250)
            refreshRows(reset = true)
        }
    }

    fun addFilter(filter: TableFilter) {
        queryState.update { it.copy(filters = it.filters + filter) }
        refreshRows(reset = true)
    }

    fun removeFilterAt(index: Int) {
        queryState.update { current ->
            current.copy(filters = current.filters.filterIndexed { i, _ -> i != index })
        }
        refreshRows(reset = true)
    }

    fun setSort(sort: TableSort?) {
        queryState.update { it.copy(sort = sort) }
        refreshRows(reset = true)
    }

    fun resetQuery() {
        queryState.value = TableQuery()
        refreshRows(reset = true)
    }

    fun setDisplayMode(mode: TableDisplayMode) {
        displayMode.value = mode
    }

    fun retry() {
        retry.update { it + 1 }
        rowsState.value = RowsState()
        refreshRows(reset = true)
    }

    fun dismissError() {
        operation.update { it.copy(error = null) }
        rowsState.update { it.copy(error = null) }
    }

    fun loadMore() {
        refreshRows(reset = false)
    }

    fun updateTable(input: TableInput, onDone: () -> Unit = {}) = perform {
        repository.updateTable(tableId, input)
        onDone()
    }

    fun duplicateTable(onCreated: (Long) -> Unit) = perform {
        onCreated(repository.duplicateTable(tableId))
    }

    fun deleteTable(onDeleted: () -> Unit) = perform {
        repository.deleteTable(tableId)
        onDeleted()
    }

    fun createRow(onCreated: (Long) -> Unit = {}) = perform {
        val id = repository.createRow(tableId)
        refreshRows(reset = true)
        onCreated(id)
    }

    fun duplicateRow(rowId: Long, onCreated: (Long) -> Unit = {}) = perform {
        val id = repository.duplicateRow(rowId)
        refreshRows(reset = true)
        onCreated(id)
    }

    fun deleteRow(rowId: Long) = perform {
        repository.deleteRow(rowId)
        refreshRows(reset = true)
    }

    fun updateCell(rowId: Long, columnId: Long, value: String?) = perform {
        repository.updateCell(rowId, columnId, value)
        refreshRows(reset = true)
    }

    fun addColumn(input: ColumnInput, onCreated: (Long) -> Unit = {}) = perform {
        val id = repository.addColumn(tableId, input)
        refreshRows(reset = true)
        onCreated(id)
    }

    fun updateColumn(columnId: Long, input: ColumnInput, onDone: () -> Unit = {}) = perform {
        repository.updateColumn(columnId, input)
        refreshRows(reset = true)
        onDone()
    }

    fun changeColumnType(columnId: Long, type: ColumnType, onDone: () -> Unit = {}) = perform {
        repository.changeColumnType(columnId, type)
        refreshRows(reset = true)
        onDone()
    }

    fun moveColumn(columnId: Long, offset: Int) = perform {
        repository.moveColumn(columnId, offset)
    }

    fun deleteColumn(columnId: Long, onDone: () -> Unit = {}) = perform {
        repository.deleteColumn(columnId)
        refreshRows(reset = true)
        onDone()
    }

    fun addOption(columnId: Long, input: OptionInput, onCreated: (Long) -> Unit = {}) = perform {
        onCreated(repository.addOption(columnId, input))
    }

    fun updateOption(optionId: Long, input: OptionInput, onDone: () -> Unit = {}) = perform {
        repository.updateOption(optionId, input)
        onDone()
    }

    fun deleteOption(optionId: Long) = perform {
        repository.deleteOption(optionId)
        refreshRows(reset = true)
    }

    fun optionFor(column: CustomColumn, id: Long): ColumnOption? =
        column.options.firstOrNull { it.id == id }

    private fun refreshRows(reset: Boolean) {
        if (!reset) {
            val current = rowsState.value
            if (current.loadingMore || !current.hasMore) return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (reset) {
                rowsState.update {
                    it.copy(loading = true, loadingMore = false, error = null)
                }
            } else {
                rowsState.update { it.copy(loadingMore = true, error = null) }
            }
            val offset = if (reset) 0 else rowsState.value.rows.size
            try {
                val page = repository.loadPage(
                    tableId = tableId,
                    query = queryState.value,
                    limit = PAGE_SIZE,
                    offset = offset,
                )
                rowsState.update { current ->
                    if (reset) {
                        RowsState(
                            loading = false,
                            rows = page.rows,
                            hasMore = page.hasMore,
                        )
                    } else {
                        current.copy(
                            loading = false,
                            loadingMore = false,
                            rows = current.rows + page.rows,
                            hasMore = page.hasMore,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                rowsState.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        error = "No pudimos cargar los registros de la tabla.",
                    )
                }
            }
        }
    }

    private fun perform(action: suspend () -> Unit) {
        if (operation.value.busy) return
        viewModelScope.launch {
            operation.value = DetailOperation(busy = true)
            try {
                action()
                operation.value = DetailOperation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                operation.value = DetailOperation(
                    error = error.message?.takeIf { it.isNotBlank() }
                        ?: "No se pudo completar la acción. Inténtalo de nuevo."
                )
            }
        }
    }

    companion object {
        private const val PAGE_SIZE = 50

        fun factory(tableId: Long, container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    TableDetailViewModel(tableId, container.tableRepository)
                }
            }
    }
}
