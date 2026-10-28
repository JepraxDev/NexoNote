package com.example.nexonote.ui.transfer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexonote.domain.model.RestoreMode

@Composable
fun RestoreRoute(model: RestoreViewModel, onBack: () -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var password by remember { mutableStateOf("") }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            password = ""
            model.inspect(uri)
        }
    }
    var confirming by remember { mutableStateOf(false) }
    var replaceAccepted by remember { mutableStateOf(false) }
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

    val preview = state.preview

    TransferScaffold(title = "Restaurar", onBack = onBack, snackbar = snackbar) { padding ->
        TransferPage(Modifier.padding(padding)) {
            item {
                Text(
                    "Elige una copia de NexoNote. Primero se valida el formato, la versión, el tamaño " +
                        "y la integridad; después verás un resumen y confirmarás la restauración.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.inspecting) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Validando la copia…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { openDocument.launch(arrayOf("*/*")) },
                    enabled = !state.busy && !state.inspecting,
                    modifier = Modifier.fillMaxWidth().testTag("restore_pick"),
                ) {
                    Text(if (state.sourceName == null) "Elegir archivo de copia" else "Elegir otra copia")
                }
            }
            if (state.sourceName != null) {
                item {
                    Text(
                        state.sourceName.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (preview != null) {
                item { PreviewSummary(preview.fileName, preview) }
                if (preview.problems.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            preview.problems.forEach { problem -> TransferStatus(problem, isError = true) }
                        }
                    }
                }
                if (preview.manifest.encrypted) {
                    item {
                        TransferSection(
                            "Contraseña",
                            if (state.passwordVerified) {
                                "Contraseña verificada. Vuelve a introducirla para confirmar la restauración."
                            } else {
                                "La copia está cifrada. Introduce su contraseña para comprobar que se puede abrir."
                            },
                        ) {
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it.take(128) },
                                label = { Text("Contraseña de la copia") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                enabled = !state.inspecting && !state.busy,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = {
                                    state.sourceUri?.let { uri ->
                                        runCatching { uri.toUri() }.getOrNull()?.let {
                                            model.inspect(it, password.toCharArray())
                                        }
                                    }
                                },
                                enabled = password.isNotEmpty() && !state.inspecting,
                            ) { Text("Verificar contraseña") }
                            if (state.passwordVerified) {
                                TransferStatus("Contraseña correcta. La copia se puede leer.", isError = false)
                            }
                        }
                    }
                }
                if (preview.compatible && state.passwordVerified) {
                    item {
                        TransferSection(
                            "Modo de restauración",
                            "Elige cómo combinar la copia con los datos que ya tienes.",
                        ) {
                            Column(Modifier.selectableGroup()) {
                                RestoreMode.entries.forEach { mode ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .selectable(
                                                selected = state.mode == mode,
                                                enabled = !state.busy,
                                                role = Role.RadioButton,
                                                onClick = { model.setMode(mode) },
                                            )
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        RadioButton(
                                            selected = state.mode == mode,
                                            onClick = null,
                                            enabled = !state.busy,
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(mode.label, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                if (mode == RestoreMode.REPLACE) {
                                                    "Elimina los datos actuales y deja exactamente los de la copia. " +
                                                        "Se conserva una instantánea para revertir si algo falla."
                                                } else {
                                                    "Añade solo los datos que no entren en conflicto. Nunca borra nada."
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            Button(
                                onClick = { confirming = true },
                                enabled = state.canRestore &&
                                    (!preview.manifest.encrypted || password.isNotEmpty()),
                                modifier = Modifier.fillMaxWidth().testTag("restore_confirm"),
                            ) { Text("Restaurar esta copia…") }
                        }
                    }
                }
            }
            if (state.result != null) {
                item {
                    TransferSection("Restauración completada") {
                        val result = state.result
                        if (result != null) {
                            SummaryRow("Filas restauradas", result.restoredRows.values.sum().toString())
                            SummaryRow("Archivos adjuntos", result.attachmentsRestored.toString())
                            Text(
                                if (result.mode == RestoreMode.REPLACE) {
                                    "Tus datos actuales se reemplazaron por los de la copia."
                                } else {
                                    "Los datos de la copia se combinaron sin borrar los existentes."
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    Button(
                        onClick = {
                            password = ""
                            model.reset()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Restaurar otra copia") }
                }
            }
            if (state.busy) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            "Restaurando de forma atómica. No cierres la aplicación…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (confirming && preview != null) {
        AlertDialog(
            onDismissRequest = { confirming = false; replaceAccepted = false },
            title = { Text("Confirmar restauración") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (state.mode == RestoreMode.REPLACE) {
                            "Se reemplazarán todos los datos actuales (${preview.counts.values.sum()} filas en la copia). " +
                                "Las copias de seguridad y este historial no se tocan."
                        } else {
                            "Se añadirán los datos de la copia que no entren en conflicto. " +
                                "Si hay identificadores o nombres repetidos, la restauración se rechazará sin cambios."
                        }
                    )
                    if (state.mode == RestoreMode.REPLACE) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = replaceAccepted,
                                onCheckedChange = { replaceAccepted = it },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Entiendo que mis datos actuales se reemplazarán.", Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = (state.mode != RestoreMode.REPLACE || replaceAccepted) &&
                        (!preview.manifest.encrypted || password.isNotEmpty()),
                    onClick = {
                        confirming = false
                        replaceAccepted = false
                        model.restore(password.toCharArray())
                        password = ""
                    },
                ) { Text("Restaurar") }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false; replaceAccepted = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun PreviewSummary(fileName: String, preview: com.example.nexonote.domain.model.RestorePreview) {
    val manifest = preview.manifest
    TransferSection("Resumen de la copia") {
        SummaryRow("Archivo", fileName)
        SummaryRow("Creada", formatTimestamp(manifest.createdAt))
        SummaryRow("Tamaño", formatSize(manifest.payloadBytes))
        SummaryRow("Versión de formato", manifest.formatVersion.toString())
        SummaryRow("Versión del esquema", manifest.schemaVersion.toString())
        SummaryRow("Cifrado", if (manifest.encrypted) "Sí (AES-256-GCM)" else "No")
        SummaryRow("Archivos adjuntos", preview.attachmentCount.toString())
        SummaryRow("Filas totales", preview.counts.values.sum().toString())
    }
    if (preview.counts.isNotEmpty()) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Contenido", style = MaterialTheme.typography.titleMedium)
                val known = TABLE_LABELS.keys.mapNotNull { key ->
                    preview.counts[key]?.takeIf { it > 0 }?.let { key to it }
                }
                val extra = preview.counts.filterKeys { it !in TABLE_LABELS && (preview.counts[it] ?: 0) > 0 }
                (known + extra.entries.map { it.key to it.value }).forEach { (key, value) ->
                    SummaryRow(tableLabel(key), value.toString())
                }
            }
        }
    }
}
