package com.example.nexonote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.MoreVert
import com.example.nexonote.domain.model.ContentType
import com.example.nexonote.domain.model.OrganizationItem
import com.example.nexonote.navigation.OrganizationRoutes
import com.example.nexonote.ui.calendar.CalendarViewModel
import com.example.nexonote.ui.calendar.CalendarRoute
import com.example.nexonote.ui.calendar.CalendarDashboardCard
import com.example.nexonote.ui.organization.FavoritesRoute
import com.example.nexonote.ui.organization.FavoritesViewModel
import com.example.nexonote.ui.organization.FoldersRoute
import com.example.nexonote.ui.organization.FoldersViewModel
import com.example.nexonote.ui.organization.GlobalSearchRoute
import com.example.nexonote.ui.organization.SearchViewModel
import com.example.nexonote.ui.organization.StatisticsRoute
import com.example.nexonote.ui.organization.StatisticsViewModel
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.nexonote.navigation.TransferRoutes
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
import com.example.nexonote.ui.transfer.BackupRoute
import com.example.nexonote.ui.transfer.BackupViewModel
import com.example.nexonote.ui.transfer.ExportRoute
import com.example.nexonote.ui.transfer.ExportViewModel
import com.example.nexonote.ui.transfer.ImportRoute
import com.example.nexonote.ui.transfer.ImportViewModel
import com.example.nexonote.ui.transfer.RestoreRoute
import com.example.nexonote.ui.transfer.RestoreViewModel
import com.example.nexonote.ui.transfer.TransferFactories

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
    organizationFactories: OrganizationFactories? = null,
    transferFactories: TransferFactories? = null,
) {
    val controller = rememberNavController()
    val backStack by controller.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val chromeHidden = route == NoteRoutes.EDITOR ||
        route == TaskRoutes.EDITOR ||
        route == TableRoutes.DETAIL ||
        route == "calendar?date={date}" ||
        route == OrganizationRoutes.FOLDERS ||
        route == OrganizationRoutes.FAVORITES ||
        route == OrganizationRoutes.SEARCH ||
        route == OrganizationRoutes.STATISTICS ||
        route == TransferRoutes.EXPORT ||
        route == TransferRoutes.IMPORT ||
        route == TransferRoutes.BACKUP ||
        route == TransferRoutes.RESTORE
    val current = Destination.entries.firstOrNull { it.route == route }
        ?: Destination.HOME
    val snackbar = remember { SnackbarHostState() }
    var organizationMenu by remember { mutableStateOf(false) }
    var transferMenu by remember { mutableStateOf(false) }
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
    val openOrganization: (String) -> Unit = { destination ->
        controller.navigate(destination) { launchSingleTop = true }
    }
    val openTransfer: (String) -> Unit = { destination ->
        controller.navigate(destination) { launchSingleTop = true }
    }
    val openOrganizationItem: (OrganizationItem) -> Unit = { item ->
        when (item.type) {
            ContentType.NOTE -> openNote(item.id)
            ContentType.TASK -> openTask(item.id)
            ContentType.TABLE -> openTable(item.id)
            ContentType.EVENT -> {
                val date = item.date ?: System.currentTimeMillis()
                controller.navigate("calendar?date=$date") { launchSingleTop = true }
            }
        }
    }
    LaunchedEffect(pendingEventDate) {
        pendingEventDate?.let {
            controller.navigate("calendar?date=$it") { launchSingleTop = true }
            onPendingEventHandled()
        }
    }
    Scaffold(
        topBar = {
            if (!chromeHidden) {
                TopAppBar(title = { Text(if (current == Destination.HOME) "NexoNote" else current.label) }, actions = {
                    if (calendarFactory != null) IconButton(onClick = openCalendar) { Icon(Icons.Outlined.CalendarMonth, "Abrir calendario") }
                    if (organizationFactories != null) {
                        Box {
                            IconButton(onClick = { organizationMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "Organización y estadísticas")
                            }
                            DropdownMenu(
                                expanded = organizationMenu,
                                onDismissRequest = { organizationMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Carpetas, categorías y etiquetas") },
                                    onClick = { organizationMenu = false; openOrganization(OrganizationRoutes.FOLDERS) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Favoritos") },
                                    onClick = { organizationMenu = false; openOrganization(OrganizationRoutes.FAVORITES) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Búsqueda global") },
                                    onClick = { organizationMenu = false; openOrganization(OrganizationRoutes.SEARCH) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Estadísticas") },
                                    onClick = { organizationMenu = false; openOrganization(OrganizationRoutes.STATISTICS) },
                                )
                            }
                        }
                    }
                    if (transferFactories != null) {
                        Box {
                            IconButton(onClick = { transferMenu = true }) {
                                Icon(Icons.Outlined.ImportExport, contentDescription = "Datos y copias")
                            }
                            DropdownMenu(
                                expanded = transferMenu,
                                onDismissRequest = { transferMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Exportar notas, tareas o tablas") },
                                    onClick = { transferMenu = false; openTransfer(TransferRoutes.EXPORT) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Importar tabla desde CSV o XLSX") },
                                    onClick = { transferMenu = false; openTransfer(TransferRoutes.IMPORT) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Copias de seguridad") },
                                    onClick = { transferMenu = false; openTransfer(TransferRoutes.BACKUP) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Restaurar una copia") },
                                    onClick = { transferMenu = false; openTransfer(TransferRoutes.RESTORE) },
                                )
                            }
                        }
                    }
                })
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!chromeHidden) {
                NavigationBar {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = current == destination,
                            onClick = { navigate(destination) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                    onOpenOrganization = openOrganization,
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
            composable(Destination.SETTINGS.route) {
                SettingsScreen(
                    state = state,
                    setTheme = model::setThemeMode,
                    onOpenExport = transferFactories?.let { { openTransfer(TransferRoutes.EXPORT) } },
                    onOpenImport = transferFactories?.let { { openTransfer(TransferRoutes.IMPORT) } },
                    onOpenBackup = transferFactories?.let { { openTransfer(TransferRoutes.BACKUP) } },
                    onOpenRestore = transferFactories?.let { { openTransfer(TransferRoutes.RESTORE) } },
                )
            }
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
            if (organizationFactories != null) {
                composable(OrganizationRoutes.FOLDERS) {
                    val folders: FoldersViewModel = viewModel(factory = organizationFactories.folders)
                    FoldersRoute(folders) { controller.popBackStack() }
                }
                composable(OrganizationRoutes.FAVORITES) {
                    val favorites: FavoritesViewModel = viewModel(factory = organizationFactories.favorites)
                    FavoritesRoute(favorites, { controller.popBackStack() }, openOrganizationItem)
                }
                composable(OrganizationRoutes.SEARCH) {
                    val search: SearchViewModel = viewModel(factory = organizationFactories.search)
                    GlobalSearchRoute(search, { controller.popBackStack() }, openOrganizationItem)
                }
                composable(OrganizationRoutes.STATISTICS) {
                    val statistics: StatisticsViewModel = viewModel(factory = organizationFactories.statistics)
                    StatisticsRoute(statistics) { controller.popBackStack() }
                }
            }
            if (transferFactories != null) {
                composable(TransferRoutes.EXPORT) {
                    val export: ExportViewModel = viewModel(factory = transferFactories.export)
                    ExportRoute(export) { controller.popBackStack() }
                }
                composable(TransferRoutes.IMPORT) {
                    val importer: ImportViewModel = viewModel(factory = transferFactories.importer)
                    ImportRoute(importer) { controller.popBackStack() }
                }
                composable(TransferRoutes.BACKUP) {
                    val backup: BackupViewModel = viewModel(factory = transferFactories.backup)
                    BackupRoute(
                        model = backup,
                        onBack = { controller.popBackStack() },
                        onOpenRestore = { uri -> openTransfer(TransferRoutes.restore(uri.toString())) },
                    )
                }
                composable(
                    route = TransferRoutes.RESTORE,
                    arguments = listOf(
                        navArgument(TransferRoutes.RESTORE_URI) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    ),
                ) { entry ->
                    val uri = entry.arguments?.getString(TransferRoutes.RESTORE_URI)
                    val restore: RestoreViewModel = viewModel(factory = transferFactories.restore(uri))
                    RestoreRoute(restore) { controller.popBackStack() }
                }
            }
        }
    }
}

data class OrganizationFactories(
    val folders: ViewModelProvider.Factory,
    val favorites: ViewModelProvider.Factory,
    val search: ViewModelProvider.Factory,
    val statistics: ViewModelProvider.Factory,
)
