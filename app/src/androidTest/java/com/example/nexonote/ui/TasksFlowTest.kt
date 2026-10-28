package com.example.nexonote.ui

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.nexonote.data.local.NexoDatabase
import com.example.nexonote.data.repository.LocalActivityRepository
import com.example.nexonote.data.repository.LocalEntryRepository
import com.example.nexonote.data.repository.LocalNoteRepository
import com.example.nexonote.data.repository.LocalOrganizationRepository
import com.example.nexonote.data.repository.LocalTableRepository
import com.example.nexonote.data.repository.LocalTaskRepository
import com.example.nexonote.domain.model.AccentColor
import com.example.nexonote.domain.model.ThemeMode
import com.example.nexonote.domain.repository.SettingsRepository
import com.example.nexonote.testing.FakeReminderScheduler
import com.example.nexonote.ui.dashboard.DashboardViewModel
import com.example.nexonote.ui.notes.NoteEditorViewModel
import com.example.nexonote.ui.notes.NotesViewModel
import com.example.nexonote.ui.tables.TableDetailViewModel
import com.example.nexonote.ui.tables.TablesViewModel
import com.example.nexonote.ui.tasks.TaskEditorViewModel
import com.example.nexonote.ui.tasks.TaskListViewModel
import com.example.nexonote.ui.theme.NexoNoteTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TasksFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: NexoDatabase
    private val models = ViewModelStore()

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NexoDatabase::class.java,
        ).build()
        val entries = LocalEntryRepository(database.entryDao())
        val notes = LocalNoteRepository(database)
        val tasks = LocalTaskRepository(database, FakeReminderScheduler())
        val tables = LocalTableRepository(database)
        val organization = LocalOrganizationRepository(database)
        val activities = LocalActivityRepository(database.entryDao())
        val settings = object : SettingsRepository {
            override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
            override val accentColor = MutableStateFlow(AccentColor.PURPLE)
            override val displayName = MutableStateFlow("")
            override suspend fun setThemeMode(mode: ThemeMode) { themeMode.value = mode }
            override suspend fun setAccentColor(color: AccentColor) { accentColor.value = color }
            override suspend fun setDisplayName(name: String) { displayName.value = name.trim() }
        }
        val model = NexoViewModel(entries, settings).also { models.put("app", it) }
        val dashboardFactory = viewModelFactory {
            initializer {
                DashboardViewModel(entries, notes, tasks, activities, settings, tables)
                    .also { models.put("dashboard", it) }
            }
        }
        val notesFactory = viewModelFactory {
            initializer { NotesViewModel(notes).also { models.put("notes", it) } }
        }
        val noteEditorFactory: (Long?) -> androidx.lifecycle.ViewModelProvider.Factory = { noteId ->
            viewModelFactory {
                initializer {
                    NoteEditorViewModel(notes, organization, noteId).also { models.put("note-editor-$noteId", it) }
                }
            }
        }
        val tasksFactory = viewModelFactory {
            initializer { TaskListViewModel(tasks).also { models.put("tasks", it) } }
        }
        val taskEditorFactory: (Long?) -> androidx.lifecycle.ViewModelProvider.Factory = { taskId ->
            viewModelFactory {
                initializer {
                    TaskEditorViewModel(tasks, organization, taskId).also { models.put("task-editor-$taskId", it) }
                }
            }
        }
        val tablesFactory = viewModelFactory {
            initializer { TablesViewModel(tables, organization).also { models.put("tables", it) } }
        }
        val tableDetailFactory: (Long) -> androidx.lifecycle.ViewModelProvider.Factory = { tableId ->
            viewModelFactory {
                initializer {
                    TableDetailViewModel(tableId, tables).also { models.put("table-$tableId", it) }
                }
            }
        }
        compose.setContent {
            val state by model.uiState.collectAsStateWithLifecycle()
            NexoNoteTheme(darkTheme = state.themeMode == ThemeMode.DARK) {
                NexoNoteApp(
                    state,
                    model,
                    dashboardFactory,
                    notesFactory,
                    noteEditorFactory,
                    tasksFactory,
                    taskEditorFactory,
                    tablesFactory,
                    tableDetailFactory,
                )
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Acciones rápidas").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav_tasks").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("task_list").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After fun teardown() {
        compose.runOnIdle { models.clear() }
        database.close()
    }

    @Test fun taskCanBeCreatedWithDetailsSearchedCompletedAndDeleted() {
        compose.onNodeWithTag("new_task").performClick()
        waitForTag("task_title")
        compose.onNodeWithTag("task_title").performTextInput("Entregar proyecto")
        compose.onNodeWithTag("task_editor").performScrollToNode(hasText("Urgente"))
        compose.onNodeWithText("Urgente").performClick()
        compose.onNodeWithTag("task_editor").performScrollToNode(hasTestTag("subtask_add"))
        compose.onNodeWithTag("subtask_add").performClick()
        waitForTag("subtask_0")
        compose.onNodeWithTag("subtask_0").performTextInput("Revisar cifras")
        compose.onNodeWithContentDescription("Guardar tarea").performClick()
        waitUntilTagGone("task_title")
        waitForText("Entregar proyecto")

        compose.onNodeWithTag("task_search").performTextInput("entrega")
        waitForText("Entregar proyecto")
        compose.onNodeWithContentDescription("Borrar búsqueda").performClick()

        compose.onNodeWithText("Entregar proyecto").performClick()
        waitForTag("task_title")
        compose.onNodeWithTag("task_editor").performScrollToNode(hasTestTag("subtask_0"))
        compose.onNodeWithTag("subtask_0").assertTextContains("Revisar cifras")
        compose.onNodeWithContentDescription("Volver").performClick()
        waitUntilTagGone("task_title")

        compose.onNode(isToggleable()).performClick()
        waitForText("Completadas (1)")

        compose.onNodeWithContentDescription("Opciones de la tarea").performClick()
        compose.onNodeWithText("Eliminar tarea").performClick()
        compose.onNodeWithText("Eliminar").performClick()
        waitForText("Aún no tienes tareas")
    }

    private fun waitForText(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitUntilTagGone(tag: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }
}
