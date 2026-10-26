package com.example.nexonote.ui.tables

import com.example.nexonote.domain.model.ColumnOption
import com.example.nexonote.domain.model.CustomColumn
import com.example.nexonote.domain.model.CustomTable
import com.example.nexonote.domain.model.TableFilter
import com.example.nexonote.domain.model.TableRow
import com.example.nexonote.domain.model.TableSort

enum class TableDisplayMode {
    GRID,
    CARDS,
}

data class TableDetailUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val error: String? = null,
    val table: CustomTable? = null,
    val columns: List<CustomColumn> = emptyList(),
    val rows: List<TableRow> = emptyList(),
    val search: String = "",
    val filters: List<TableFilter> = emptyList(),
    val sort: TableSort? = null,
    val displayMode: TableDisplayMode = TableDisplayMode.GRID,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val busy: Boolean = false,
    val operationError: String? = null,
) {
    val primaryColumn: CustomColumn?
        get() = columns.firstOrNull { it.primary } ?: columns.firstOrNull()

    val hasActiveQuery: Boolean
        get() = search.isNotBlank() || filters.isNotEmpty() || sort != null

    fun option(columnId: Long, optionId: Long): ColumnOption? =
        columns.firstOrNull { it.id == columnId }?.options?.firstOrNull { it.id == optionId }
}
