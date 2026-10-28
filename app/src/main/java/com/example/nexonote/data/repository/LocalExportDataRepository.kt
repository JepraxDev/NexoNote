package com.example.nexonote.data.repository

import com.example.nexonote.domain.model.Category
import com.example.nexonote.domain.model.CustomColumn
import com.example.nexonote.domain.model.CustomTable
import com.example.nexonote.domain.model.Folder
import com.example.nexonote.domain.model.Note
import com.example.nexonote.domain.model.TableQuery
import com.example.nexonote.domain.model.TableRow
import com.example.nexonote.domain.model.Task
import com.example.nexonote.domain.repository.ExportDataRepository
import com.example.nexonote.domain.repository.NoteRepository
import com.example.nexonote.domain.repository.OrganizationRepository
import com.example.nexonote.domain.repository.TableRepository
import com.example.nexonote.domain.repository.TaskRepository
import kotlinx.coroutines.flow.first

/**
 * Snapshot access for export. It reuses the domain repositories (and therefore the same
 * normalisation rules as the rest of the app) instead of querying Room directly.
 */
class LocalExportDataRepository(
    private val notes: NoteRepository,
    private val tasks: TaskRepository,
    private val tables: TableRepository,
    private val organization: OrganizationRepository,
) : ExportDataRepository {
    override suspend fun notes(): List<Note> = notes.notes.first()

    override suspend fun tasks(): List<Task> = tasks.tasks.first()

    override suspend fun tables(): List<CustomTable> = tables.tables.first()

    override suspend fun columns(tableId: Long): List<CustomColumn> = tables.observeColumns(tableId).first()

    override suspend fun rows(tableId: Long): List<TableRow> {
        val result = mutableListOf<TableRow>()
        var offset = 0
        while (result.size < MAX_ROWS) {
            val page = tables.loadPage(
                tableId = tableId,
                query = TableQuery(),
                limit = PAGE_SIZE,
                offset = offset,
            )
            result.addAll(page.rows)
            if (!page.hasMore || page.rows.isEmpty()) break
            offset += page.rows.size
        }
        return result
    }

    override suspend fun folders(): List<Folder> = organization.folders.first()

    override suspend fun categories(): List<Category> = organization.categories.first()

    private companion object {
        const val PAGE_SIZE = 200
        const val MAX_ROWS = 50_000
    }
}
