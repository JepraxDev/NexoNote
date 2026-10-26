package com.example.nexonote.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexonote.domain.model.ActivityKind
import com.example.nexonote.domain.model.RecentTableRow
import com.example.nexonote.domain.model.Task
import com.example.nexonote.domain.model.isTaskOverdue
import com.example.nexonote.navigation.Destination
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val spanishLocale = Locale.forLanguageTag("es")

@Composable
fun DashboardRoute(
    model: DashboardViewModel,
    navigate: (Destination) -> Unit,
    create: (Destination) -> Unit,
    openNote: (Long) -> Unit,
    openTask: (Long) -> Unit,
    openTable: (Long) -> Unit,
) {
    val state by model.uiState.collectAsStateWithLifecycle()
    DashboardScreen(
        state,
        navigate,
        create,
        openNote,
        openTask,
        openTable,
        model::retry,
        model::saveName,
        model::clearNameError,
    )
}

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    navigate: (Destination) -> Unit,
    create: (Destination) -> Unit,
    openNote: (Long) -> Unit,
    openTask: (Long) -> Unit,
    openTable: (Long) -> Unit,
    retry: () -> Unit,
    saveName: (String, () -> Unit) -> Unit,
    clearNameError: () -> Unit,
) {
    var editingName by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val fontScale = LocalDensity.current.fontScale
        val available = maxWidth.value.coerceAtMost(1120f) / fontScale
        val columns = when { available >= 900 -> 4; available >= 360 -> 2; else -> 1 }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.widthIn(max = 1120.dp).fillMaxSize().testTag("dashboard_grid"),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Preparando tu espacio…", style = MaterialTheme.typography.titleLarge)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            } else if (state.error != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle("No se pudo cargar el dashboard")
                            Text(state.error)
                            Button(onClick = retry) { Text("Reintentar") }
                        }
                    }
                }
            } else {
                item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val hour = Calendar.getInstance().apply { timeInMillis = state.now }.get(Calendar.HOUR_OF_DAY)
                            Text(
                                greeting(hour) + if (state.displayName.isBlank()) "" else ", ${state.displayName}",
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Text(DateFormat.getDateInstance(DateFormat.FULL, spanishLocale).format(Date(state.now)))
                            Text("Tus ideas y pendientes, en un solo lugar.")
                            TextButton(onClick = {
                                name = state.displayName
                                clearNameError()
                                editingName = true
                            }) {
                                Icon(Icons.Outlined.Edit, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (state.displayName.isBlank()) "Configurar mi nombre" else "Cambiar nombre")
                            }
                        }
                    }
                }
                item(key = "notes") {
                    StatisticCard("Notas", state.noteCount, Destination.NOTES.icon) { navigate(Destination.NOTES) }
                }
                item(key = "pending") {
                    StatisticCard("Tareas pendientes", state.pendingCount, Destination.TASKS.icon) { navigate(Destination.TASKS) }
                }
                item(key = "completed") {
                    StatisticCard("Tareas completadas", state.completedCount, Icons.Outlined.CheckCircle) { navigate(Destination.TASKS) }
                }
                item(key = "tables") {
                    StatisticCard("Tablas", state.tableCount, Destination.TABLES.icon) { navigate(Destination.TABLES) }
                }
                item(key = "progress", span = { GridItemSpan(maxLineSpan) }) {
                    TaskProgressCard(state) { navigate(Destination.TASKS) }
                }
                item(key = "actions", span = { GridItemSpan(maxLineSpan) }) { QuickActions(create) }
                item(key = "recentNotes", span = { GridItemSpan(if (columns == 4) 2 else maxLineSpan) }) {
                    EntryListCard(
                        title = "Notas recientes",
                        entries = state.recentNotes,
                        emptyMessage = "Aún no tienes notas. Usa Nueva nota para capturar tu primera idea.",
                        icon = Destination.NOTES.icon,
                        titleOf = { it.displayTitle },
                        subtitle = { note ->
                            note.modifiedAt?.let { "Modificada: ${formatDate(it)}" }
                                ?: "Fecha de modificación no registrada"
                        },
                        onOpenItem = { openNote(it.id) },
                        onOpenModule = { navigate(Destination.NOTES) },
                    )
                }
                item(key = "upcomingTasks", span = { GridItemSpan(if (columns == 4) 2 else maxLineSpan) }) {
                    EntryListCard(
                        title = "Tareas próximas",
                        entries = state.upcomingTasks,
                        emptyMessage = if (state.taskCount == 0) "No hay tareas todavía. Crea una desde las acciones rápidas." else "Todo al día: no tienes tareas pendientes.",
                        icon = Destination.TASKS.icon,
                        titleOf = { it.displayTitle },
                        subtitle = { entry -> "${taskDeadline(entry, state.now)} · ${entry.priority.label}" },
                        onOpenItem = { openTask(it.id) },
                        onOpenModule = { navigate(Destination.TASKS) },
                    )
                }
                item(key = "recentTableRows", span = { GridItemSpan(if (columns == 4) 2 else maxLineSpan) }) {
                    EntryListCard<RecentTableRow>(
                        title = "Registros recientes",
                        entries = state.recentTableRows,
                        emptyMessage = "Aún no hay registros en tus tablas. Abre una tabla para crear el primero.",
                        icon = Destination.TABLES.icon,
                        titleOf = { it.title.ifBlank { "Registro sin título" } },
                        subtitle = { row ->
                            val modified = row.modifiedAt?.let { " · ${formatDate(it)}" }.orEmpty()
                            row.tableName + modified
                        },
                        onOpenItem = { openTable(it.tableId) },
                        onOpenModule = { navigate(Destination.TABLES) },
                    )
                }
                if (state.recentActivity.isNotEmpty()) {
                    item(key = "activity", span = { GridItemSpan(maxLineSpan) }) {
                        ActivityCard(
                            activities = state.recentActivity,
                            timeLabel = ::formatDate,
                            onOpen = { activity ->
                                when (activity.kind) {
                                    ActivityKind.NOTE_CREATED -> openNote(activity.entryId)
                                    ActivityKind.TABLE_CREATED -> openTable(activity.entryId)
                                    else -> openTask(activity.entryId)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    if (editingName) {
        AlertDialog(
            onDismissRequest = { if (!state.savingName) editingName = false },
            title = { Text("¿Cómo te llamas?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 40) name = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        enabled = !state.savingName,
                        supportingText = { Text("${name.length}/40 · Opcional") },
                    )
                    state.nameError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !state.savingName, onClick = { saveName(name) { editingName = false } }) {
                    Text(if (state.savingName) "Guardando…" else "Guardar")
                }
            },
            dismissButton = {
                TextButton(enabled = !state.savingName, onClick = { editingName = false }) { Text("Cancelar") }
            },
        )
    }
}

internal fun greeting(hour: Int): String = when (hour) {
    in 6..11 -> "Buenos días"
    in 12..19 -> "Buenas tardes"
    else -> "Buenas noches"
}

private fun formatDate(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, spanishLocale).format(Date(timestamp))

private fun taskDeadline(task: Task, now: Long): String {
    val due = task.dueAt ?: return "Sin vencimiento"
    val prefix = if (isTaskOverdue(task, now)) "Vencida" else "Vence"
    val formatted = if (task.dueHasTime) {
        formatDate(due)
    } else {
        DateFormat.getDateInstance(DateFormat.MEDIUM, spanishLocale).format(Date(due))
    }
    return "$prefix: $formatted"
}
