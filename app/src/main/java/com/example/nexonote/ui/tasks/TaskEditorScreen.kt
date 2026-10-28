package com.example.nexonote.ui.tasks

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexonote.domain.model.TaskPriority
import com.example.nexonote.domain.model.TaskRecurrence
import com.example.nexonote.domain.model.TaskStatus
import com.example.nexonote.domain.model.taskReminderAt
import com.example.nexonote.ui.organization.OrganizationFields
import java.text.DateFormat as AndroidDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val editorTaskLocale = Locale.forLanguageTag("es")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorRoute(model: TaskEditorViewModel, onBack: () -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            model.dismissError()
        }
    }
    val requestExit = {
        if (state.dirty && !state.saving && !state.missing) showDiscard = true else onBack()
    }
    BackHandler {
        requestExit()
    }
    TaskEditorScreen(
        state = state,
        snackbar = snackbar,
        onBack = requestExit,
        onTitleChange = model::setTitle,
        onDescriptionChange = model::setDescription,
        onStatusChange = model::setStatus,
        onPriorityChange = model::setPriority,
        onStartDateChange = model::setStartDate,
        onDueDateChange = model::setDueDate,
        onDueHasTimeChange = model::setDueHasTime,
        onDueTimeChange = model::setDueTime,
        onReminderChange = model::setReminderEnabled,
        onRecurrenceChange = model::setRecurrence,
        onRecurrenceEndChange = model::setRecurrenceEnd,
        onAddLabel = model::addLabel,
        onRemoveLabel = model::removeLabel,
        onFolderChange = model::setFolder,
        onCategoryChange = model::setCategory,
        onToggleFavorite = model::toggleFavorite,
        onAddSubtask = model::addSubtask,
        onSubtaskTextChange = model::setSubtaskText,
        onSubtaskCompletedChange = model::setSubtaskCompleted,
        onRemoveSubtask = model::removeSubtask,
        onMoveSubtask = model::moveSubtask,
        onSave = { model.saveAndExit(onBack) },
        onSaveAndExit = { model.saveAndExit(onBack) },
        onDelete = { model.delete(onBack) },
        onStopRecurrence = model::stopRecurrence,
    )
    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("¿Descartar cambios?") },
            text = { Text("Los cambios sin guardar de esta tarea se perderán.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    onBack()
                }) { Text("Descartar") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) { Text("Seguir editando") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorScreen(
    state: TaskEditorUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onStatusChange: (TaskStatus) -> Unit,
    onPriorityChange: (TaskPriority) -> Unit,
    onStartDateChange: (Long?) -> Unit,
    onDueDateChange: (Long?) -> Unit,
    onDueHasTimeChange: (Boolean) -> Unit,
    onDueTimeChange: (Int, Int) -> Unit,
    onReminderChange: (Boolean) -> Unit,
    onRecurrenceChange: (TaskRecurrence) -> Unit,
    onRecurrenceEndChange: (Long?) -> Unit,
    onAddLabel: (String) -> Unit,
    onRemoveLabel: (String) -> Unit,
    onFolderChange: (Long?) -> Unit,
    onCategoryChange: (Long?) -> Unit,
    onToggleFavorite: () -> Unit,
    onAddSubtask: () -> Unit,
    onSubtaskTextChange: (Long, String) -> Unit,
    onSubtaskCompletedChange: (Long, Boolean) -> Unit,
    onRemoveSubtask: (Long) -> Unit,
    onMoveSubtask: (Long, Int) -> Unit,
    onSave: () -> Unit,
    onSaveAndExit: () -> Unit,
    onDelete: () -> Unit,
    onStopRecurrence: () -> Unit,
) {
    val context = LocalContext.current
    var showStartPicker by remember { mutableStateOf(false) }
    var showDuePicker by remember { mutableStateOf(false) }
    var showRecurrenceEndPicker by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showStopRecurrence by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(notificationsEnabled(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsEnabled = granted }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when {
                                state.loading -> "Cargando tarea"
                                state.taskId == null -> "Nueva tarea"
                                else -> "Editar tarea"
                            }
                        )
                        Text(
                            when {
                                state.saving -> "Guardando…"
                                state.savedAt != null && !state.dirty -> "Guardado ${timeLabel(state.savedAt)}"
                                state.dirty -> "Cambios sin guardar"
                                else -> "Tarea sincronizada"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.saving) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (!state.loading && !state.missing) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                if (state.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (state.favorite) {
                                    "Quitar de favoritos"
                                } else {
                                    "Marcar como favorito"
                                },
                                tint = if (state.favorite) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = onSave,
                            enabled = !state.saving,
                            modifier = Modifier.testTag("task_save"),
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = "Guardar tarea")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            state.missing -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Esta tarea ya no existe", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Es posible que se haya eliminado desde otro lugar.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onBack) { Text("Volver a las tareas") }
                }
            }
            else -> BoxWithConstraints(
                Modifier.fillMaxSize().padding(padding).imePadding(),
                contentAlignment = Alignment.TopCenter,
            ) {
                val width = maxWidth.value.coerceAtMost(860f).dp
                LazyColumn(
                    modifier = Modifier.width(width).fillMaxSize().testTag("task_editor"),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 64.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item(key = "details") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = state.title,
                                onValueChange = onTitleChange,
                                modifier = Modifier.fillMaxWidth().testTag("task_title"),
                                label = { Text("Título") },
                                placeholder = { Text("¿Qué necesitas hacer?") },
                                singleLine = true,
                                supportingText = { Text("${state.title.length}/120") },
                            )
                            OutlinedTextField(
                                value = state.description,
                                onValueChange = onDescriptionChange,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                                label = { Text("Descripción") },
                                placeholder = { Text("Añade contexto, pasos o notas…") },
                                minLines = 4,
                            )
                        }
                    }
                    item(key = "status") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionLabel("Estado")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                statusOptions.forEach { (status, label) ->
                                    FilterChip(
                                        selected = state.status == status,
                                        onClick = { onStatusChange(status) },
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }
                    }
                    item(key = "priority") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionLabel("Prioridad")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TaskPriority.entries.forEach { priority ->
                                    FilterChip(
                                        selected = state.priority == priority,
                                        onClick = { onPriorityChange(priority) },
                                        label = { Text(priority.label) },
                                    )
                                }
                            }
                        }
                    }
                    item(key = "dates") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionLabel("Fechas")
                            DateRow(
                                label = "Inicio",
                                timestamp = state.startAt,
                                onPick = { showStartPicker = true },
                                onClear = { onStartDateChange(null) },
                            )
                            DateRow(
                                label = "Vencimiento",
                                timestamp = state.dueAt,
                                onPick = { showDuePicker = true },
                                onClear = { onDueDateChange(null) },
                            )
                            if (state.dueAt != null) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Switch(
                                        checked = state.dueHasTime,
                                        onCheckedChange = onDueHasTimeChange,
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text("Incluir hora", style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            "Sin hora, la tarea vence al final del día.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (state.dueHasTime) {
                                        TextButton(onClick = {
                                            val calendar = Calendar.getInstance().apply {
                                                timeInMillis = state.dueAt
                                            }
                                            TimePickerDialog(
                                                context,
                                                { _, hour, minute -> onDueTimeChange(hour, minute) },
                                                calendar.get(Calendar.HOUR_OF_DAY),
                                                calendar.get(Calendar.MINUTE),
                                                DateFormat.is24HourFormat(context),
                                            ).show()
                                        }) {
                                            Icon(Icons.Outlined.Schedule, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text(timeText(state.dueAt))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item(key = "reminder") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionLabel("Recordatorio")
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text("Recibir recordatorio", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        reminderText(state),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = state.reminderEnabled,
                                    enabled = state.dueAt != null,
                                    onCheckedChange = { enabled ->
                                        onReminderChange(enabled)
                                        if (enabled &&
                                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                            !notificationsEnabled
                                        ) {
                                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    },
                                )
                            }
                            if (state.dueAt == null) {
                                Text(
                                    "Añade una fecha de vencimiento para activar el recordatorio.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else if (state.reminderEnabled && !notificationsEnabled) {
                                Text(
                                    "Las notificaciones están desactivadas. Concede el permiso para recibir el aviso.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    TextButton(onClick = {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }) { Text("Permitir notificaciones") }
                                }
                            }
                        }
                    }
                    item(key = "recurrence") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionLabel("Repetición")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                recurrenceOptions.forEach { (recurrence, label) ->
                                    FilterChip(
                                        selected = state.recurrence == recurrence,
                                        onClick = { onRecurrenceChange(recurrence) },
                                        enabled = recurrence == TaskRecurrence.NONE || state.dueAt != null,
                                        label = { Text(label) },
                                    )
                                }
                            }
                            if (state.recurrence != TaskRecurrence.NONE) {
                                Text(
                                    "Al completar esta tarea se creará automáticamente la siguiente.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                DateRow(
                                    label = "Repetir hasta (opcional)",
                                    timestamp = state.recurrenceEndAt,
                                    onPick = { showRecurrenceEndPicker = true },
                                    onClear = { onRecurrenceEndChange(null) },
                                )
                            } else if (state.dueAt == null) {
                                Text(
                                    "Añade una fecha de vencimiento para repetir la tarea.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    item(key = "organization") {
                        OrganizationFields(
                            folderId = state.folderId,
                            categoryId = state.categoryId,
                            folders = state.folders,
                            categories = state.categories,
                            onFolderChange = onFolderChange,
                            onCategoryChange = onCategoryChange,
                        )
                    }
                    item(key = "labels") {
                        LabelsSection(
                            selected = state.labels,
                            available = state.availableLabels,
                            onAdd = onAddLabel,
                            onRemove = onRemoveLabel,
                        )
                    }
                    item(key = "subtasks") {
                        SubtasksSection(
                            subtasks = state.subtasks,
                            onAdd = onAddSubtask,
                            onTextChange = onSubtaskTextChange,
                            onCompletedChange = onSubtaskCompletedChange,
                            onRemove = onRemoveSubtask,
                            onMove = onMoveSubtask,
                        )
                    }
                    item(key = "actions") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = onSaveAndExit,
                                enabled = !state.saving,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (state.saving) {
                                    CircularProgressIndicator(
                                        Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Guardando…")
                                } else {
                                    Text("Guardar tarea")
                                }
                            }
                            if (state.taskId != null) {
                                if (state.recurrence != TaskRecurrence.NONE) {
                                    OutlinedButton(
                                        onClick = { showStopRecurrence = true },
                                        enabled = !state.saving,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Outlined.Repeat, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Finalizar repetición")
                                    }
                                }
                                OutlinedButton(
                                    onClick = { showDelete = true },
                                    enabled = !state.saving,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                ) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Eliminar tarea")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showStartPicker) {
        TaskDatePickerDialog(
            title = "Fecha de inicio",
            initial = state.startAt,
            onDismiss = { showStartPicker = false },
            onSelect = {
                showStartPicker = false
                onStartDateChange(it)
            },
        )
    }
    if (showDuePicker) {
        TaskDatePickerDialog(
            title = "Fecha de vencimiento",
            initial = state.dueAt,
            onDismiss = { showDuePicker = false },
            onSelect = {
                showDuePicker = false
                onDueDateChange(it)
            },
        )
    }
    if (showRecurrenceEndPicker) {
        TaskDatePickerDialog(
            title = "Repetir hasta",
            initial = state.recurrenceEndAt ?: state.dueAt,
            onDismiss = { showRecurrenceEndPicker = false },
            onSelect = {
                showRecurrenceEndPicker = false
                onRecurrenceEndChange(it)
            },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("¿Eliminar tarea?") },
            text = { Text("Se eliminarán la tarea y sus subtareas. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    onDelete()
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancelar") } },
        )
    }
    if (showStopRecurrence) {
        AlertDialog(
            onDismissRequest = { showStopRecurrence = false },
            title = { Text("¿Finalizar repetición?") },
            text = { Text("No se crearán más ocurrencias de esta tarea. Las existentes se conservarán.") },
            confirmButton = {
                TextButton(onClick = {
                    showStopRecurrence = false
                    onStopRecurrence()
                }) { Text("Finalizar") }
            },
            dismissButton = { TextButton(onClick = { showStopRecurrence = false }) { Text("Cancelar") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelsSection(
    selected: List<String>,
    available: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val submit = {
        onAdd(input)
        input = ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Etiquetas")
        if (selected.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                selected.forEach { label ->
                    InputChip(
                        selected = true,
                        onClick = { onRemove(label) },
                        label = { Text(label) },
                        trailingIcon = {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Quitar etiqueta $label",
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { if (it.length <= 30) input = it },
                modifier = Modifier.weight(1f).testTag("task_label_input"),
                label = { Text("Nueva etiqueta") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            IconButton(onClick = submit, enabled = input.isNotBlank() && selected.size < 20) {
                Icon(Icons.Outlined.Add, contentDescription = "Añadir etiqueta")
            }
        }
        val suggestions = available.filterNot { it in selected }.take(8)
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { label ->
                    SuggestionChip(onClick = { onAdd(label) }, label = { Text(label) })
                }
            }
        }
    }
}

@Composable
private fun SubtasksSection(
    subtasks: List<TaskSubtaskRow>,
    onAdd: () -> Unit,
    onTextChange: (Long, String) -> Unit,
    onCompletedChange: (Long, Boolean) -> Unit,
    onRemove: (Long) -> Unit,
    onMove: (Long, Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                SectionLabel("Subtareas")
                Text(
                    if (subtasks.isEmpty()) "Divide la tarea en pasos pequeños."
                    else "${subtasks.count { it.completed }}/${subtasks.size} completadas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onAdd, modifier = Modifier.testTag("subtask_add")) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Añadir")
            }
        }
        subtasks.forEachIndexed { index, row ->
            SubtaskRow(
                row = row,
                index = index,
                canMoveUp = index > 0,
                canMoveDown = index < subtasks.lastIndex,
                onTextChange = { onTextChange(row.key, it) },
                onCompletedChange = { onCompletedChange(row.key, it) },
                onRemove = { onRemove(row.key) },
                onMove = { direction -> onMove(row.key, direction) },
            )
        }
    }
}

@Composable
private fun SubtaskRow(
    row: TaskSubtaskRow,
    index: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onTextChange: (String) -> Unit,
    onCompletedChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = row.completed, onCheckedChange = onCompletedChange)
        OutlinedTextField(
            value = row.text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f).testTag("subtask_$index"),
            placeholder = { Text("Subtarea") },
            singleLine = true,
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Opciones de la subtarea")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Subir") },
                    enabled = canMoveUp,
                    onClick = { menuOpen = false; onMove(-1) },
                )
                DropdownMenuItem(
                    text = { Text("Bajar") },
                    enabled = canMoveDown,
                    onClick = { menuOpen = false; onMove(1) },
                )
                DropdownMenuItem(
                    text = { Text("Eliminar") },
                    onClick = { menuOpen = false; onRemove() },
                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun DateRow(
    label: String,
    timestamp: Long?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    timestamp?.let(::dateText) ?: "Sin fecha",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (timestamp != null) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Outlined.Close, contentDescription = "Quitar $label")
                }
            }
            TextButton(onClick = onPick) { Text(if (timestamp == null) "Añadir" else "Cambiar") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDatePickerDialog(
    title: String,
    initial: Long?,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial?.let(::localDateToPickerMillis)
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { onSelect(pickerMillisToLocalDate(it)) }
                },
                enabled = pickerState.selectedDateMillis != null,
            ) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) {
        DatePicker(
            state = pickerState,
            title = { Text(title, modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
            headline = null,
            showModeToggle = false,
        )
    }
}

private fun notificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED)

private fun reminderText(state: TaskEditorUiState): String {
    if (state.dueAt == null) return "Sin fecha de vencimiento"
    if (!state.reminderEnabled) return "Recordatorio desactivado"
    val reminderAt = taskReminderAt(state.dueAt, state.dueHasTime)
    return "Aviso: ${dateTimeText(reminderAt)}"
}

private fun dateText(timestamp: Long): String =
    AndroidDateFormat.getDateInstance(AndroidDateFormat.MEDIUM, editorTaskLocale)
        .format(Date(timestamp))

private fun timeText(timestamp: Long?): String =
    timestamp?.let {
        AndroidDateFormat.getTimeInstance(AndroidDateFormat.SHORT, editorTaskLocale).format(Date(it))
    }.orEmpty()

private fun dateTimeText(timestamp: Long?): String =
    timestamp?.let {
        AndroidDateFormat.getDateTimeInstance(
            AndroidDateFormat.MEDIUM,
            AndroidDateFormat.SHORT,
            editorTaskLocale,
        ).format(Date(it))
    }.orEmpty()

private fun timeLabel(timestamp: Long): String =
    AndroidDateFormat.getTimeInstance(AndroidDateFormat.SHORT, editorTaskLocale).format(Date(timestamp))

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

private fun localDateToPickerMillis(timestamp: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = timestamp }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun pickerMillisToLocalDate(timestamp: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = timestamp }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private val statusOptions = listOf(
    TaskStatus.PENDING to "Pendiente",
    TaskStatus.IN_PROGRESS to "En curso",
    TaskStatus.COMPLETED to "Completada",
)

private val recurrenceOptions = listOf(
    TaskRecurrence.NONE to "Nunca",
    TaskRecurrence.DAILY to "Diaria",
    TaskRecurrence.WEEKLY to "Semanal",
    TaskRecurrence.MONTHLY to "Mensual",
)
