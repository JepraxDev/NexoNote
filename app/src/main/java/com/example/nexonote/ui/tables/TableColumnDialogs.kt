package com.example.nexonote.ui.tables

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.nexonote.domain.model.ColumnInput
import com.example.nexonote.domain.model.ColumnOption
import com.example.nexonote.domain.model.ColumnType
import com.example.nexonote.domain.model.CustomColumn
import com.example.nexonote.domain.model.OptionInput
import com.example.nexonote.domain.model.validateColumnInput
import com.example.nexonote.domain.model.validateOptionInput

@Composable
fun ColumnManagerDialog(
    columns: List<CustomColumn>,
    onDismiss: () -> Unit,
    onAddColumn: () -> Unit,
    onEditColumn: (CustomColumn) -> Unit,
    onManageOptions: (CustomColumn) -> Unit,
    onMoveColumn: (CustomColumn, Int) -> Unit,
    onDeleteColumn: (CustomColumn) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Columnas") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                columns.forEachIndexed { index, column ->
                    var menu by remember(column.id) { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditColumn(column) }
                            .padding(vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = columnIconVector(column.type),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(
                            Modifier.weight(1f).padding(start = 10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = column.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (column.primary) {
                                    Icon(
                                        Icons.Outlined.Lock,
                                        contentDescription = "Columna principal",
                                        modifier = Modifier.padding(start = 4.dp).size(14.dp),
                                    )
                                }
                            }
                            Text(
                                text = column.type.label + if (column.required) " · obligatoria" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "Acciones de columna")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Editar") },
                                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                    onClick = {
                                        menu = false
                                        onEditColumn(column)
                                    },
                                )
                                if (column.type.usesOptions) {
                                    DropdownMenuItem(
                                        text = { Text("Gestionar opciones") },
                                        leadingIcon = { Icon(Icons.Outlined.Sell, contentDescription = null) },
                                        onClick = {
                                            menu = false
                                            onManageOptions(column)
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Mover arriba") },
                                    leadingIcon = { Icon(Icons.Outlined.ArrowUpward, contentDescription = null) },
                                    enabled = index > 0,
                                    onClick = {
                                        menu = false
                                        onMoveColumn(column, -1)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Mover abajo") },
                                    leadingIcon = { Icon(Icons.Outlined.ArrowDownward, contentDescription = null) },
                                    enabled = index < columns.lastIndex,
                                    onClick = {
                                        menu = false
                                        onMoveColumn(column, 1)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Eliminar") },
                                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                    enabled = columns.size > 1,
                                    onClick = {
                                        menu = false
                                        onDeleteColumn(column)
                                    },
                                )
                            }
                        }
                    }
                }
                TextButton(
                    onClick = onAddColumn,
                    modifier = Modifier.testTag("add_column"),
                ) { Text("Añadir columna") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnFormDialog(
    initial: CustomColumn?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ColumnInput) -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var type by remember(initial?.id) { mutableStateOf(initial?.type ?: ColumnType.SHORT_TEXT) }
    var required by remember(initial?.id) { mutableStateOf(initial?.required ?: false) }
    var defaultValue by remember(initial?.id) {
        mutableStateOf(initial?.defaultValue.orEmpty())
    }
    var typeMenu by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val isPrimary = initial?.primary == true
    val input = ColumnInput(
        name = name,
        type = type,
        required = isPrimary || required,
        defaultValue = defaultValue.takeIf { it.isNotBlank() },
    )

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (initial == null) "Nueva columna" else "Editar columna") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        localError = null
                    },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("column_name"),
                )
                Column {
                    OutlinedButton(
                        onClick = { if (!isPrimary) typeMenu = true },
                        enabled = !isPrimary,
                        modifier = Modifier.fillMaxWidth().testTag("column_type"),
                    ) {
                        Icon(
                            columnIconVector(type),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(type.label, modifier = Modifier.padding(start = 8.dp))
                    }
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        ColumnType.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    if (option != type) {
                                        defaultValue = ""
                                        localError = null
                                    }
                                    type = option
                                    typeMenu = false
                                },
                            )
                        }
                    }
                }
                if (isPrimary) {
                    Text(
                        "La columna principal define el título de cada registro.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Obligatoria", modifier = Modifier.weight(1f))
                        Switch(checked = required, onCheckedChange = { required = it })
                    }
                }
                Text("Valor predeterminado (opcional)", style = MaterialTheme.typography.labelLarge)
                when {
                    type.usesOptions && initial?.options.isNullOrEmpty() -> {
                        Text(
                            "Añade opciones después de crear la columna.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    type == ColumnType.SELECT || type == ColumnType.STATUS -> {
                        OptionDropdown(
                            label = "Opción predeterminada",
                            options = initial?.options.orEmpty(),
                            selectedId = defaultValue.toLongOrNull(),
                            onSelect = { defaultValue = it?.id?.toString().orEmpty() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    type == ColumnType.MULTISELECT -> {
                        val ids = defaultValue.split(',').mapNotNull(String::toLongOrNull).toSet()
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            initial?.options.orEmpty().forEach { option ->
                                val selected = option.id in ids
                                OptionChip(
                                    option = option,
                                    selected = selected,
                                    onClick = {
                                        val next = if (selected) ids - option.id else ids + option.id
                                        defaultValue = next.sorted().joinToString(",")
                                    },
                                )
                            }
                        }
                    }
                    type == ColumnType.DATE || type == ColumnType.DATETIME -> {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                defaultValue.toLongOrNull()
                                    ?.let { formatDateValue(it, withTime = type == ColumnType.DATETIME) }
                                    ?: "Sin valor"
                            )
                        }
                        if (defaultValue.isNotBlank()) {
                            TextButton(onClick = { defaultValue = "" }) { Text("Quitar") }
                        }
                    }
                    type == ColumnType.CHECKBOX -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = defaultValue == "1",
                                onCheckedChange = { defaultValue = if (it) "1" else "0" },
                            )
                            Text("Marcado por defecto")
                        }
                    }
                    else -> {
                        OutlinedTextField(
                            value = defaultValue,
                            onValueChange = {
                                defaultValue = it
                                localError = null
                            },
                            singleLine = type != ColumnType.LONG_TEXT,
                            minLines = if (type == ColumnType.LONG_TEXT) 3 else 1,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = when (type) {
                                    ColumnType.INTEGER -> KeyboardType.Number
                                    ColumnType.DECIMAL, ColumnType.CURRENCY -> KeyboardType.Decimal
                                    ColumnType.EMAIL -> KeyboardType.Email
                                    ColumnType.PHONE -> KeyboardType.Phone
                                    ColumnType.URL -> KeyboardType.Uri
                                    else -> KeyboardType.Text
                                }
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                localError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    val value = input.copy(name = name.trim())
                    val error = validateColumnInput(value)
                    if (error != null) {
                        localError = error
                    } else {
                        onConfirm(value)
                    }
                },
                modifier = Modifier.testTag("column_confirm"),
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )

    if (showDatePicker) {
        TableDatePickerDialog(
            title = if (type == ColumnType.DATETIME) "Fecha y hora" else "Fecha",
            initial = defaultValue.toLongOrNull(),
            onDismiss = { showDatePicker = false },
            onSelect = {
                defaultValue = it.toString()
                showDatePicker = false
            },
        )
    }
}

