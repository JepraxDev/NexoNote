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
class NotesFlowTest {
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
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Acciones rápidas").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav_notes").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("notes_list").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After fun teardown() {
        compose.runOnIdle { models.clear() }
        database.close()
    }

    @Test fun noteCanBeCreatedSearchedPinnedArchivedTrashedRestoredAndDeleted() {
        compose.onNodeWithTag("notes_add").performClick()
        waitForTag("note_title")
        compose.onNodeWithTag("note_title").performTextInput("Viaje a la costa")
        compose.onNodeWithTag("note_content").performTextInput("Reservar hotel y revisar el clima")
        compose.onNodeWithContentDescription("Volver").performClick()
        waitUntilTagGone("note_title")
        waitForText("Viaje a la costa")

        compose.onNodeWithTag("notes_search").performTextInput("costa")
        waitForText("Viaje a la costa")
        compose.onNodeWithContentDescription("Borrar búsqueda").performClick()

        compose.onNodeWithContentDescription("Fijar Viaje a la costa").performScrollTo().performClick()
        waitForContentDescription("Desfijar Viaje a la costa")
        compose.onNodeWithContentDescription("Marcar como favorita Viaje a la costa")
            .performScrollTo().performClick()
        waitForContentDescription("Quitar de favoritas Viaje a la costa")

        compose.onNodeWithContentDescription("Archivar Viaje a la costa").performScrollTo().performClick()
        waitUntilTextGone("Viaje a la costa")
        compose.onNodeWithText("Archivadas (1)").performClick()
        waitForText("Viaje a la costa")
        compose.onNodeWithContentDescription("Desarchivar Viaje a la costa").performScrollTo().performClick()
        waitUntilTextGone("Viaje a la costa")
        compose.onNodeWithText("Notas (1)").performClick()
        waitForText("Viaje a la costa")

        compose.onNodeWithContentDescription("Mover a papelera Viaje a la costa").performScrollTo().performClick()
        waitUntilTextGone("Viaje a la costa")
        compose.onNodeWithText("Papelera (1)").performClick()
        waitForText("Viaje a la costa")
        compose.onNodeWithContentDescription("Restaurar Viaje a la costa").performScrollTo().performClick()
        waitUntilTextGone("Viaje a la costa")
        compose.onNodeWithText("Notas (1)").performClick()
        waitForText("Viaje a la costa")

        compose.onNodeWithContentDescription("Mover a papelera Viaje a la costa").performScrollTo().performClick()
        waitUntilTextGone("Viaje a la costa")
        compose.onNodeWithText("Papelera (1)").performClick()
        waitForText("Viaje a la costa")
        compose.onNodeWithContentDescription("Eliminar Viaje a la costa").performScrollTo().performClick()
        compose.onNodeWithText("Eliminar").performClick()
        waitForText("La papelera está vacía")
    }

    @Test fun checklistAndLabelSurviveAutosaveAndBack() {
        compose.onNodeWithTag("notes_add").performClick()
        waitForTag("note_title")
        compose.onNodeWithTag("note_title").performTextInput("Compra semanal")
        compose.onNodeWithText("Lista").performClick()
        compose.onNodeWithTag("add_checklist").performScrollTo().performClick()
        waitForTag("checklist_item_1")
        compose.onNodeWithTag("checklist_item_1").performTextInput("Leche")
        compose.onNode(isToggleable()).performClick()

        compose.onNodeWithTag("add_label").performScrollTo().performClick()
        waitForTag("label_input")
        compose.onNodeWithTag("label_input").performTextInput("compras")
        compose.onNodeWithContentDescription("Añadir etiqueta").performClick()
        compose.onNodeWithText("Listo").performClick()

        compose.onNodeWithContentDescription("Volver").performClick()
        waitUntilTagGone("note_title")
        waitForText("Compra semanal")
        waitForText("1 de 1 completados")
        waitForText("#compras")
    }

    private fun waitForText(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitUntilTextGone(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
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

    private fun waitForContentDescription(description: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
