package com.example.nexonote.ui.tables

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.nexonote.domain.model.ColumnType
import com.example.nexonote.domain.model.CustomColumn
import com.example.nexonote.domain.model.TableRow
import com.example.nexonote.domain.model.normalizeCellValue
import java.util.Calendar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CellEditorDialog(
    column: CustomColumn,
    row: TableRow,
    onDismiss: () -> Unit,
    onCommit: (String?) -> Unit,
) {
    var draft by remember(row.id, column.id) { mutableStateOf(row.value(column.id).orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isDateTime = column.type == ColumnType.DATETIME

    fun validateAndCommit() {
        val result = runCatching {
            normalizeCellValue(column.type, draft, column.options, column.required)
        }
        result.onSuccess { onCommit(it) }
            .onFailure { error = it.message ?: "El valor no es válido." }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(column.displayName) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (column.required) {
                    Text(
                        "Campo obligatorio",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                when (column.type) {
                    ColumnType.CHECKBOX -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = draft == "1",
                                onCheckedChange = { draft = if (it) "1" else "0" },
                            )
                            Text("Marcado")
                        }
                    }
                    ColumnType.SELECT, ColumnType.STATUS -> {
                        OptionDropdown(
                            label = "Opción",
                            options = column.options,
                            selectedId = draft.toLongOrNull(),
                            enabled = column.options.isNotEmpty(),
                            onSelect = { draft = it?.id?.toString().orEmpty() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (column.options.isEmpty()) {
                            Text(
                                "Esta columna todavía no tiene opciones.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    ColumnType.MULTISELECT -> {
                        val selectedIds = draft.split(',')
                            .mapNotNull(String::toLongOrNull)
                            .toSet()
                        if (column.options.isEmpty()) {
                            Text(
                                "Esta columna todavía no tiene opciones.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                column.options.forEach { option ->
                                    val selected = option.id in selectedIds
                                    OptionChip(
                                        option = option,
                                        selected = selected,
                                        onClick = {
                                            val next = if (selected) selectedIds - option.id
                                            else selectedIds + option.id
                                            draft = next.sorted().joinToString(",")
                                        },
                                    )
                                }
                            }
                        }
                    }
                    ColumnType.DATE, ColumnType.DATETIME -> {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                draft.toLongOrNull()
                                    ?.let { formatDateValue(it, withTime = isDateTime) }
                                    ?: "Elegir fecha"
                            )
                        }
                        if (draft.isNotBlank()) {
                            TextButton(onClick = { draft = "" }) { Text("Vaciar fecha") }
                        }
                    }
                    ColumnType.LONG_TEXT -> {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = {
                                draft = it
                                error = null
                            },
                            label = { Text("Valor") },
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier.fillMaxWidth().testTag("cell_value"),
                        )
                    }
                    else -> {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = {
                                draft = it
                                error = null
                            },
                            label = { Text("Valor") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = when (column.type) {
                                    ColumnType.INTEGER -> KeyboardType.Number
                                    ColumnType.DECIMAL, ColumnType.CURRENCY -> KeyboardType.Decimal
                                    ColumnType.EMAIL -> KeyboardType.Email
                                    ColumnType.PHONE -> KeyboardType.Phone
                                    ColumnType.URL -> KeyboardType.Uri
                                    else -> KeyboardType.Text
                                }
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("cell_value"),
                        )
                    }
                }
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { validateAndCommit() },
                modifier = Modifier.testTag("cell_apply"),
            ) { Text("Aplicar") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        draft = ""
                        validateAndCommit()
                    },
                ) { Text("Vaciar") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )

    if (showDatePicker) {
        TableDatePickerDialog(
            title = if (isDateTime) "Fecha y hora" else "Fecha",
            initial = draft.toLongOrNull(),
            onDismiss = { showDatePicker = false },
            onSelect = { dateMillis ->
                showDatePicker = false
                if (isDateTime) {
                    val current = draft.toLongOrNull()?.let { value ->
                        Calendar.getInstance().apply { timeInMillis = value }
                    } ?: Calendar.getInstance()
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            draft = combineDateAndTime(dateMillis, hour, minute).toString()
                            error = null
                        },
                        current.get(Calendar.HOUR_OF_DAY),
                        current.get(Calendar.MINUTE),
                        DateFormat.is24HourFormat(context),
                    ).show()
                } else {
                    draft = dateMillis.toString()
                    error = null
                }
            },
        )
    }
}

private fun combineDateAndTime(dateMillis: Long, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = dateMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

@Composable
fun CellValuePreview(
    column: CustomColumn,
    row: TableRow,
    modifier: Modifier = Modifier,
) {
    val value = row.value(column.id)
    when (column.type) {
        ColumnType.SELECT, ColumnType.STATUS -> {
            val option = column.options.firstOrNull { it.id.toString() == value }
            if (option == null) EmptyOptionChip() else OptionChip(option, modifier)
        }
        ColumnType.MULTISELECT -> {
            val selected = value.orEmpty().split(',')
                .mapNotNull(String::toLongOrNull)
                .mapNotNull { id -> column.options.firstOrNull { it.id == id } }
            if (selected.isEmpty()) {
                EmptyOptionChip()
            } else {
                FlowRow(
                    modifier = modifier,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    selected.take(2).forEach { OptionChip(it) }
                    if (selected.size > 2) {
                        Text(
                            "+${selected.size - 2}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 2.dp, top = 3.dp),
                        )
                    }
                }
            }
        }
        ColumnType.DATE -> {
            Text(
                value?.toLongOrNull()?.let { formatDateValue(it) }.orEmpty(),
                modifier = modifier,
            )
        }
        ColumnType.DATETIME -> {
            Text(
                value?.toLongOrNull()?.let { formatDateValue(it, withTime = true) }.orEmpty(),
                modifier = modifier,
            )
        }
        ColumnType.CHECKBOX -> {
            Text(if (value == "1") "Sí" else "No", modifier = modifier)
        }
        else -> {
            Text(
                text = value.orEmpty(),
                modifier = modifier,
                maxLines = 2,
            )
        }
    }
}
