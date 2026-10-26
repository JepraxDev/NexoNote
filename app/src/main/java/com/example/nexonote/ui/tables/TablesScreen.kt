package com.example.nexonote.ui.tables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexonote.domain.model.CustomTable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TablesRoute(
    model: TablesViewModel,
    openCreate: Boolean,
    onCreateConsumed: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showCreate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CustomTable?>(null) }
    var deleting by remember { mutableStateOf<CustomTable?>(null) }

    LaunchedEffect(openCreate) {
        if (openCreate) {
            showCreate = true
            onCreateConsumed()
        }
    }
    LaunchedEffect(state.operationError) {
        state.operationError?.let {
            snackbar.showSnackbar(it)
            model.dismissError()
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Nueva tabla") },
                modifier = Modifier.testTag("new_table"),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        TablesScreen(
            state = state,
            modifier = Modifier.padding(padding),
            onQueryChange = model::setQuery,
            onRetry = model::retry,
            onOpen = onOpen,
            onCreate = { showCreate = true },
            onEdit = { editing = it },
            onDuplicate = { table ->
                model.duplicateTable(table.id, onCreated = onOpen)
            },
            onDelete = { deleting = it },
        )
    }

    if (showCreate) {
        TableFormDialog(
            initial = null,
            busy = state.busy,
            onDismiss = { showCreate = false },
            onConfirm = { input ->
                model.createTable(input) { id ->
                    showCreate = false
                    onOpen(id)
                }
            },
        )
    }
    editing?.let { table ->
        TableFormDialog(
            initial = table,
            busy = state.busy,
            onDismiss = { editing = null },
            onConfirm = { input ->
                model.updateTable(table.id, input) { editing = null }
            },
        )
    }
    deleting?.let { table ->
        DeleteTableDialog(
            table = table,
            busy = state.busy,
            onDismiss = { deleting = null },
            onConfirm = {
                model.deleteTable(table.id) { deleting = null }
            },
        )
    }
}

@Composable
private fun TablesScreen(
    state: TablesUiState,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    onEdit: (CustomTable) -> Unit,
    onDuplicate: (CustomTable) -> Unit,
    onDelete: (CustomTable) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = when (state.totalTableCount) {
                    0 -> "Organiza cualquier cosa"
                    1 -> "1 tabla"
                    else -> "${state.totalTableCount} tablas"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Crea tablas flexibles con columnas, filtros y registros.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Limpiar búsqueda")
                        }
                    }
                },
                placeholder = { Text("Buscar tablas") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().testTag("table_search"),
            )
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Cargando tablas…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.error != null -> ErrorPane(state.error, onRetry)
            state.tables.isEmpty() -> EmptyTables(onCreate)
            state.visibleTables.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay tablas que coincidan con la búsqueda.")
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.visibleTables, key = { it.id }) { table ->
                    TableCard(
                        table = table,
                        onClick = { onOpen(table.id) },
                        onEdit = { onEdit(table) },
                        onDuplicate = { onDuplicate(table) },
                        onDelete = { onDelete(table) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TableCard(
    table: CustomTable,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("table_card_${table.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TableIconBadge(table.icon, table.color)
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = table.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (table.description.isNotBlank()) {
                    Text(
                        text = table.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${table.columnCount} ${if (table.columnCount == 1) "columna" else "columnas"} · " +
                        "${table.rowCount} ${if (table.rowCount == 1) "registro" else "registros"}" +
                        table.modifiedAt?.let { " · ${formatTableDate(it)}" }.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Acciones de ${table.displayName}")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Editar") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            menu = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicar") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                        onClick = {
                            menu = false
                            onDuplicate()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTables(onCreate: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Outlined.GridOn,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("Tu primera tabla", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Empieza con una columna «Nombre» y añade los campos que necesites.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Crear tabla") },
            )
        }
    }
}

@Composable
private fun ErrorPane(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            androidx.compose.material3.TextButton(onClick = onRetry) { Text("Reintentar") }
        }
    }
}

private fun formatTableDate(timestamp: Long): String =
    SimpleDateFormat("d MMM", Locale.forLanguageTag("es")).format(Date(timestamp))
