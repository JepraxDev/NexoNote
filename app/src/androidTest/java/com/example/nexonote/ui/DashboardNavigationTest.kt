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
class DashboardNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: NexoDatabase
    private val models = ViewModelStore()

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, NexoDatabase::class.java,
        ).build()
        val entries = LocalEntryRepository(database.entryDao())
        val notes = LocalNoteRepository(database)
        val tasks = LocalTaskRepository(database, FakeReminderScheduler())
        val tables = LocalTableRepository(database)
        val organization = LocalOrganizationRepository(database)
        val activities = LocalActivityRepository(database.entryDao())
        val settings = object : SettingsRepository {
            override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
            override val displayName = MutableStateFlow("")
            override suspend fun setThemeMode(mode: ThemeMode) { themeMode.value = mode }
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
        val editorFactory: (Long?) -> androidx.lifecycle.ViewModelProvider.Factory = { noteId ->
            viewModelFactory {
                initializer {
                    NoteEditorViewModel(notes, organization, noteId).also { models.put("editor-$noteId", it) }
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
                    editorFactory,
                    tasksFactory,
                    taskEditorFactory,
                    tablesFactory,
                    tableDetailFactory,
                )
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Configurar mi nombre").fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun teardown() {
        compose.runOnIdle { models.clear() }
        database.close()
    }

    @Test fun quickActionsOpenFormsAndRoomChangesReachDashboard() {
        createNoteFromDashboard("Idea del dashboard")
        compose.onNode(hasText("Notas") and hasText("1")).assertExists()
        createTaskFromDashboard("Pendiente del dashboard")
        compose.onNode(hasText("Tareas pendientes") and hasText("1")).assertExists()
        createTableFromDashboard("Tabla del dashboard")
        compose.onNode(hasText("Tablas") and hasText("1")).assertExists()

        compose.onNodeWithText("Tareas", useUnmergedTree = true).performClick()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("Inicio").performClick()
        scrollTo("Tu progreso")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("100%").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("100%").assertExists()
        scrollTo("Notas recientes")
        val recentNote = compose.onNode(
            hasText("Idea del dashboard") and hasText("Modificada:", substring = true)
        )
        recentNote.assertExists()
        recentNote.performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("note_title").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("note_title").assertTextContains("Idea del dashboard")
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("note_title").fetchSemanticsNodes().isEmpty()
        }
        scrollTo("Actividad reciente")
        compose.onNodeWithText("Completaste una tarea").assertExists()
    }

    @Test fun nameAndThemeCanBeChangedWithoutBreakingNavigation() {
        compose.onNodeWithText("Configurar mi nombre").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Ana")
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(", Ana", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Ajustes", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Oscuro").performClick()
        compose.onNodeWithText("Inicio").performClick()
        compose.onNodeWithText(", Ana", substring = true).assertExists()
        compose.onNodeWithText("Ajustes", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Claro").performClick()
        compose.onNodeWithText("Inicio").performClick()
        compose.onNodeWithText(", Ana", substring = true).assertExists()
    }

    private fun createNoteFromDashboard(title: String) {
        scrollTo("Nueva nota")
        compose.onNodeWithText("Nueva nota").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("note_title").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("note_title").performTextInput(title)
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("note_title").fetchSemanticsNodes().isEmpty()
        }
        scrollTo("Notas")
    }

    private fun createTaskFromDashboard(title: String) {
        scrollTo("Nueva tarea")
        compose.onNodeWithText("Nueva tarea").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("task_title").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("task_title").performTextInput(title)
        compose.onNodeWithContentDescription("Guardar tarea").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("task_title").fetchSemanticsNodes().isEmpty()
        }
        scrollTo("Notas")
    }

    private fun createTableFromDashboard(title: String) {
        scrollTo("Nueva tabla")
        compose.onNodeWithText("Nueva tabla").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("table_name").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("table_name").performTextInput(title)
        compose.onNodeWithTag("table_confirm").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("new_row").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("table_search").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Inicio").performClick()
        scrollTo("Notas")
    }

    private fun scrollTo(text: String) {
        compose.onNodeWithTag("dashboard_grid").performScrollToNode(hasText(text))
    }
}
