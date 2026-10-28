package com.example.nexonote.ui.transfer

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexonote.domain.model.ExportSection
import com.example.nexonote.domain.model.NoteExportFormat
import com.example.nexonote.domain.model.SpreadsheetFormat

@Composable
fun ExportRoute(model: ExportViewModel, onBack: () -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val createDocument = rememberLauncherForActivityResult(CreateDocumentContract()) { uri ->
        if (uri != null) model.saveTo(uri)
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            model.consumeMessage()
        }
    }
    LaunchedEffect(state.actionError) {
        state.actionError?.let {
            snackbar.showSnackbar(it)
            model.dismissError()
        }
    }
    LaunchedEffect(state.shareRequest) {
        val request = state.shareRequest ?: return@LaunchedEffect
        val launched = runCatching {
            context.startActivity(Intent.createChooser(model.shareIntent(request), "Compartir archivo"))
        }.isSuccess
        if (!launched) snackbar.showSnackbar("No hay ninguna aplicación disponible para compartir.")
        model.onShareHandled()
    }

    val csvConflict = state.section == ExportSection.TABLES &&
        state.spreadsheetFormat == SpreadsheetFormat.CSV &&
        state.selectedCount > 1
    val canAct = state.canExport && !csvConflict

    TransferScaffold(title = "Exportar", onBack = onBack, snackbar = snackbar) { padding ->
        TransferPage(Modifier.padding(padding)) {
            item {
                Text(
                    "Elige qué quieres sacar de NexoNote. Los archivos se guardan donde tú decidas " +
                        "o se comparten con otra aplicación, sin permisos de almacenamiento amplios.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth().testTag("export_loading")) }
            }
            state.error?.let { message ->
                item { TransferStatus(message, isError = true) }
            }
            item {
                TransferSection("Contenido") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportSection.entries.forEach { section ->
                            FilterChip(
                                selected = state.section == section,
                                onClick = { model.setSection(section) },
                                label = { Text(section.label) },
                                enabled = !state.busy,
                            )
                        }
                    }
                }
            }
            item {
                TransferSection("Formato") {
                    if (state.section == ExportSection.NOTES) {
                        Column(Modifier.selectableGroup()) {
                            NoteExportFormat.entries.forEach { format ->
                                FormatOption(
                                    label = format.label,
                                    description = when (format) {
                                        NoteExportFormat.PDF -> "Conserva el diseño y la paginación."
                                        NoteExportFormat.TXT -> "Texto plano, fácil de leer en cualquier sitio."
                                        NoteExportFormat.MARKDOWN -> "Ideal para otros editores y para copiar a la web."
                                    },
                                    selected = state.noteFormat == format,
                                    enabled = !state.busy,
                                    onClick = { model.setNoteFormat(format) },
                                )
                            }
                        }
                    } else {
                        Column(Modifier.selectableGroup()) {
                            SpreadsheetFormat.entries.forEach { format ->
                                FormatOption(
                                    label = format.label,
                                    description = when (format) {
                                        SpreadsheetFormat.CSV -> "Compatible con cualquier hoja de cálculo."
                                        SpreadsheetFormat.XLSX -> "Varias tablas en un libro, una hoja por tabla."
                                    },
                                    selected = state.spreadsheetFormat == format,
                                    enabled = !state.busy,
                                    onClick = { model.setSpreadsheetFormat(format) },
                                )
                            }
                        }
                        if (state.section == ExportSection.TABLES) {
                            Text(
                                "Con CSV solo se puede guardar una tabla por archivo; con XLSX, varias.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Selección (${state.selectedCount} de ${state.items.size})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row {
                        TextButton(onClick = model::selectAll, enabled = !state.busy) { Text("Todo") }
                        TextButton(onClick = model::clearSelection, enabled = !state.busy) { Text("Ninguno") }
                    }
                }
            }
            if (state.items.isEmpty() && !state.loading) {
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Nada que exportar", style = MaterialTheme.typography.titleMedium)
                            Text(
                                when (state.section) {
                                    ExportSection.NOTES -> "Crea una nota y vuelve a intentarlo."
                                    ExportSection.TASKS -> "Crea una tarea y vuelve a intentarlo."
                                    ExportSection.TABLES -> "Crea una tabla y vuelve a intentarlo."
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            items(state.items.size, key = { state.items[it].id }) { index ->
                val item = state.items[index]
                OutlinedCard(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !state.busy) { model.toggle(item.id) },
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = item.id in state.selected,
                            onCheckedChange = { model.toggle(item.id) },
                            enabled = !state.busy,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (item.detail.isNotBlank()) {
                                Text(
                                    item.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            if (csvConflict) {
                item {
                    TransferStatus(
                        "CSV solo admite una tabla por archivo. Selecciona una o cambia a Excel (XLSX).",
                        isError = true,
                    )
                }
            }
            if (state.busy) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Preparando el archivo…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            createDocument.launch(
                                CreateDocumentInput(model.suggestedFileName(), model.currentMimeType())
                            )
                        },
                        enabled = canAct,
                        modifier = Modifier.weight(1f).testTag("export_save"),
                    ) { Text("Guardar como…") }
                    OutlinedButton(
                        onClick = model::share,
                        enabled = canAct,
                        modifier = Modifier.weight(1f).testTag("export_share"),
                    ) { Text("Compartir") }
                }
            }
            item { Spacer(Modifier.width(4.dp)) }
        }
    }
}

@Composable
private fun FormatOption(
    label: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
