package com.example.nexonote.ui.calendar

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexonote.domain.model.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun activityColor(hex: String): Color = runCatching { Color(hex.toColorInt()) }.getOrDefault(Color(0xFF6750A4))
private fun dayItems(items: List<CalendarActivity>, day: Long): List<CalendarActivity> {
    val from = calendarDay(day)
    val until = calendarAdd(from, Calendar.DAY_OF_YEAR, 1)
    return items.filter { it.startAt < until && it.endAt.coerceAtLeast(it.startAt + 1) > from }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarRoute(model: CalendarViewModel, onBack: () -> Unit, openTask: (Long) -> Unit, initialDate: Long? = null) {
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.operationError.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingStart by rememberSaveable { mutableLongStateOf(0) }
    var editingEnd by rememberSaveable { mutableLongStateOf(0) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var scope by rememberSaveable { mutableStateOf<String?>(null) }
    var datePicker by rememberSaveable { mutableStateOf(false) }
    var initialDateApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialDate) {
        if (!initialDateApplied && initialDate != null) {
            model.select(initialDate)
            initialDateApplied = true
        }
    }
    val editedEvent = state.events.firstOrNull { it.id == editingId }
    val target = editedEvent?.let {
        EventOccurrence(it, editingStart, editingEnd)
    }
    val open: (CalendarActivity) -> Unit = { item ->
        if (item.task != null) openTask(item.task.id) else item.event?.let {
            editingId = it.event.id; editingStart = it.startAt; editingEnd = it.endAt
            scope = if (it.event.parentId != null || it.event.input.recurrence != TaskRecurrence.NONE) null else "occurrence"
            model.dismissError()
        }
    }
    val dismiss: () -> Unit = { creating = false; editingId = null; scope = null }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Calendario") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") }
        }, actions = { TextButton(onClick = { model.select(System.currentTimeMillis()) }) { Text("Hoy") } })
    }, floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { creating = true; model.dismissError() },
            icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Nuevo evento") }, modifier = Modifier.testTag("new_event"))
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).testTag("calendar_screen")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                CalendarMode.entries.forEach { mode -> FilterChip(state.mode == mode, { model.mode(mode) }, label = { Text(mode.label) }) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { model.move(-1) }, modifier = Modifier.semantics { contentDescription = "Periodo anterior" }) { Text("‹") }
                TextButton(onClick = { datePicker = true }) {
                    Text(SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(state.selected)))
                }
                TextButton(onClick = { model.move(1) }, modifier = Modifier.semantics { contentDescription = "Periodo siguiente" }) { Text("›") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                CalendarFilter.entries.forEach { filter -> FilterChip(state.filter == filter, { model.filter(filter) }, label = { Text(filter.label) }) }
            }
            when {
                state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error != null -> Column(Modifier.padding(24.dp)) {
                    Text(state.error.orEmpty()); Button(onClick = model::retry) { Text("Reintentar") }
                }
                state.mode == CalendarMode.WEEK -> WeekTimeline(state, model::select, open)
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
                    if (state.mode == CalendarMode.MONTH) item { MonthGrid(state, model::select) }
                    item { Text(calendarDateLabel(state.selected), Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium) }
                    val activities = dayItems(state.activities, state.selected)
                    if (activities.isEmpty()) item { Text("No hay actividades para este día.", Modifier.padding(24.dp)) }
                    items(activities, key = { it.key }) { ActivityCard(it, { open(it) }) }
                }
            }
        }
    }
    if (datePicker) CalendarDateDialog(state.selected, { datePicker = false }, { model.select(it); datePicker = false })
    if (target != null && scope == null) AlertDialog(onDismissRequest = dismiss,
        title = { Text("Evento recurrente") }, text = { Text("¿Quieres editar esta ocurrencia o toda la serie?") },
        confirmButton = { TextButton(onClick = { scope = "series" }) { Text("Toda la serie") } },
        dismissButton = { TextButton(onClick = { scope = "occurrence" }) { Text("Esta ocurrencia") } })
    if (creating || (target != null && scope != null)) {
        val series = scope == "series"
        val defaultStart = eventCalendar(calendarDay(state.selected)).apply { set(Calendar.HOUR_OF_DAY, 9) }.timeInMillis
        val initial = if (target == null) EventInput("", defaultStart, calendarAdd(defaultStart, Calendar.HOUR_OF_DAY, 1))
        else if (series) state.events.first { it.id == (target.event.parentId ?: target.event.id) }.input
        else target.event.input.copy(startAt = target.startAt, endAt = target.endAt, recurrence = TaskRecurrence.NONE, repeatUntil = null)
        key(editingId, editingStart, scope, creating) {
            EventEditor(initial, target != null, series, busy, error,
                folders = state.folders,
                categories = state.categories,
                availableTags = state.tags,
                onDismiss = dismiss,
                onSave = { model.save(it, target, series, dismiss) },
                onDelete = { target?.let { model.delete(it, series, dismiss) } })
        }
    }
}

