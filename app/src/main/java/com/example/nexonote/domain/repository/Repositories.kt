package com.example.nexonote.domain.repository

import com.example.nexonote.domain.model.Activity
import com.example.nexonote.domain.model.Entry
import com.example.nexonote.domain.model.EntryType
import com.example.nexonote.domain.model.Note
import com.example.nexonote.domain.model.NoteInput
import com.example.nexonote.domain.model.ColumnInput
import com.example.nexonote.domain.model.ColumnOption
import com.example.nexonote.domain.model.ColumnType
import com.example.nexonote.domain.model.CustomColumn
import com.example.nexonote.domain.model.CustomTable
import com.example.nexonote.domain.model.OptionInput
import com.example.nexonote.domain.model.RecentTableRow
import com.example.nexonote.domain.model.TableInput
import com.example.nexonote.domain.model.TablePage
import com.example.nexonote.domain.model.TableQuery
import com.example.nexonote.domain.model.TableRow
import com.example.nexonote.domain.model.Task
import com.example.nexonote.domain.model.TaskInput
import com.example.nexonote.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface EntryRepository {
    val entries: Flow<List<Entry>>
    suspend fun create(title: String, type: EntryType)
    suspend fun setTaskCompleted(id: Long, completed: Boolean)
}

interface ActivityRepository {
    val recent: Flow<List<Activity>>
}

interface NoteRepository {
    val notes: Flow<List<Note>>
    val labels: Flow<List<String>>
    fun observeNote(id: Long): Flow<Note?>
    suspend fun save(id: Long?, input: NoteInput): Long
    suspend fun setPinned(id: Long, pinned: Boolean)
    suspend fun setFavorite(id: Long, favorite: Boolean)
    suspend fun setArchived(id: Long, archived: Boolean)
    suspend fun moveToTrash(id: Long)
    suspend fun restore(id: Long)
    suspend fun deletePermanently(id: Long)
    suspend fun emptyTrash()
}

interface TaskRepository {
    val tasks: Flow<List<Task>>
    val labels: Flow<List<String>>
    fun observeTask(id: Long): Flow<Task?>
    suspend fun findTask(id: Long): Task?
    suspend fun save(id: Long?, input: TaskInput): Long
    suspend fun setCompleted(id: Long, completed: Boolean)
    suspend fun setSubtaskCompleted(taskId: Long, subtaskId: Long, completed: Boolean)
    suspend fun delete(id: Long)
    suspend fun stopRecurrence(id: Long)
    suspend fun rescheduleReminders()
}

interface TableRepository {
    val tables: Flow<List<CustomTable>>
    val recentRows: Flow<List<RecentTableRow>>
    fun observeTable(id: Long): Flow<CustomTable?>
    fun observeColumns(tableId: Long): Flow<List<CustomColumn>>
    fun observeOptions(tableId: Long): Flow<List<ColumnOption>>
    suspend fun findTable(id: Long): CustomTable?
    suspend fun findRow(rowId: Long): TableRow?
    suspend fun createTable(input: TableInput): Long
    suspend fun updateTable(id: Long, input: TableInput)
    suspend fun duplicateTable(id: Long): Long
    suspend fun deleteTable(id: Long)
    suspend fun addColumn(tableId: Long, input: ColumnInput): Long
    suspend fun updateColumn(columnId: Long, input: ColumnInput)
    suspend fun changeColumnType(columnId: Long, type: ColumnType)
    suspend fun moveColumn(columnId: Long, offset: Int)
    suspend fun deleteColumn(columnId: Long)
    suspend fun addOption(columnId: Long, input: OptionInput): Long
    suspend fun updateOption(optionId: Long, input: OptionInput)
    suspend fun deleteOption(optionId: Long)
    suspend fun createRow(tableId: Long): Long
    suspend fun duplicateRow(rowId: Long): Long
    suspend fun deleteRow(rowId: Long)
    suspend fun updateCell(rowId: Long, columnId: Long, value: String?)
    suspend fun loadPage(
        tableId: Long,
        query: TableQuery,
        limit: Int,
        offset: Int,
    ): TablePage
}

interface SettingsRepository {
    val displayName: Flow<String>
    suspend fun setDisplayName(name: String)
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
}
