package com.example.nexonote.ui.tables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.nexonote.domain.model.ColumnType
import com.example.nexonote.domain.model.CustomColumn
import com.example.nexonote.domain.model.TableFilter
import com.example.nexonote.domain.model.TableFilterOperator
import com.example.nexonote.domain.model.TableSort
import com.example.nexonote.domain.model.filterOperatorsFor

@Composable
fun FilterDialog(
    columns: List<CustomColumn>,
    filters: List<TableFilter>,
    onDismiss: () -> Unit,
    onAddFilter: () -> Unit,
    onRemoveFilter: (Int) -> Unit,
    onOpenSort: () -> Unit,
    onClearAll: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.FilterAlt, contentDescription = null) },
        title = { Text("Filtros y orden") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (filters.isEmpty()) {
                    Text(
                        "Sin filtros. Puedes combinar varios campos.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    filters.forEachIndexed { index, filter ->
                        val column = columns.firstOrNull { it.id == filter.columnId }
                        if (column != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = column.displayName,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        text = buildFilterLabel(filter, column),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { onRemoveFilter(index) }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Quitar filtro")
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = onAddFilter, modifier = Modifier.testTag("add_filter")) {
                    Text("Añadir filtro")
                }
                HorizontalDivider()
                Text("Orden", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = onOpenSort, modifier = Modifier.fillMaxWidth()) {
                    Text("Configurar orden")
                }
                if (filters.isNotEmpty()) {
                    TextButton(onClick = onClearAll) { Text("Limpiar filtros y orden") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
fun AddFilterDialog(
    columns: List<CustomColumn>,
    onDismiss: () -> Unit,
    onAdd: (TableFilter) -> Unit,
) {
    var selectedId by remember { mutableStateOf(columns.firstOrNull()?.id) }
    val selectedColumn = columns.firstOrNull { it.id == selectedId }
    var operator by remember {
        mutableStateOf(selectedColumn?.type?.let(::filterOperatorsFor)?.firstOrNull())
    }
    var value by remember {
        mutableStateOf(selectedColumn?.let(::initialFilterValue).orEmpty())
    }
    var columnMenu by remember { mutableStateOf(false) }

    fun updateColumn(column: CustomColumn) {
        selectedId = column.id
        operator = filterOperatorsFor(column.type).firstOrNull()
        value = initialFilterValue(column)
    }

    val needsValue = operator?.let(::operatorNeedsValue) == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir filtro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    OutlinedButton(
                        onClick = { columnMenu = true },
                        modifier = Modifier.fillMaxWidth().testTag("filter_column"),
                    ) {
                        Text(selectedColumn?.displayName ?: "Elegir columna")
                    }
                    DropdownMenu(expanded = columnMenu, onDismissRequest = { columnMenu = false }) {
                        columns.forEach { column ->
                            DropdownMenuItem(
                                text = { Text(column.displayName) },
                                onClick = {
                                    updateColumn(column)
                                    columnMenu = false
                                },
                            )
                        }
                    }
                }
                if (selectedColumn != null) {
                    OperatorDropdown(
                        column = selectedColumn,
                        selected = operator,
                        onSelect = {
                            operator = it
                            if (!operatorNeedsValue(it)) value = ""
                        },
                    )
                    if (needsValue) {
                        FilterValueField(
                            column = selectedColumn,
                            value = value,
                            onValueChange = { value = it },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedColumn != null && operator != null && (!needsValue || value.isNotBlank()),
                onClick = {
                    val column = selectedColumn ?: return@TextButton
                    val op = operator ?: return@TextButton
                    onAdd(TableFilter(columnId = column.id, operator = op, value = value))
                },
                modifier = Modifier.testTag("filter_apply"),
            ) { Text("Añadir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OperatorDropdown(
    column: CustomColumn,
    selected: TableFilterOperator?,
    onSelect: (TableFilterOperator) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = filterOperatorsFor(column.type)
    Column {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().testTag("filter_operator"),
        ) {
            Text(selected?.label ?: "Elegir condición")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { operator ->
                DropdownMenuItem(
                    text = { Text(operator.label) },
                    onClick = {
                        onSelect(operator)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterValueField(
    column: CustomColumn,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    when (column.type) {
        ColumnType.SELECT, ColumnType.STATUS, ColumnType.MULTISELECT -> {
            OptionDropdown(
                label = "Valor",
                options = column.options,
                selectedId = value.toLongOrNull(),
                enabled = column.options.isNotEmpty(),
                onSelect = { onValueChange(it?.id?.toString().orEmpty()) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ColumnType.DATE, ColumnType.DATETIME -> {
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    value.toLongOrNull()?.let { formatDateValue(it) } ?: "Elegir fecha"
                )
            }
            if (showDatePicker) {
                TableDatePickerDialog(
                    title = "Fecha del filtro",
                    initial = value.toLongOrNull(),
                    onDismiss = { showDatePicker = false },
                    onSelect = {
                        onValueChange(it.toString())
                        showDatePicker = false
                    },
                )
            }
        }
        ColumnType.INTEGER, ColumnType.DECIMAL, ColumnType.CURRENCY -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Valor") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        else -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Valor") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun SortDialog(
    columns: List<CustomColumn>,
    sort: TableSort?,
    onDismiss: () -> Unit,
    onApply: (TableSort?) -> Unit,
) {
    var columnId by remember { mutableStateOf(sort?.columnId ?: columns.firstOrNull()?.id) }
    var ascending by remember { mutableStateOf(sort?.ascending ?: true) }
    var columnMenu by remember { mutableStateOf(false) }
    val column = columns.firstOrNull { it.id == columnId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ordenar registros") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    OutlinedButton(
                        onClick = { columnMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(column?.displayName ?: "Elegir columna")
                    }
                    DropdownMenu(expanded = columnMenu, onDismissRequest = { columnMenu = false }) {
                        columns.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.displayName) },
                                onClick = {
                                    columnId = item.id
                                    columnMenu = false
                                },
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (ascending) "Ascendente" else "Descendente",
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = ascending, onCheckedChange = { ascending = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = column != null,
                onClick = { onApply(column?.let { TableSort(it.id, ascending) }) },
                modifier = Modifier.testTag("sort_apply"),
            ) { Text("Aplicar") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onApply(null) }) { Text("Quitar") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

internal fun buildFilterLabel(filter: TableFilter, column: CustomColumn): String {
    val value = if (operatorNeedsValue(filter.operator)) {
        val display = when (column.type) {
            ColumnType.DATE, ColumnType.DATETIME ->
                filter.value.toLongOrNull()?.let { formatDateValue(it) } ?: filter.value
            ColumnType.SELECT, ColumnType.STATUS, ColumnType.MULTISELECT ->
                formatFilterValue(filter.value, column.options)
            else -> filter.value
        }
        " · $display"
    } else {
        ""
    }
    return filter.operator.label + value
}

private fun operatorNeedsValue(operator: TableFilterOperator): Boolean = when (operator) {
    TableFilterOperator.NOT_EMPTY,
    TableFilterOperator.IS_TRUE,
    TableFilterOperator.IS_FALSE -> false
    else -> true
}

private fun initialFilterValue(column: CustomColumn): String = when (column.type) {
    ColumnType.SELECT, ColumnType.STATUS, ColumnType.MULTISELECT ->
        column.options.firstOrNull()?.id?.toString().orEmpty()
    else -> ""
}
