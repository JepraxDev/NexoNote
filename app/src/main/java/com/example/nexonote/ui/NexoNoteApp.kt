package com.example.nexonote.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import com.example.nexonote.ui.calendar.CalendarViewModel
import com.example.nexonote.ui.calendar.CalendarRoute
import com.example.nexonote.ui.calendar.CalendarDashboardCard
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.nexonote.navigation.Destination
import com.example.nexonote.navigation.NoteRoutes
import com.example.nexonote.navigation.TableRoutes
import com.example.nexonote.navigation.TaskRoutes
import com.example.nexonote.ui.dashboard.DashboardRoute
import com.example.nexonote.ui.dashboard.DashboardViewModel
import com.example.nexonote.ui.notes.NoteEditorRoute
import com.example.nexonote.ui.notes.NoteEditorViewModel
import com.example.nexonote.ui.notes.NotesRoute
import com.example.nexonote.ui.notes.NotesViewModel
import com.example.nexonote.ui.screens.SettingsScreen
import com.example.nexonote.ui.tables.TableDetailRoute
import com.example.nexonote.ui.tables.TableDetailViewModel
import com.example.nexonote.ui.tables.TablesRoute
import com.example.nexonote.ui.tables.TablesViewModel
import com.example.nexonote.ui.tasks.TaskEditorRoute
import com.example.nexonote.ui.tasks.TaskEditorViewModel
import com.example.nexonote.ui.tasks.TaskListRoute
import com.example.nexonote.ui.tasks.TaskListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexoNoteApp(
    state: NexoUiState,
    model: NexoViewModel,
    dashboardFactory: ViewModelProvider.Factory,
    notesFactory: ViewModelProvider.Factory,
    noteEditorFactory: (Long?) -> ViewModelProvider.Factory,
    tasksFactory: ViewModelProvider.Factory,
    taskEditorFactory: (Long?) -> ViewModelProvider.Factory,
    tablesFactory: ViewModelProvider.Factory,
    tableDetailFactory: (Long) -> ViewModelProvider.Factory,
    pendingTaskId: Long? = null,
    onPendingTaskHandled: () -> Unit = {},
    calendarFactory: ViewModelProvider.Factory? = null,
    pendingEventDate: Long? = null,
    onPendingEventHandled: () -> Unit = {},
) {
    val controller = rememberNavController()
    val backStack by controller.currentBackStackEntryAsState()
    val isEditor = backStack?.destination?.route == NoteRoutes.EDITOR ||
        backStack?.destination?.route == TaskRoutes.EDITOR ||
        backStack?.destination?.route == TableRoutes.DETAIL ||
        backStack?.destination?.route == "calendar?date={date}"
    val current = Destination.entries.firstOrNull { it.route == backStack?.destination?.route }
        ?: Destination.HOME
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            model.dismissError()
        }
    }
    val navigate: (Destination) -> Unit = { destination ->
        controller.navigate(destination.route) {
            popUpTo(controller.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val openNote: (Long) -> Unit = { noteId ->
        controller.navigate(NoteRoutes.editor(noteId)) {
            launchSingleTop = true
        }
    }
    val openTask: (Long) -> Unit = { taskId ->
        controller.navigate(TaskRoutes.editor(taskId)) {
            launchSingleTop = true
        }
    }
    val openTable: (Long) -> Unit = { tableId ->
        controller.navigate(TableRoutes.detail(tableId)) {
            launchSingleTop = true
        }
    }
    LaunchedEffect(pendingTaskId) {
        pendingTaskId?.let {
            openTask(it)
            onPendingTaskHandled()
        }
    }
    val openCalendar: () -> Unit = { controller.navigate("calendar") { launchSingleTop = true } }
    LaunchedEffect(pendingEventDate) {
        pendingEventDate?.let {
            controller.navigate("calendar?date=$it") { launchSingleTop = true }
            onPendingEventHandled()
        }
    }
    Scaffold(
        topBar = {
            if (!isEditor) {
                TopAppBar(title = { Text(if (current == Destination.HOME) "NexoNote" else current.label) }, actions = {
                    if (calendarFactory != null) IconButton(onClick = openCalendar) { Icon(Icons.Outlined.CalendarMonth, "Abrir calendario") }
                })
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!isEditor) {
                NavigationBar {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = current == destination,
                            onClick = { navigate(destination) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label, maxLines = 1) },
                            modifier = Modifier.testTag("nav_${destination.route}"),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(controller, startDestination = Destination.HOME.route, modifier = Modifier.padding(padding)) {
            composable(Destination.HOME.route) {
                val dashboard: DashboardViewModel = viewModel(factory = dashboardFactory)
                DashboardRoute(
                    model = dashboard,
                    navigate = navigate,
                    create = { destination ->
                        when (destination) {
                            Destination.NOTES -> openNote(NoteRoutes.NEW_NOTE_ID)
                            Destination.TASKS -> openTask(TaskRoutes.NEW_TASK_ID)
                            else -> {
                                navigate(destination)
                                controller.currentBackStackEntry?.savedStateHandle?.set("openCreate", true)
                            }
                        }
                    },
                    openNote = openNote,
                    openTask = openTask,
                    openTable = openTable,
                    calendarContent = {
                        if (calendarFactory != null) {
                            val calendar: CalendarViewModel = viewModel(key = "dashboard_calendar", factory = calendarFactory)
                            CalendarDashboardCard(calendar, openCalendar)
                        }
                    },
                )
            }
            composable(Destination.NOTES.route) {
                val notes: NotesViewModel = viewModel(factory = notesFactory)
                NotesRoute(
                    model = notes,
                    onCreate = { openNote(NoteRoutes.NEW_NOTE_ID) },
                    onOpen = openNote,
                )
            }
            composable(Destination.TASKS.route) {
                val tasks: TaskListViewModel = viewModel(factory = tasksFactory)
                TaskListRoute(
                    model = tasks,
                    onCreate = { openTask(TaskRoutes.NEW_TASK_ID) },
                    onOpen = openTask,
                )
            }
            composable(Destination.TABLES.route) { entry ->
                val tables: TablesViewModel = viewModel(factory = tablesFactory)
                val openCreate by entry.savedStateHandle.getStateFlow("openCreate", false).collectAsStateWithLifecycle()
                TablesRoute(
                    model = tables,
                    openCreate = openCreate,
                    onCreateConsumed = { entry.savedStateHandle["openCreate"] = false },
                    onOpen = openTable,
                )
            }
            composable(Destination.SETTINGS.route) { SettingsScreen(state, model::setThemeMode) }
            composable("calendar?date={date}", arguments = listOf(navArgument("date") { type = NavType.LongType; defaultValue = 0L })) { entry ->
                if (calendarFactory != null) {
                    val calendar: CalendarViewModel = viewModel(factory = calendarFactory)
                    CalendarRoute(calendar, { controller.popBackStack() }, openTask,
                        entry.arguments?.getLong("date")?.takeIf { it != 0L })
                }
            }
            composable(
                route = NoteRoutes.EDITOR,
                arguments = listOf(navArgument(NoteRoutes.NOTE_ID) { type = NavType.LongType }),
            ) { entry ->
                val noteId = entry.arguments?.getLong(NoteRoutes.NOTE_ID)?.takeIf { it > 0 }
                val editor: NoteEditorViewModel = viewModel(factory = noteEditorFactory(noteId))
                NoteEditorRoute(editor) { controller.popBackStack() }
            }
            composable(
                route = TaskRoutes.EDITOR,
                arguments = listOf(navArgument(TaskRoutes.TASK_ID) { type = NavType.LongType }),
            ) { entry ->
                val taskId = entry.arguments?.getLong(TaskRoutes.TASK_ID)?.takeIf { it > 0 }
                val editor: TaskEditorViewModel = viewModel(factory = taskEditorFactory(taskId))
                TaskEditorRoute(editor) { controller.popBackStack() }
            }
            composable(
                route = TableRoutes.DETAIL,
                arguments = listOf(navArgument(TableRoutes.TABLE_ID) { type = NavType.LongType }),
            ) { entry ->
                val tableId = entry.arguments?.getLong(TableRoutes.TABLE_ID) ?: 0L
                val detail: TableDetailViewModel = viewModel(factory = tableDetailFactory(tableId))
                TableDetailRoute(
                    model = detail,
                    onBack = { controller.popBackStack() },
                    onOpenTable = openTable,
                )
            }
        }
    }
}