@Composable
private fun MonthGrid(state: CalendarUiState, select: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row {
            repeat(7) { index ->
                val date = calendarAdd(state.from, Calendar.DAY_OF_YEAR, index)
                Text(SimpleDateFormat("EE", Locale.getDefault()).format(Date(date)), Modifier.weight(1f).padding(4.dp),
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        repeat(6) { week -> Row {
            repeat(7) { weekday ->
                val day = calendarAdd(state.from, Calendar.DAY_OF_YEAR, week * 7 + weekday)
                val count = dayItems(state.activities, day).size
                val selected = calendarDay(day) == calendarDay(state.selected)
                val today = calendarDay(day) == calendarDay(state.now)
                Surface(onClick = { select(day) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                    .semantics { contentDescription = "${calendarDateLabel(day)}, $count actividades${if (today) ", hoy" else ""}" },
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = if (today) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    shape = MaterialTheme.shapes.small) {
                    Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(eventCalendar(day).get(Calendar.DAY_OF_MONTH).toString())
                        Text(if (count > 0) "• $count" else "", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        } }
    }
}

@Composable
fun ActivityCard(item: CalendarActivity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(onClick = onClick, modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.padding(12.dp)) {
            Box(Modifier.width(5.dp).height(48.dp).background(activityColor(item.color)))
            Column(Modifier.padding(start = 12.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall)
                Text(if (item.allDay) "Día completo" else "${calendarTimeLabel(item.startAt)} – ${calendarTimeLabel(item.endAt)}", style = MaterialTheme.typography.bodySmall)
                item.task?.let { task -> Text("Tarea · ${when(task.status) { TaskStatus.COMPLETED -> "Completada"; TaskStatus.IN_PROGRESS -> "En curso"; else -> "Pendiente" }} · ${task.priority.label}", style = MaterialTheme.typography.bodySmall) }
                item.event?.let { Text("Evento${it.event.input.category.takeIf(String::isNotBlank)?.let { c -> " · $c" }.orEmpty()}", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun WeekTimeline(state: CalendarUiState, select: (Long) -> Unit, open: (CalendarActivity) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val dayWidth = ((maxWidth - 48.dp) / 7).coerceAtLeast(130.dp)
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row(Modifier.padding(start = 48.dp)) {
                repeat(7) { index ->
                    val day = calendarAdd(state.from, Calendar.DAY_OF_YEAR, index)
                    Column(Modifier.width(dayWidth).padding(2.dp)) {
                        TextButton(onClick = { select(day) }) {
                            Text(SimpleDateFormat("EEE d", Locale.getDefault()).format(Date(day)),
                                color = if (calendarDay(day) == calendarDay(state.now)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        val allDay = dayItems(state.activities, day).filter { it.allDay }
                        allDay.forEach { item ->
                            Text(item.title, Modifier.fillMaxWidth().clickable { open(item) }.padding(6.dp), maxLines = 2,
                                style = MaterialTheme.typography.labelSmall, color = activityColor(item.color))
                        }
                    }
                }
            }
            Row(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 100.dp)) {
                Column(Modifier.width(48.dp)) { repeat(24) { hour -> Text("%02d:00".format(hour), Modifier.height(64.dp), style = MaterialTheme.typography.labelSmall) } }
                repeat(7) { index ->
                    val day = calendarAdd(state.from, Calendar.DAY_OF_YEAR, index)
                    val end = calendarAdd(day, Calendar.DAY_OF_YEAR, 1)
                    val lanes = calendarLanes(dayItems(state.activities, day).filter { !it.allDay }.map { it.copy(startAt = maxOf(day, it.startAt), endAt = minOf(end, it.endAt)) })
                    Box(Modifier.width(dayWidth).height((24 * 64).dp).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        repeat(24) { hour -> HorizontalDivider(Modifier.offset(y = (hour * 64).dp)) }
                        lanes.forEach { lane ->
                            val c = eventCalendar(lane.activity.startAt)
                            val top = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
                            val e = eventCalendar(lane.activity.endAt)
                            val bottom = if (lane.activity.endAt == end) 1440 else e.get(Calendar.HOUR_OF_DAY) * 60 + e.get(Calendar.MINUTE)
                            val width = dayWidth / lane.lanes
                            Surface(onClick = { open(lane.activity) }, color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.offset(x = width * lane.lane, y = (top * 64f / 60).dp)
                                    .width(width).height(((bottom - top).coerceAtLeast(30) * 64f / 60).dp)
                                    .border(2.dp, activityColor(lane.activity.color)).semantics { contentDescription = "${lane.activity.title}, ${calendarTimeLabel(lane.activity.startAt)}" }) {
                                Text(lane.activity.title, Modifier.padding(4.dp), style = MaterialTheme.typography.labelSmall, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarDashboardCard(model: CalendarViewModel, onOpen: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    OutlinedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Calendario · Próximos 7 días", style = MaterialTheme.typography.titleMedium)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (state.error != null) Text(state.error.orEmpty())
            else if (state.upcoming.isEmpty()) Text("No hay actividades próximas. Abre el calendario para crear un evento.")
            state.upcoming.forEach { Text("${calendarDateLabel(it.startAt)} · ${it.title}", maxLines = 2) }
            Text("Abrir calendario", color = MaterialTheme.colorScheme.primary)
        }
    }
}
