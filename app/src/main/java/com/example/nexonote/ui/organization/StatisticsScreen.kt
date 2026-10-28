package com.example.nexonote.ui.organization

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexonote.domain.model.ActivityBucket
import com.example.nexonote.domain.model.OrganizationStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsRoute(model: StatisticsViewModel, onBack: () -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estadísticas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> OrganizationLoading()
                state.error != null -> OrganizationError(state.error.orEmpty(), model::retry)
                else -> StatisticsContent(state, model::selectPeriod)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatisticsContent(
    state: StatisticsUiState,
    onPeriod: (ActivityPeriod) -> Unit,
) {
    val stats = state.stats
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.testTag("statistics_screen"),
    ) {
        item(key = "totals") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatTile("Notas", stats.totalNotes, Icons.AutoMirrored.Outlined.StickyNote2, Modifier.weight(1f))
                StatTile("Pendientes", stats.pendingTasks, Icons.Outlined.CheckCircle, Modifier.weight(1f))
                StatTile("Completadas", stats.completedTasks, Icons.Outlined.CheckCircle, Modifier.weight(1f))
                StatTile("Vencidas", stats.overdueTasks, Icons.Outlined.ErrorOutline, Modifier.weight(1f))
                StatTile("Tablas", stats.tables, Icons.Outlined.TableChart, Modifier.weight(1f))
                StatTile("Registros", stats.records, Icons.Outlined.TableChart, Modifier.weight(1f))
                StatTile("Eventos", stats.scheduledEvents, Icons.Outlined.CalendarMonth, Modifier.weight(1f))
                StatTile("Próximos 30 días", stats.upcomingEvents, Icons.Outlined.CalendarMonth, Modifier.weight(1f))
            }
        }
        item(key = "completion") {
            Card(shape = RoundedCornerShape(20.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { stats.completionRate },
                            modifier = Modifier.size(88.dp),
                            strokeWidth = 9.dp,
                        )
                        Text(
                            "${stats.completionPercent}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Cumplimiento de tareas", style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(
                            progress = { stats.completionRate },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${stats.completedTasks} de ${stats.totalTasks} tareas completadas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (stats.overdueTasks > 0) {
                            Text(
                                "${stats.overdueTasks} tarea(s) vencida(s) sin completar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
        item(key = "notesPeriod") {
            ChartCard(
                title = "Notas creadas",
                subtitle = "${stats.notesLast7Days} en 7 días · ${stats.notesLast30Days} en 30 días",
                buckets = stats.noteDaily,
                color = Color(0xFF6A5AA8),
            )
        }
        item(key = "activityHeader") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Actividad", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActivityPeriod.entries.forEach { value ->
                        FilterChip(
                            selected = state.period == value,
                            onClick = { onPeriod(value) },
                            label = { Text(value.label) },
                        )
                    }
                }
                Text(
                    when (state.period) {
                        ActivityPeriod.DAILY -> "Últimos 7 días"
                        ActivityPeriod.WEEKLY -> "Últimas 8 semanas"
                        ActivityPeriod.MONTHLY -> "Últimos 6 meses"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "activityChart") {
            val buckets = when (state.period) {
                ActivityPeriod.DAILY -> stats.activityDaily
                ActivityPeriod.WEEKLY -> stats.activityWeekly
                ActivityPeriod.MONTHLY -> stats.activityMonthly
            }
            ChartCard(
                title = "Acciones registradas",
                subtitle = "${buckets.sumOf { it.value }} en total",
                buckets = buckets,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        item(key = "records") {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Registros de tablas", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "${stats.records} registro(s) en ${stats.tables} tabla(s)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "${stats.recordsLast30Days} creado(s) en los últimos 30 días",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (stats.isEmpty) {
            item(key = "empty") {
                OrganizationEmpty(
                    title = "Aún no hay datos",
                    message = "Cuando crees notas, tareas, tablas o eventos verás aquí estadísticas reales.",
                )
            }
        }
        item(key = "footnote") {
            Text(
                "Las cifras se calculan con tus datos locales; no se usan valores simulados.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    subtitle: String,
    buckets: List<ActivityBucket>,
    color: Color,
) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (buckets.isEmpty()) {
                Text("Sin datos en este periodo.", style = MaterialTheme.typography.bodySmall)
            } else {
                BarChart(buckets = buckets, color = color, title = title)
            }
        }
    }
}

@Composable
private fun BarChart(
    buckets: List<ActivityBucket>,
    color: Color,
    title: String,
) {
    val max = buckets.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
    val description = buckets.joinToString(", ") { "${it.label}: ${it.value}" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .semantics { contentDescription = "$title. $description" },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        buckets.forEach { bucket ->
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (bucket.value > 0) {
                    Text(
                        bucket.value.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth(0.55f)
                        .height((2f + 116f * bucket.value / max).dp)
                        .background(color, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    bucket.label,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