@Composable
fun OptionManagerDialog(
    column: CustomColumn,
    onDismiss: () -> Unit,
    onAddOption: () -> Unit,
    onEditOption: (ColumnOption) -> Unit,
    onDeleteOption: (ColumnOption) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Opciones · ${column.displayName}") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (column.options.isEmpty()) {
                    Text(
                        "Todavía no hay opciones. Añade la primera para poder seleccionarla.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    column.options.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(colorFromHex(option.color))
                            )
                            Text(
                                text = option.name,
                                modifier = Modifier.weight(1f).padding(start = 10.dp),
                            )
                            IconButton(onClick = { onEditOption(option) }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Editar opción")
                            }
                            IconButton(onClick = { onDeleteOption(option) }) {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = "Eliminar opción",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = onAddOption, modifier = Modifier.testTag("add_option")) {
                    Text("Añadir opción")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptionFormDialog(
    initial: ColumnOption?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (OptionInput) -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember(initial?.id) { mutableStateOf(initial?.color ?: tableColorPalette[1]) }
    var localError by remember { mutableStateOf<String?>(null) }
    val input = OptionInput(name = name, color = color)
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (initial == null) "Nueva opción" else "Editar opción") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        localError = null
                    },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("option_name"),
                )
                Text("Color", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    tableColorPalette.forEach { hex ->
                        val selected = hex.equals(color, ignoreCase = true)
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(colorFromHex(hex))
                                .clickable { color = hex }
                                .padding(2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Outlined.Lock,
                                    contentDescription = "Color seleccionado",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
                localError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    val value = input.copy(name = name.trim())
                    val error = validateOptionInput(value)
                    if (error != null) {
                        localError = error
                    } else {
                        onConfirm(value)
                    }
                },
                modifier = Modifier.testTag("option_confirm"),
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}

@Composable
fun DeleteColumnDialog(
    column: CustomColumn,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Eliminar columna") },
        text = {
            Text("Se eliminará «${column.displayName}» y sus valores en todos los registros.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}

@Composable
fun DeleteOptionDialog(
    option: ColumnOption,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Eliminar opción") },
        text = {
            Text("Se quitará «${option.name}» de los registros que la usen. Esta acción no se puede deshacer.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}
