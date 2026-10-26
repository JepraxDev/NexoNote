package com.example.nexonote.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.nexonote.domain.model.Activity
import com.example.nexonote.domain.model.ActivityKind
import com.example.nexonote.navigation.Destination

@Composable
internal fun StatisticCard(title: String, count: Int, icon: ImageVector, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(count.toString(), style = MaterialTheme.typography.headlineLarge)
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
internal fun TaskProgressCard(state: DashboardUiState, onClick: () -> Unit) {
    val animatedProgress by animateFloatAsState(state.progress, label = "taskProgress")
    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Tu progreso")
            Text("${state.percentage}%", style = MaterialTheme.typography.headlineMedium)
            LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth())
            Text(
                if (state.taskCount == 0) "Aún no hay tareas. Crea la primera para empezar."
                else "${state.completedCount} de ${state.taskCount} tareas completadas",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onClick) { Text("Ver tareas") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickActions(onCreate: (Destination) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Acciones rápidas")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Destination.NOTES to "Nueva nota", Destination.TASKS to "Nueva tarea", Destination.TABLES to "Nueva tabla").forEach { (destination, label) ->
                FilledTonalButton(onClick = { onCreate(destination) }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(label)
                }
            }
        }
    }
}

@Composable
internal fun <T> EntryListCard(
    title: String,
    entries: List<T>,
    emptyMessage: String,
    icon: ImageVector,
    titleOf: (T) -> String,
    subtitle: (T) -> String,
    onOpenItem: (T) -> Unit,
    onOpenModule: () -> Unit,
) {
    OutlinedCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(title)
            if (entries.isEmpty()) {
                Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                entries.forEach { entry ->
                    Surface(onClick = { onOpenItem(entry) }, shape = RoundedCornerShape(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(titleOf(entry), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(subtitle(entry), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            TextButton(onClick = onOpenModule) { Text("Abrir módulo") }
        }
    }
}

@Composable
internal fun ActivityCard(
    activities: List<Activity>,
    timeLabel: (Long) -> String,
    onOpen: (Activity) -> Unit,
) {
    OutlinedCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Actividad reciente")
            activities.forEach { activity ->
                val icon = when (activity.kind) {
                    ActivityKind.TASK_COMPLETED -> Icons.Outlined.CheckCircle
                    ActivityKind.TASK_REOPENED -> Icons.Outlined.History
                    else -> Icons.Outlined.Add
                }
                Surface(onClick = { onOpen(activity) }, shape = RoundedCornerShape(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                activity.title ?: "Elemento no disponible",
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                activityDescription(activity.kind),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                timeLabel(activity.occurredAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun activityDescription(kind: ActivityKind): String = when (kind) {
    ActivityKind.NOTE_CREATED -> "Creaste una nota"
    ActivityKind.TASK_CREATED -> "Creaste una tarea"
    ActivityKind.TABLE_CREATED -> "Creaste una tabla"
    ActivityKind.TASK_COMPLETED -> "Completaste una tarea"
    ActivityKind.TASK_REOPENED -> "Reabriste una tarea"
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}
