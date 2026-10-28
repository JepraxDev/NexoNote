package com.example.nexonote.ui.dashboard

import androidx.lifecycle.ViewModelStore
import com.example.nexonote.domain.model.Activity
import com.example.nexonote.domain.model.ActivityKind
import com.example.nexonote.domain.model.AccentColor
import com.example.nexonote.domain.model.ColumnInput
import com.example.nexonote.domain.model.ColumnOption
import com.example.nexonote.domain.model.ColumnType
import com.example.nexonote.domain.model.CustomColumn
import com.example.nexonote.domain.model.CustomTable
import com.example.nexonote.domain.model.Entry
import com.example.nexonote.domain.model.EntryType
import com.example.nexonote.domain.model.Note
import com.example.nexonote.domain.model.NoteInput
import com.example.nexonote.domain.model.NoteType
import com.example.nexonote.domain.model.OptionInput
import com.example.nexonote.domain.model.RecentTableRow
import com.example.nexonote.domain.model.TableInput
import com.example.nexonote.domain.model.TablePage
import com.example.nexonote.domain.model.TableQuery
import com.example.nexonote.domain.model.Task
import com.example.nexonote.domain.model.TaskInput
import com.example.nexonote.domain.model.TaskStatus
import com.example.nexonote.domain.model.ThemeMode
import com.example.nexonote.domain.repository.ActivityRepository
import com.example.nexonote.domain.repository.EntryRepository
import com.example.nexonote.domain.repository.NoteRepository
import com.example.nexonote.domain.repository.SettingsRepository
import com.example.nexonote.domain.repository.TableRepository
import com.example.nexonote.domain.repository.TaskRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun teardown() { store.clear(); Dispatchers.resetMain() }

    @Test fun roomEmissionsAndNameChangesUpdateDashboard() = runTest {
        val entries = FakeEntries()
        val notes = FakeNotes()
        val tasks = FakeTasks()
        val activities = FakeActivities()
        val settings = FakeSettings()
        val model = model(entries, notes, tasks, activities, settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        runCurrent()
        assertFalse(model.uiState.value.loading)
        tasks.data.value = listOf(Task(1, "Tarea"))
        notes.data.value = listOf(note(1, "Nota"))
        runCurrent()
        assertEquals(1, model.uiState.value.pendingCount)
        assertEquals(1, model.uiState.value.noteCount)
        tasks.data.value = tasks.data.value.map { it.copy(status = TaskStatus.COMPLETED) }
        activities.data.value = listOf(Activity(1, 1, ActivityKind.TASK_COMPLETED, 100, "Tarea"))
        settings.displayName.value = "Ana"
        runCurrent()
        assertEquals(100, model.uiState.value.percentage)
        assertEquals("Ana", model.uiState.value.displayName)
        assertEquals(listOf(ActivityKind.TASK_COMPLETED), model.uiState.value.recentActivity.map { it.kind })
    }

    @Test fun readFailureCanBeRetriedWithoutRecreatingViewModel() = runTest {
        val entries = FakeEntries().apply { failRead = true }
        val notes = FakeNotes()
        val tasks = FakeTasks()
        val model = model(entries, notes, tasks, FakeActivities(), FakeSettings())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        runCurrent()
        assertNotNull(model.uiState.value.error)
        assertFalse(model.uiState.value.loading)
        entries.failRead = false
        entries.data.value = listOf(Entry(1, "Tabla", EntryType.TABLE))
        tasks.data.value = listOf(Task(1, "Tarea"))
        notes.data.value = listOf(note(2, "Nota"))
        model.retry()
        runCurrent()
        assertNull(model.uiState.value.error)
        assertEquals(1, model.uiState.value.noteCount)
    }

    private fun model(
        entries: EntryRepository,
        notes: NoteRepository,
        tasks: TaskRepository,
        activities: ActivityRepository,
        settings: SettingsRepository,
    ) = DashboardViewModel(entries, notes, tasks, activities, settings, FakeTables()) { 100L }
        .also { store.put("dashboard", it) }

    private fun note(id: Long, title: String) = Note(
        id = id,
        title = title,
        content = "",
        type = NoteType.TEXT,
    )

    private class FakeEntries : EntryRepository {
        val data = MutableStateFlow<List<Entry>>(emptyList())
        var failRead = false
        override val entries = flow {
            if (failRead) throw IOException("Read failed")
            emitAll(data)
        }
        override suspend fun create(title: String, type: EntryType) = Unit
        override suspend fun setTaskCompleted(id: Long, completed: Boolean) = Unit
    }

    private class FakeNotes : NoteRepository {
        val data = MutableStateFlow<List<Note>>(emptyList())
        override val notes = data
        override val labels = MutableStateFlow<List<String>>(emptyList())
        override fun observeNote(id: Long) = data.map { notes -> notes.firstOrNull { it.id == id } }
        override suspend fun save(id: Long?, input: NoteInput): Long = 0
        override suspend fun setPinned(id: Long, pinned: Boolean) = Unit
        override suspend fun setFavorite(id: Long, favorite: Boolean) = Unit
        override suspend fun setArchived(id: Long, archived: Boolean) = Unit
        override suspend fun moveToTrash(id: Long) = Unit
        override suspend fun restore(id: Long) = Unit
        override suspend fun deletePermanently(id: Long) = Unit
        override suspend fun emptyTrash() = Unit
    }

    private class FakeTasks : TaskRepository {
        val data = MutableStateFlow<List<Task>>(emptyList())
        override val tasks = data
        override val labels = MutableStateFlow<List<String>>(emptyList())
        override fun observeTask(id: Long) = data.map { tasks -> tasks.firstOrNull { it.id == id } }
        override suspend fun findTask(id: Long): Task? = data.value.firstOrNull { it.id == id }
        override suspend fun save(id: Long?, input: TaskInput): Long = 0
        override suspend fun setCompleted(id: Long, completed: Boolean) = Unit
        override suspend fun setSubtaskCompleted(taskId: Long, subtaskId: Long, completed: Boolean) = Unit
        override suspend fun delete(id: Long) = Unit
        override suspend fun stopRecurrence(id: Long) = Unit
        override suspend fun rescheduleReminders() = Unit
    }

    private class FakeTables : TableRepository {
        override val tables = MutableStateFlow<List<CustomTable>>(emptyList())
        override val recentRows = MutableStateFlow<List<RecentTableRow>>(emptyList())
        override fun observeTable(id: Long) = flowOf<CustomTable?>(null)
        override fun observeColumns(tableId: Long) = flowOf<List<CustomColumn>>(emptyList())
        override fun observeOptions(tableId: Long) = flowOf<List<ColumnOption>>(emptyList())
        override suspend fun findTable(id: Long): CustomTable? = null
        override suspend fun findRow(rowId: Long) = null
        override suspend fun createTable(input: TableInput): Long = 0
        override suspend fun updateTable(id: Long, input: TableInput) = Unit
        override suspend fun duplicateTable(id: Long): Long = 0
        override suspend fun deleteTable(id: Long) = Unit
        override suspend fun addColumn(tableId: Long, input: ColumnInput): Long = 0
        override suspend fun updateColumn(columnId: Long, input: ColumnInput) = Unit
        override suspend fun changeColumnType(columnId: Long, type: ColumnType) = Unit
        override suspend fun moveColumn(columnId: Long, offset: Int) = Unit
        override suspend fun deleteColumn(columnId: Long) = Unit
        override suspend fun addOption(columnId: Long, input: OptionInput): Long = 0
        override suspend fun updateOption(optionId: Long, input: OptionInput) = Unit
        override suspend fun deleteOption(optionId: Long) = Unit
        override suspend fun createRow(tableId: Long): Long = 0
        override suspend fun duplicateRow(rowId: Long): Long = 0
        override suspend fun deleteRow(rowId: Long) = Unit
        override suspend fun updateCell(rowId: Long, columnId: Long, value: String?) = Unit
        override suspend fun loadPage(tableId: Long, query: TableQuery, limit: Int, offset: Int) =
            TablePage(emptyList(), false)
    }

    private class FakeActivities : ActivityRepository {
        val data = MutableStateFlow<List<Activity>>(emptyList())
        override val recent = data
    }

    private class FakeSettings : SettingsRepository {
        override val displayName = MutableStateFlow("")
        override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
        override val accentColor = MutableStateFlow(AccentColor.PURPLE)
        override suspend fun setDisplayName(name: String) {
            displayName.value = name
        }
        override suspend fun setThemeMode(mode: ThemeMode) { themeMode.value = mode }
        override suspend fun setAccentColor(color: AccentColor) { accentColor.value = color }
    }
}
