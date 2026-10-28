package com.example.nexonote.ui.tables

import com.example.nexonote.domain.model.Category
import com.example.nexonote.domain.model.CustomTable
import com.example.nexonote.domain.model.Folder
import com.example.nexonote.domain.model.Tag

data class TablesUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val tables: List<CustomTable> = emptyList(),
    val query: String = "",
    val busy: Boolean = false,
    val operationError: String? = null,
    val folders: List<Folder> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
) {
    val visibleTables: List<CustomTable>
        get() {
            val normalized = query.trim()
            if (normalized.isEmpty()) return tables
            return tables.filter { it.name.contains(normalized, ignoreCase = true) }
        }

    val totalTableCount: Int get() = tables.size
}

internal fun tablesState(
    tables: List<CustomTable>,
    query: String,
    loading: Boolean = false,
    error: String? = null,
): TablesUiState = TablesUiState(
    loading = loading,
    error = error,
    tables = tables,
    query = query,
)
