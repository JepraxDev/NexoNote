package com.example.nexonote.ui.transfer

import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexonote.domain.model.BackupFrequency
import com.example.nexonote.domain.model.BackupHistoryEntry
import com.example.nexonote.domain.model.BackupLocation
import com.example.nexonote.domain.model.BackupStatus

private sealed interface BackupTarget {
    data object Managed : BackupTarget
    data class External(val uri: Uri) : BackupTarget
}

@Composable
fun BackupRoute(
    model: BackupViewModel,
    onBack: () -> Unit,
    onOpenRestore: (Uri) -> Unit,
) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var encrypt by rememberSaveable { mutableStateOf(false) }
    var pendingTarget by remember { mutableStateOf<BackupTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<BackupHistoryEntry?>(null) }
    val createDocument = rememberLauncherForActivityResult(CreateDocumentContract()) { uri ->
        if (uri != null) {
            val target = BackupTarget.External(uri)
            if (encrypt) pendingTarget = target else model.createExternal(uri, null)
        }
    }
    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) model.setDestination(uri)
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

    TransferScaffold(
        title = "Copias de seguridad",
        onBack = onBack,
        snackbar = snackbar,
        actions = {
            IconButton(onClick = model::refresh) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Actualizar historial")
            }
        },
    ) { padding ->
        TransferPage(Modifier.padding(padding)) {
            item {
                Text(
                    "Copia notas, tareas, tablas, eventos, carpetas, categorías, etiquetas y ajustes, " +
                        "con sus relaciones e identificadores. Elige guardarla dentro de NexoNote o en una " +
                        "carpeta a la que tengas acceso.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.settingsLoading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            item {
                TransferSection("Datos actuales") {
                    val counts = state.counts
                    val total = state.totalRows
                    if (counts.isEmpty()) {
                        Text("Calculando el contenido…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        SummaryRow("Notas", (counts["notes"] ?: 0).toString())
                        SummaryRow("Tareas", (counts["tasks"] ?: 0).toString())
                        SummaryRow("Tablas", (counts["custom_tables"] ?: 0).toString())
                        SummaryRow("Eventos", (counts["events"] ?: 0).toString())
                        SummaryRow("Filas totales", total.toString())
                    }
                }
            }
            item {
                TransferSection(
                    "Crear una copia ahora",
                    "La copia se verifica con una suma SHA-256 antes de guardarse. Puedes protegerla con contraseña.",
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Cifrar con contraseña", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "AES-256-GCM con una clave derivada de tu contraseña. No se guarda la contraseña.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = encrypt,
                            onCheckedChange = { encrypt = it },
                            enabled = !state.busy,
                        )
                    }
                    if (state.busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Button(
                        onClick = {
                            if (encrypt) pendingTarget = BackupTarget.Managed
                            else model.createManaged(null)
                        },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().testTag("backup_managed"),
                    ) { Text("Crear copia interna") }
                    OutlinedButton(
                        onClick = {
                            createDocument.launch(
                                CreateDocumentInput(
                                    fileName = model.suggestedFileName(),
                                    mimeType = "application/octet-stream",
                                )
                            )
                        },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().testTag("backup_external"),
                    ) { Text("Guardar en una carpeta…") }
                }
            }
            item {
                TransferSection(
                    "Copias automáticas",
                    "WorkManager ejecuta la copia cuando el sistema lo permite; Android no garantiza una hora exacta. " +
                        "Las copias automáticas no se cifran para no almacenar la contraseña.",
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Activar copias automáticas", Modifier.weight(1f))
                        Switch(
                            checked = state.automaticEnabled,
                            onCheckedChange = model::setAutomaticEnabled,
                            enabled = !state.busy,
                        )
                    }
                    Column(Modifier.selectableGroup()) {
                        BackupFrequency.entries.forEach { frequency ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = state.frequency == frequency,
                                        enabled = !state.busy,
                                        role = Role.RadioButton,
                                        onClick = { model.setFrequency(frequency) },
                                    )
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = state.frequency == frequency,
                                    onClick = null,
                                    enabled = !state.busy,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(frequency.label, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Carpeta de destino", style = MaterialTheme.typography.titleSmall)
                            Text(
                                state.destinationLabel ?: "Sin carpeta elegida",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OutlinedButton(
                            onClick = { chooseFolder.launch(null) },
                            enabled = !state.busy,
                        ) { Text(if (state.destination == null) "Elegir" else "Cambiar") }
                    }
                    state.warning?.let { TransferStatus(it, isError = true) }
                    Text(
                        "Última copia automática: " +
                            (state.lastAutomaticAt?.let(::formatTimestamp) ?: "nunca"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Historial", style = MaterialTheme.typography.titleLarge)
                    if (state.historyLoading) Text("Cargando…", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!state.historyLoading && state.history.isEmpty()) {
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Todavía no hay copias", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Crea la primera copia para poder restaurarla más adelante.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            items(state.history.size, key = { state.history[it].id }) { index ->
                val entry = state.history[index]
                val missing = entry.id in state.missing
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                entry.fileName,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (entry.encrypted) {
                                Text("Cifrada", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text(
                            "${formatTimestamp(entry.createdAt)} · ${formatSize(entry.sizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            entry.location.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        when {
                            entry.status == BackupStatus.FAILED -> Text(
                                "Error: ${entry.message ?: "no se pudo crear la copia"}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            missing -> Text(
                                "El archivo ya no está disponible en su ubicación.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (entry.status == BackupStatus.OK) {
                                TextButton(
                                    enabled = !missing && !state.busy,
                                    onClick = {
                                        model.restoreUri(entry)?.let(onOpenRestore)
                                    },
                                ) { Text("Restaurar") }
                            }
                            TextButton(
                                enabled = !state.busy,
                                onClick = { deleteTarget = entry },
                            ) {
                                Text(if (entry.location == BackupLocation.EXTERNAL) "Quitar del historial" else "Eliminar")
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.width(4.dp)) }
        }
    }

    pendingTarget?.let { target ->
        BackupPasswordDialog(
            title = "Proteger la copia",
            description = if (target is BackupTarget.Managed) {
                "La copia se guardará en el almacenamiento interno de NexoNote."
            } else {
                "La copia se guardará en el archivo que elegiste."
            },
            onDismiss = { pendingTarget = null },
            onConfirm = { password ->
                pendingTarget = null
                when (target) {
                    is BackupTarget.Managed -> model.createManaged(password)
                    is BackupTarget.External -> model.createExternal(target.uri, password)
                }
            },
        )
    }
    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(if (entry.location == BackupLocation.EXTERNAL) "Quitar del historial" else "Eliminar copia") },
            text = {
                Text(
                    if (entry.location == BackupLocation.EXTERNAL) {
                        "Se quitará «${entry.fileName}» del historial. El archivo externo no se borrará."
                    } else {
                        "Se eliminará «${entry.fileName}» del almacenamiento interno. Esta acción no se puede deshacer."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        model.delete(entry)
                    },
                ) { Text("Continuar") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
            },
        )
    }
}
