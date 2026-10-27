package com.example.nexonote.ui.calendar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.nexonote.domain.model.*
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

fun calendarDateLabel(time: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(time))
fun calendarTimeLabel(time: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarDateDialog(initial: Long, onDismiss: () -> Unit, onDate: (Long) -> Unit) {
    val local = eventCalendar(initial)
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear(); set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
    val state = rememberDatePickerState(initialSelectedDateMillis = utc)
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = {
        TextButton(onClick = {
            state.selectedDateMillis?.let { value ->
                val date = eventCalendar(value, "UTC")
                val result = eventCalendar(initial).apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.YEAR, date.get(Calendar.YEAR)); set(Calendar.MONTH, date.get(Calendar.MONTH))
                    set(Calendar.DAY_OF_MONTH, date.get(Calendar.DAY_OF_MONTH))
                }
                onDate(result.timeInMillis)
            }
        }) { Text("Aceptar") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }) {
        DatePicker(state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventTimeDialog(initial: Long, onDismiss: () -> Unit, onTime: (Long) -> Unit) {
    val context = LocalContext.current
    val c = eventCalendar(initial)
    val time = rememberTimePickerState(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE),
        android.text.format.DateFormat.is24HourFormat(context))
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Hora") }, text = { TimePicker(time) },
        confirmButton = { TextButton(onClick = {
            onTime(eventCalendar(initial).apply {
                set(Calendar.HOUR_OF_DAY, time.hour); set(Calendar.MINUTE, time.minute)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis)
        }) { Text("Aceptar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun EventEditor(
    initial: EventInput, editing: Boolean, series: Boolean, busy: Boolean, error: String?,
    onDismiss: () -> Unit, onSave: (EventInput) -> Unit, onDelete: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var description by rememberSaveable { mutableStateOf(initial.description) }
    var start by rememberSaveable { mutableLongStateOf(initial.startAt) }
    var end by rememberSaveable { mutableLongStateOf(if (initial.allDay) initial.endAt - 1 else initial.endAt) }
    var allDay by rememberSaveable { mutableStateOf(initial.allDay) }
    var category by rememberSaveable { mutableStateOf(initial.category) }
    var color by rememberSaveable { mutableStateOf(initial.color) }
    var recurrence by rememberSaveable { mutableStateOf(initial.recurrence.name) }
    var until by rememberSaveable { mutableStateOf(initial.repeatUntil) }
    var reminder by rememberSaveable { mutableStateOf(initial.reminderMinutes) }
    var picker by rememberSaveable { mutableStateOf("") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> permissionDenied = !granted }
    val input = EventInput(title, if (allDay) calendarDay(start) else start,
        if (allDay) calendarAdd(calendarDay(end), Calendar.DAY_OF_YEAR, 1) else end,
        description, allDay, color, category,
        if (allDay) TimeZone.getDefault().id else initial.zoneId,
        if (series || !editing) TaskRecurrence.valueOf(recurrence) else TaskRecurrence.NONE,
        until, reminder)
    val validation = validateEvent(input)
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 600.dp).fillMaxWidth().fillMaxHeight(0.94f), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(20.dp)) {
                Text(if (editing) "Editar evento" else "Nuevo evento", style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (series && editing) Text("Toda la serie. Al guardar se restablecen sus excepciones.")
                    OutlinedTextField(title, { title = it }, label = { Text("Título obligatorio") }, modifier = Modifier.fillMaxWidth().testTag("event_title"), singleLine = true)
                    OutlinedTextField(description, { description = it }, label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Row { Checkbox(allDay, { allDay = it }); Text("Día completo", Modifier.padding(top = 12.dp)) }
                    Text("Inicio")
                    Row { TextButton(onClick = { picker = "startDate" }) { Text(calendarDateLabel(start)) }
                        if (!allDay) TextButton(onClick = { picker = "startTime" }) { Text(calendarTimeLabel(start)) } }
                    Text("Final (inclusive para día completo)")
                    Row { TextButton(onClick = { picker = "endDate" }) { Text(calendarDateLabel(end)) }
                        if (!allDay) TextButton(onClick = { picker = "endTime" }) { Text(calendarTimeLabel(end)) } }
                    Text("Zona de la serie: ${initial.zoneId}. Horas mostradas en ${TimeZone.getDefault().id}.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(category, { category = it }, label = { Text("Categoría opcional") }, modifier = Modifier.fillMaxWidth())
                    ChoiceMenu("Color", color, listOf("#6750A4", "#006A6A", "#B3261E", "#775A00", "#0061A4")) { color = it }
                    if (series || !editing) {
                        ChoiceMenu("Repetir", recurrenceLabel(TaskRecurrence.valueOf(recurrence)), TaskRecurrence.entries.map(::recurrenceLabel)) {
                            recurrence = TaskRecurrence.entries.first { value -> recurrenceLabel(value) == it }.name
                        }
                        if (recurrence != TaskRecurrence.NONE.name) {
                            TextButton(onClick = { picker = "until" }) { Text(until?.let { "Hasta ${calendarDateLabel(it)}" } ?: "Sin fecha final: configurar") }
                            if (until != null) TextButton(onClick = { until = null }) { Text("Quitar fecha final") }
                        }
                    }
                    val reminders = listOf<Int?>(null) + EVENT_REMINDER_MINUTES
                    ChoiceMenu("Recordatorio", reminderLabel(reminder), reminders.map(::reminderLabel)) { label ->
                        reminder = reminders.first { reminderLabel(it) == label }
                        if (reminder != null && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    Text("Android puede retrasar los avisos para ahorrar batería. No son alarmas exactas.", style = MaterialTheme.typography.bodySmall)
                    if (permissionDenied) Text("Notificaciones desactivadas. Puedes habilitarlas en Ajustes de Android; el evento se guardará igualmente.")
                    (error ?: validation)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (editing) TextButton(onClick = { confirmDelete = true }, enabled = !busy) { Text("Eliminar evento", color = MaterialTheme.colorScheme.error) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") }
                    Button(onClick = { onSave(input) }, enabled = !busy && validation == null, modifier = Modifier.testTag("event_save")) { Text("Guardar") }
                }
            }
        }
    }
    if (picker.endsWith("Date") || picker == "until") CalendarDateDialog(
        when (picker) { "startDate" -> start; "endDate" -> end; else -> until ?: start }, { picker = "" }, {
            when (picker) { "startDate" -> start = it; "endDate" -> end = it; else -> until = it }; picker = ""
        })
    if (picker.endsWith("Time")) EventTimeDialog(if (picker == "startTime") start else end, { picker = "" }, {
        if (picker == "startTime") start = it else end = it; picker = ""
    })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("¿Eliminar evento?") },
        text = { Text(if (series) "Se eliminará toda la serie y sus excepciones." else "Se eliminará esta ocurrencia.") },
        confirmButton = { TextButton(onClick = onDelete, enabled = !busy) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
}

@Composable
private fun ChoiceMenu(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: $selected") }
        DropdownMenu(expanded, { expanded = false }) { options.forEach { option ->
            DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
        } }
    }
}
fun recurrenceLabel(value: TaskRecurrence): String = when (value) {
    TaskRecurrence.NONE -> "No repetir"; TaskRecurrence.DAILY -> "Diariamente"
    TaskRecurrence.WEEKLY -> "Semanalmente"; TaskRecurrence.MONTHLY -> "Mensualmente"
}
fun reminderLabel(minutes: Int?): String = when (minutes) {
    null -> "Sin aviso"; 0 -> "Al momento"; 60 -> "1 hora antes"; 1440 -> "1 día antes"; else -> "$minutes minutos antes"
}
