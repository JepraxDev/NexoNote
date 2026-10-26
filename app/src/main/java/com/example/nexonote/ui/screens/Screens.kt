package com.example.nexonote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.nexonote.domain.model.Entry
import com.example.nexonote.domain.model.EntryType
import com.example.nexonote.domain.model.ThemeMode
import com.example.nexonote.ui.NexoUiState

@Composable
fun CollectionScreen(
    type: EntryType,
    state: NexoUiState,
    create: (String, EntryType, () -> Unit) -> Unit,
    setCompleted: (Entry, Boolean) -> Unit,
    openCreate: Boolean = false,
    onCreateConsumed: () -> Unit = {},
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(openCreate) {
        if (openCreate) {
            showDialog = true
            onCreateConsumed()
        }
    }
    val entries = state.entries.filter { it.type == type }
    val singular = when (type) { EntryType.NOTE -> "nota"; EntryType.TASK -> "tarea"; EntryType.TABLE -> "tabla" }
    val description = when (type) {
        EntryType.NOTE -> "Captura una idea con un título. El editor de contenido llegará en una próxima etapa."
        EntryType.TASK -> "Anota un pendiente y márcalo cuando lo completes."
        EntryType.TABLE -> "Organiza tus tablas por nombre. Las columnas, celdas y fórmulas llegarán en otra etapa."
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (state.loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            } else if (entries.isEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Un nuevo comienzo", style = MaterialTheme.typography.titleLarge)
                            Text("Crea tu primera $singular con el botón de abajo.")
                        }
                    }
                }
            }
            items(entries, key = { it.id }) { entry ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (type == EntryType.TASK) {
                            Checkbox(
                                checked = entry.completed,
                                onCheckedChange = { setCompleted(entry, it) },
                                enabled = !state.saving,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            entry.title,
                            style = MaterialTheme.typography.titleMedium,
                            textDecoration = if (entry.completed) TextDecoration.LineThrough else null,
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text("Nueva $singular") },
        )
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { if (!state.saving) showDialog = false },
            title = { Text("Nueva $singular") },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 120) title = it },
                    label = { Text("Título") },
                    singleLine = true,
                    enabled = !state.saving,
                    supportingText = { Text("${title.length}/120") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = title.isNotBlank() && !state.saving,
                    onClick = { create(title, type) { showDialog = false; title = "" } },
                ) { Text(if (state.saving) "Guardando…" else "Crear") }
            },
            dismissButton = {
                TextButton(enabled = !state.saving, onClick = { showDialog = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
fun SettingsScreen(state: NexoUiState, setTheme: (ThemeMode) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Hazlo tuyo", style = MaterialTheme.typography.headlineMedium)
            Text("Elige cómo quieres ver tu espacio.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.selectableGroup().padding(16.dp)) {
                    Text("Apariencia", style = MaterialTheme.typography.titleLarge)
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            Modifier.fillMaxWidth().selectable(
                                selected = state.themeMode == mode,
                                enabled = !state.saving && !state.loading,
                                role = Role.RadioButton,
                                onClick = { setTheme(mode) },
                            ).padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = state.themeMode == mode, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(when (mode) { ThemeMode.SYSTEM -> "Según el sistema"; ThemeMode.LIGHT -> "Claro"; ThemeMode.DARK -> "Oscuro" })
                        }
                    }
                }
            }
        }
        item {
            Text("NexoNote · Base nativa", style = MaterialTheme.typography.titleMedium)
            Text("Tus elementos se guardan localmente con Room y tu apariencia con DataStore.")
        }
    }
}
