package com.example.nexonote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.nexonote.domain.model.AccentColor
import com.example.nexonote.domain.model.Entry
import com.example.nexonote.domain.model.EntryType
import com.example.nexonote.domain.model.ThemeMode
import com.example.nexonote.ui.NexoUiState
import com.example.nexonote.ui.theme.accentSwatch

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: NexoUiState,
    setTheme: (ThemeMode) -> Unit,
    setAccent: (AccentColor) -> Unit = {},
    saveName: (String) -> Unit = {},
    onOpenExport: (() -> Unit)? = null,
    onOpenImport: (() -> Unit)? = null,
    onOpenBackup: (() -> Unit)? = null,
    onOpenRestore: (() -> Unit)? = null,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Hazlo tuyo", style = MaterialTheme.typography.headlineSmall)
            Text("Elige cómo quieres ver tu espacio.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tu perfil", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "El nombre aparece en el saludo del inicio.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    var name by rememberSaveable(state.displayName) { mutableStateOf(state.displayName) }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 40) name = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        enabled = !state.saving && !state.loading,
                        supportingText = { Text("${name.length}/40 · Opcional") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_name"),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(
                            onClick = { saveName(name) },
                            enabled = !state.saving && !state.loading && name.trim() != state.displayName,
                        ) {
                            Text(if (state.saving) "Guardando…" else "Guardar nombre")
                        }
                    }
                }
            }
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Apariencia", style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.selectableGroup()) {
                        ThemeMode.entries.forEach { mode ->
                            Row(
                                Modifier.fillMaxWidth().selectable(
                                    selected = state.themeMode == mode,
                                    enabled = !state.saving && !state.loading,
                                    role = Role.RadioButton,
                                    onClick = { setTheme(mode) },
                                ).padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = state.themeMode == mode, onClick = null)
                                Spacer(Modifier.width(12.dp))
                                Text(when (mode) { ThemeMode.SYSTEM -> "Según el sistema"; ThemeMode.LIGHT -> "Claro"; ThemeMode.DARK -> "Oscuro" })
                            }
                        }
                    }
                    Text(
                        "Color de acento",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "Botones, tarjetas y resaltados.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FlowRow(
                        modifier = Modifier.selectableGroup().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AccentColor.entries.forEach { accent ->
                            AccentSwatch(
                                accent = accent,
                                selected = state.accentColor == accent,
                                enabled = !state.saving && !state.loading,
                                onClick = { setAccent(accent) },
                            )
                        }
                    }
                }
            }
        }
        if (onOpenExport != null || onOpenImport != null || onOpenBackup != null || onOpenRestore != null) {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Datos y copias", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Exporta, importa o protege tu información. No se piden permisos de almacenamiento amplios.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        onOpenExport?.let {
                            DataAction(
                                icon = Icons.Outlined.Download,
                                title = "Exportar",
                                description = "Notas en PDF, TXT o Markdown; tareas y tablas en CSV o XLSX.",
                                onClick = it,
                            )
                        }
                        onOpenImport?.let {
                            DataAction(
                                icon = Icons.Outlined.Upload,
                                title = "Importar tabla",
                                description = "Carga un CSV o XLSX con vista previa y asignación de columnas.",
                                onClick = it,
                            )
                        }
                        onOpenBackup?.let {
                            DataAction(
                                icon = Icons.Outlined.SettingsBackupRestore,
                                title = "Copias de seguridad",
                                description = "Crea copias verificadas, prográmalas o elimínalas del historial.",
                                onClick = it,
                            )
                        }
                        onOpenRestore?.let {
                            DataAction(
                                icon = Icons.Outlined.Restore,
                                title = "Restaurar",
                                description = "Valida una copia y recupérala de forma atómica.",
                                onClick = it,
                            )
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

@Composable
private fun AccentSwatch(
    accent: AccentColor,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(48.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { contentDescription = "Color ${accent.label}" }
            .testTag("accent_${accent.name}"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(accentSwatch(accent))
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                ),
        )
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** One tappable row of the "Datos y copias" settings card. */
@Composable
private fun DataAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
