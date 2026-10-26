package com.example.nexonote.ui.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexonote.domain.model.Task
import com.example.nexonote.domain.model.TaskDateFilter
import com.example.nexonote.domain.model.TaskFilters
import com.example.nexonote.domain.model.TaskPriority
import com.example.nexonote.domain.model.TaskRecurrence
import com.example.nexonote.domain.model.TaskSort
import com.example.nexonote.domain.model.TaskStatus
import com.example.nexonote.domain.model.TaskStatusFilter
import com.example.nexonote.domain.model.isTaskOverdue
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val taskLocale = Locale.forLanguageTag("es")

@Composable
fun TaskListRoute(
    model: TaskListViewModel,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.operationError) {
        state.operationError?.let {
            snackbar.showSnackbar(it)
            model.dismissError()
        }
    }
    TaskListScreen(
        state = state,
        snackbar = snackbar,
        onCreate = onCreate,
        onOpen = onOpen,
        onQueryChange = model::setQuery,
        onStatusChange = model::setStatusFilter,
        onDateChange = model::setDateFilter,
        onSortChange = model::setSort,
        onToggleCompleted = model::toggleCompleted,
        onDelete = model::delete,
        onStopRecurrence = model::stopRecurrence,
        onClearFilters = model::clearFilters,
        onRetry = model::retry,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskListScreen(
    state: TaskListUiState,
    snackbar: SnackbarHostState,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit,
    onQueryChange: (String) -> Unit,
    onStatusChange: (TaskStatusFilter) -> Unit,
    onDateChange: (TaskDateFilter) -> Unit,
    onSortChange: (TaskSort) -> Unit,
    onToggleCompleted: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    onStopRecurrence: (Task) -> Unit,
    onClearFilters: () -> Unit,
    onRetry: () -> Unit,
) {
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var taskToStop by remember { mutableStateOf<Task?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val width = maxWidth.value.coerceAtMost(980f).dp
        Box(Modifier.width(width).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("task_list"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "summary") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${state.activeTasks} activas · ${state.completedTasks} completadas",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Organiza, prioriza y cumple tus pendientes.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SortMenu(state.filters.sort, onSortChange)
                    }
                }
                item(key = "search") {
                    OutlinedTextField(
                        value = state.filters.query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth().testTag("task_search"),
                        label = { Text("Buscar tareas") },
                        placeholder = { Text("Título, etiqueta o subtarea") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.filters.query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Borrar búsqueda")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {}),
                    )
                }
                item(key = "filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            statusFilterOptions.forEach { (filter, label) ->
                                FilterChip(
                                    selected = state.filters.status == filter,
                                    onClick = { onStatusChange(filter) },
                                    label = { Text(label) },
                                )
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            dateFilterOptions.forEach { (filter, label) ->
                                FilterChip(
                                    selected = state.filters.date == filter,
                                    onClick = { onDateChange(filter) },
                                    label = { Text(label) },
                                )
                            }
                        }
                        if (state.filters != TaskFilters()) {
                            TextButton(onClick = onClearFilters) {
                                Text("Limpiar filtros")
                            }
                        }
                    }
                }
                if (state.busy) {
                    item(key = "busy") {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                when {
                    state.loading && state.totalTasks == 0 -> item(key = "loading") {
                        Column(
                            Modifier.fillMaxWidth().padding(top = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            CircularProgressIndicator()
                            Text("Cargando tareas…")
                        }
                    }
                    state.error != null -> item(key = "error") {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("No se pudieron cargar las tareas", style = MaterialTheme.typography.titleLarge)
                                Text(state.error)
                                Button(onClick = onRetry) { Text("Reintentar") }
                            }
                        }
                    }
                    !state.hasTasks -> item(key = "empty") {
                        EmptyTaskState(
                            title = "Aún no tienes tareas",
                            message = "Crea tu primera tarea y empieza a organizar tu día.",
                            actionLabel = "Nueva tarea",
                            onAction = onCreate,
                        )
                    }
                    state.sections.isEmpty() -> item(key = "noMatches") {
                        EmptyTaskState(
                            title = "Sin resultados",
                            message = "Prueba otra búsqueda o cambia los filtros.",
                            actionLabel = "Limpiar filtros",
                            onAction = onClearFilters,
                        )
                    }
                    else -> {
                        state.sections.forEach { section ->
                            item(key = "header_${section.group.name}") {
                                Text(
                                    "${section.group.label} (${section.tasks.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            items(section.tasks, key = { it.id }) { task ->
                                TaskCard(
                                    task = task,
                                    now = state.now,
                                    onOpen = { onOpen(task.id) },
                                    onToggleCompleted = { onToggleCompleted(task) },
                                    onDelete = { taskToDelete = task },
                                    onStopRecurrence = { taskToStop = task },
                                )
                            }
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = onCreate,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).testTag("new_task"),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Nueva tarea")
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
        }
    }
    taskToDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("¿Eliminar tarea?") },
            text = { Text("Se eliminará “${task.displayTitle}” y sus subtareas. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    taskToDelete = null
                    onDelete(task)
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { taskToDelete = null }) { Text("Cancelar") } },
        )
    }
    taskToStop?.let { task ->
        AlertDialog(
            onDismissRequest = { taskToStop = null },
            title = { Text("¿Finalizar repetición?") },
            text = { Text("No se crearán más ocurrencias de esta tarea. Las tareas existentes se conservarán.") },
            confirmButton = {
                TextButton(onClick = {
                    taskToStop = null
                    onStopRecurrence(task)
                }) { Text("Finalizar") }
            },
            dismissButton = { TextButton(onClick = { taskToStop = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SortMenu(selected: TaskSort, onSelect: (TaskSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.Sort, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(sortLabels.getValue(selected))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sortLabels.forEach { (sort, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { expanded = false; onSelect(sort) },
                    leadingIcon = if (sort == selected) {
                        { Icon(Icons.Outlined.CheckCircle, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskCard(
    task: Task,
    now: Long,
    onOpen: () -> Unit,
    onToggleCompleted: () -> Unit,
    onDelete: () -> Unit,
    onStopRecurrence: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val overdue = isTaskOverdue(task, now)
    OutlinedCard(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().testTag("task_${task.id}"),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (task.isCompleted) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggleCompleted() },
                modifier = Modifier.testTag("task_done_${task.id}"),
            )
            Column(
                Modifier.weight(1f).padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        task.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (task.status == TaskStatus.IN_PROGRESS && !task.isCompleted) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                "En curso",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                if (task.description.isNotBlank()) {
                    Text(
                        task.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        scheduleLabel(task, overdue),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (task.priority != TaskPriority.UNSPECIFIED) {
                        PriorityBadge(task.priority)
                    }
                    if (task.reminderEnabled) {
                        Icon(
                            Icons.Outlined.Notifications,
                            contentDescription = "Recordatorio activo",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (task.recurrence != TaskRecurrence.NONE) {
                        Icon(
                            Icons.Outlined.Repeat,
                            contentDescription = recurrenceLabel(task.recurrence),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (task.totalSubtasks > 0) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LinearProgressIndicator(
                            progress = { task.subtaskProgress },
                            modifier = Modifier.weight(1f).height(5.dp),
                        )
                        Text(
                            "${task.completedSubtasks}/${task.totalSubtasks}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (task.labels.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        task.labels.forEach { label ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Opciones de la tarea")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (task.recurrence != TaskRecurrence.NONE) {
                        DropdownMenuItem(
                            text = { Text("Finalizar repetición") },
                            onClick = { menuOpen = false; onStopRecurrence() },
                            leadingIcon = { Icon(Icons.Outlined.Repeat, contentDescription = null) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Eliminar tarea") },
                        onClick = { menuOpen = false; onDelete() },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PriorityBadge(priority: TaskPriority) {
    val color = when (priority) {
        TaskPriority.URGENT -> MaterialTheme.colorScheme.errorContainer
        TaskPriority.HIGH -> MaterialTheme.colorScheme.tertiaryContainer
        TaskPriority.NORMAL -> MaterialTheme.colorScheme.primaryContainer
        TaskPriority.LOW -> MaterialTheme.colorScheme.secondaryContainer
        TaskPriority.UNSPECIFIED -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(shape = RoundedCornerShape(50), color = color) {
        Text(
            priority.label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun EmptyTaskState(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private fun scheduleLabel(task: Task, overdue: Boolean): String {
    val due = task.dueAt
    val base = if (due == null) {
        "Sin fecha"
    } else {
        val format = if (task.dueHasTime) {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, taskLocale)
        } else {
            DateFormat.getDateInstance(DateFormat.MEDIUM, taskLocale)
        }
        format.format(Date(due))
    }
    return when {
        overdue -> "Vencida · $base"
        task.isCompleted -> "Completada · $base"
        else -> base
    }
}

private fun recurrenceLabel(recurrence: TaskRecurrence): String = when (recurrence) {
    TaskRecurrence.NONE -> "Sin repetición"
    TaskRecurrence.DAILY -> "Se repite diariamente"
    TaskRecurrence.WEEKLY -> "Se repite semanalmente"
    TaskRecurrence.MONTHLY -> "Se repite mensualmente"
}

private val statusFilterOptions = listOf(
    TaskStatusFilter.ALL to "Todas",
    TaskStatusFilter.ACTIVE to "Activas",
    TaskStatusFilter.PENDING to "Pendientes",
    TaskStatusFilter.IN_PROGRESS to "En curso",
    TaskStatusFilter.COMPLETED to "Completadas",
)

private val dateFilterOptions = listOf(
    TaskDateFilter.ALL to "Cualquier fecha",
    TaskDateFilter.TODAY to "Hoy",
    TaskDateFilter.WEEK to "Próximos 7 días",
    TaskDateFilter.OVERDUE to "Vencidas",
    TaskDateFilter.NO_DATE to "Sin fecha",
)

private val sortLabels = linkedMapOf(
    TaskSort.DUE_DATE to "Vencimiento",
    TaskSort.PRIORITY to "Prioridad",
    TaskSort.MODIFIED to "Modificadas",
    TaskSort.TITLE to "Título",
)
