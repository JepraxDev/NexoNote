package com.example.nexonote.ui.transfer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexonote.domain.model.ColumnType
import com.example.nexonote.domain.model.DuplicateStrategy
import com.example.nexonote.domain.model.ImportMapping

@Composable
fun ImportRoute(model: ImportViewModel, onBack: () -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmUpdate by remember { mutableStateOf(false) }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.openFile(uri)
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            model.consumeMessage()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            model.dismissError()
        }
    }

    TransferScaffold(title = "Importar tabla", onBack = onBack, snackbar = snackbar) { padding ->
        TransferPage(Modifier.padding(padding)) {
            if (state.stage == ImportStage.DONE) {
                resultItems(model, state)
                return@TransferPage
            }
            item {
                Text(
                    "Importa un CSV o XLSX en una tabla nueva o existente. Verás una vista previa y " +
                        "podrás revisar cada columna antes de escribir nada.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.busy) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            if (state.stage == ImportStage.REVIEW) "Importando filas…" else "Leyendo el archivo…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                TransferSection("Archivo") {
                    OutlinedButton(
                        onClick = { openDocument.launch(arrayOf("*/*")) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().testTag("import_pick_file"),
                    ) {
                        Text(if (state.fileName == null) "Elegir archivo CSV o XLSX" else "Cambiar archivo")
                    }
                    state.fileName?.let {
                        Text(it, style = MaterialTheme.typography.titleSmall)
                    }
                    if (state.parsed != null) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("La primera fila contiene los encabezados", Modifier.weight(1f))
                            Switch(
                                checked = state.options.firstRowIsHeader,
                                onCheckedChange = model::setFirstRowIsHeader,
                                enabled = !state.busy,
                            )
                        }
                        val sheets = state.parsed?.sheetNames.orEmpty()
                        if (sheets.size > 1) {
                            Text("Hoja", style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                sheets.forEachIndexed { index, name ->
                                    FilterChip(
                                        selected = state.parsed?.sheetIndex == index,
                                        onClick = { model.selectSheet(index) },
                                        label = { Text(name.ifBlank { "Hoja ${index + 1}" }) },
                                        enabled = !state.busy,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                TargetPicker(model, state)
            }
            if (state.parsed != null && state.preview != null) {
                item {
                    Preview(state)
                }
                item { MappingSection(model, state) }
                item { OptionsSection(model, state) }
                item {
                    Button(
                        onClick = {
                            if (state.targetTableId != null &&
                                state.options.duplicateStrategy == DuplicateStrategy.UPDATE
                            ) {
                                confirmUpdate = true
                            } else {
                                model.runImport()
                            }
                        },
                        enabled = state.canImport,
                        modifier = Modifier.fillMaxWidth().testTag("import_run"),
                    ) { Text("Importar ${state.totalRows} filas") }
                }
            }
        }
    }
    if (confirmUpdate) {
        AlertDialog(
            onDismissRequest = { confirmUpdate = false },
            title = { Text("Confirmar actualización") },
            text = {
                Text(
                    "Se actualizarán las filas de «${state.tables.firstOrNull { it.id == state.targetTableId }?.name ?: "la tabla"}" +
                        "» cuya columna principal coincida con el archivo. Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmUpdate = false
                        model.runImport()
                    },
                ) { Text("Actualizar filas") }
            },
            dismissButton = {
                TextButton(onClick = { confirmUpdate = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun TargetPicker(model: ImportViewModel, state: ImportUiState) {
    TransferSection(
        "Tabla de destino",
        "Puedes crear una tabla nueva con los tipos detectados o añadir las filas a una tabla existente.",
    ) {
        Column(Modifier.selectableGroup()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = state.targetTableId == null,
                        enabled = !state.busy,
                        role = Role.RadioButton,
                        onClick = { model.selectTarget(null) },
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = state.targetTableId == null, onClick = null, enabled = !state.busy)
                Spacer(Modifier.width(10.dp))
                Text("Crear una tabla nueva", style = MaterialTheme.typography.titleSmall)
            }
            if (state.tablesLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            state.tables.forEach { table ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.targetTableId == table.id,
                            enabled = !state.busy,
                            role = Role.RadioButton,
                            onClick = { model.selectTarget(table.id) },
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = state.targetTableId == table.id,
                        onClick = null,
                        enabled = !state.busy,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(table.name, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        if (state.targetTableId == null) {
            OutlinedTextField(
                value = state.newTableName,
                onValueChange = model::setNewTableName,
                label = { Text("Nombre de la nueva tabla") },
                singleLine = true,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                "Si una columna no existe en el archivo, elige «Sin asignar» para dejarla vacía.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Preview(state: ImportUiState) {
    val preview = state.preview ?: return
    TransferSection("Vista previa", "${preview.totalRows} filas de datos detectadas.") {
        val scroll = rememberScrollState()
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.horizontalScroll(scroll).padding(10.dp)) {
                Row {
                    preview.headers.forEach { header ->
                        Text(
                            header,
                            modifier = Modifier.width(150.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                preview.sampleRows.forEachIndexed { index, row ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        preview.headers.indices.forEach { column ->
                            Text(
                                row.getOrNull(column).orEmpty(),
                                modifier = Modifier.width(150.dp),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (index < preview.sampleRows.lastIndex) HorizontalDivider()
                }
            }
        }
        if (preview.detectedTypes.size == preview.headers.size) {
            Text(
                "Tipos detectados: " + preview.headers.mapIndexed { index, header ->
                    "$header (${preview.detectedTypes[index].label})"
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MappingSection(model: ImportViewModel, state: ImportUiState) {
    if (state.targetTableId == null) {
        TransferSection(
            "Columnas de la nueva tabla",
            "La primera columna incluida será la principal. Desmarca las que no quieras importar.",
        ) {
            state.mappings.forEachIndexed { index, mapping ->
                val included = mapping.sourceIndex != null
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = included,
                                onCheckedChange = { model.setNewColumnIncluded(index, it) },
                                enabled = !state.busy,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                state.sourceHeaders.getOrNull(index).orEmpty().ifBlank { "Columna ${index + 1}" },
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (index == 0) {
                                Text("Principal", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        OutlinedTextField(
                            value = mapping.targetName,
                            onValueChange = { model.setNewColumnName(index, it) },
                            label = { Text("Nombre de la columna") },
                            singleLine = true,
                            enabled = included && !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TypeDropdown(
                            selected = mapping.targetType,
                            enabled = included && !state.busy,
                            onSelected = { model.setNewColumnType(index, it) },
                        )
                    }
                }
            }
        }
    } else {
        TransferSection(
            "Asignación de columnas",
            "Elige qué columna del archivo alimenta cada columna de la tabla de destino.",
        ) {
            state.mappings.forEach { mapping ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            mapping.targetName,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(mapping.targetType.label, style = MaterialTheme.typography.labelSmall)
                    }
                    SourceDropdown(
                        mapping = mapping,
                        headers = state.sourceHeaders,
                        enabled = !state.busy,
                        onSelected = { model.setMappingSource(mapping.targetColumnId ?: -1L, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionsSection(model: ImportViewModel, state: ImportUiState) {
    TransferSection("Duplicados y errores") {
        Column(Modifier.selectableGroup()) {
            DuplicateStrategy.entries.forEach { strategy ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.options.duplicateStrategy == strategy,
                            enabled = !state.busy,
                            role = Role.RadioButton,
                            onClick = { model.setDuplicateStrategy(strategy) },
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = state.options.duplicateStrategy == strategy,
                        onClick = null,
                        enabled = !state.busy,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(strategy.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            strategy.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Omitir filas con errores", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Si lo desactivas, una sola fila no válida impide importar el archivo completo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.options.skipInvalidRows,
                onCheckedChange = model::setSkipInvalidRows,
                enabled = !state.busy,
            )
        }
    }
}

@Composable
private fun TypeDropdown(
    selected: ColumnType,
    enabled: Boolean,
    onSelected: (ColumnType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
            Text("Tipo: ${selected.label}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                ImportViewModel.CREATABLE_TYPES.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.label) },
                        onClick = {
                            expanded = false
                            onSelected(type)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceDropdown(
    mapping: ImportMapping,
    headers: List<String>,
    enabled: Boolean,
    onSelected: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
            Text(
                mapping.sourceIndex?.let { index ->
                    headers.getOrNull(index).orEmpty().ifBlank { "Columna ${index + 1}" }
                } ?: "Sin asignar",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                DropdownMenuItem(
                    text = { Text("Sin asignar") },
                    onClick = {
                        expanded = false
                        onSelected(null)
                    },
                )
                headers.forEachIndexed { index, header ->
                    DropdownMenuItem(
                        text = { Text(header.ifBlank { "Columna ${index + 1}" }) },
                        onClick = {
                            expanded = false
                            onSelected(index)
                        },
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.resultItems(
    model: ImportViewModel,
    state: ImportUiState,
) {
    item {
        Text("Resultado", style = MaterialTheme.typography.headlineSmall)
    }
    item {
        state.message?.let { TransferStatus(it, isError = false) }
    }
    item {
        val result = state.result
        TransferSection("Resumen") {
            if (result == null) {
                Text("No se importó nada.")
            } else {
                SummaryRow("Filas nuevas", result.imported.toString())
                SummaryRow("Filas actualizadas", result.updated.toString())
                SummaryRow("Filas omitidas", result.skipped.toString())
                if (result.createdTableId != null && state.targetTableId == null) {
                    Text(
                        "Se creó una tabla nueva con las columnas y tipos revisados.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    val errors = state.result?.errors.orEmpty()
    if (errors.isNotEmpty()) {
        item {
            TransferSection(
                "Filas con errores (${errors.size})",
                "Ninguna de estas filas se escribió. Corrige el archivo o activa «Omitir filas con errores».",
            ) {
                errors.take(50).forEach { error ->
                    Text(
                        "Fila ${error.rowNumber}: ${error.message}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (errors.size > 50) {
                    Text(
                        "Y ${errors.size - 50} filas más con errores.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = model::reviewAgain, modifier = Modifier.weight(1f)) {
                Text("Revisar de nuevo")
            }
            Button(onClick = model::backToFile, modifier = Modifier.weight(1f)) {
                Text("Otro archivo")
            }
        }
    }
}
