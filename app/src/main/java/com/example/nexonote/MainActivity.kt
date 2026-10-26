package com.example.nexonote

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nexonote.domain.model.ThemeMode
import com.example.nexonote.ui.NexoNoteApp
import com.example.nexonote.ui.NexoViewModel
import com.example.nexonote.ui.dashboard.DashboardViewModel
import com.example.nexonote.ui.notes.NoteEditorViewModel
import com.example.nexonote.ui.notes.NotesViewModel
import com.example.nexonote.ui.tables.TableDetailViewModel
import com.example.nexonote.ui.tables.TablesViewModel
import com.example.nexonote.ui.tasks.TaskEditorViewModel
import com.example.nexonote.ui.tasks.TaskListViewModel
import com.example.nexonote.ui.theme.NexoNoteTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TASK_ID = "com.example.nexonote.extra.TASK_ID"
    }

    private val pendingTaskId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingTaskId.value = intent.taskIdExtra()
        enableEdgeToEdge()
        val container = (application as NexoNoteApplication).container
        setContent {
            val appFactory = remember(container) { NexoViewModel.factory(container) }
            val dashboardFactory = remember(container) { DashboardViewModel.factory(container) }
            val notesFactory = remember(container) { NotesViewModel.factory(container) }
            val editorFactory = remember(container) {
                { noteId: Long? -> NoteEditorViewModel.factory(container, noteId) }
            }
            val tasksFactory = remember(container) { TaskListViewModel.factory(container) }
            val taskEditorFactory = remember(container) {
                { taskId: Long? -> TaskEditorViewModel.factory(container, taskId) }
            }
            val tablesFactory = remember(container) { TablesViewModel.factory(container) }
            val tableDetailFactory = remember(container) {
                { tableId: Long -> TableDetailViewModel.factory(tableId, container) }
            }
            val model: NexoViewModel = viewModel(factory = appFactory)
            val state by model.uiState.collectAsStateWithLifecycle()
            val dark = when (state.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            NexoNoteTheme(darkTheme = dark) {
                NexoNoteApp(
                    state = state,
                    model = model,
                    dashboardFactory = dashboardFactory,
                    notesFactory = notesFactory,
                    noteEditorFactory = editorFactory,
                    tasksFactory = tasksFactory,
                    taskEditorFactory = taskEditorFactory,
                    tablesFactory = tablesFactory,
                    tableDetailFactory = tableDetailFactory,
                    pendingTaskId = pendingTaskId.value,
                    onPendingTaskHandled = { pendingTaskId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingTaskId.value = intent.taskIdExtra()
    }

    private fun Intent?.taskIdExtra(): Long? =
        this?.getLongExtra(EXTRA_TASK_ID, 0L)?.takeIf { it > 0 }
}
