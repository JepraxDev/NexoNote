package com.example.nexonote.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexonote.domain.model.Note
import com.example.nexonote.domain.model.NoteColor
import com.example.nexonote.domain.model.NoteFilter
import com.example.nexonote.domain.model.NoteType
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val spanishLocale = Locale.forLanguageTag("es")

@Composable
fun NotesRoute(
    model: NotesViewModel,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val state by model.uiState.collectAsStateWithLifecycle()
    NotesScreen(
        state = state,
        onCreate = onCreate,
        onOpen = onOpen,
        onQueryChange = model::setQuery,
        onFilterChange = model::setFilter,
        onRetry = model::retry,
        onDismissError = model::dismissError,
        onTogglePinned = model::togglePinned,
        onToggleFavorite = model::toggleFavorite,
        onToggleArchived = model::toggleArchived,
        onTrash = model::moveToTrash,
        onRestore = model::restore,
        onDeletePermanently = model::deletePermanently,
        onEmptyTrash = model::emptyTrash,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotesScreen(
    state: NotesUiState,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (NoteFilter) -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    onTogglePinned: (Note) -> Unit,
    onToggleFavorite: (Note) -> Unit,
    onToggleArchived: (Note) -> Unit,
    onTrash: (Note) -> Unit,
    onRestore: (Note) -> Unit,
    onDeletePermanently: (Note) -> Unit,
    onEmptyTrash: () -> Unit,
) {
    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val contentWidth = maxWidth.value.coerceAtMost(860f).dp
        Box(Modifier.width(contentWidth).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("notes_list"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Ideas, listas y recordatorios en un solo lugar.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = onQueryChange,
                            modifier = Modifier.fillMaxWidth().testTag("notes_search"),
                            label = { Text("Buscar notas") },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            trailingIcon = if (state.query.isNotEmpty()) {
                                {
                                    IconButton(onClick = { onQueryChange("") }) {
                                        Icon(Icons.Outlined.Close, contentDescription = "Borrar búsqueda")
                                    }
                                }
                            } else null,
                            singleLine = true,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            NoteFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = state.filter == filter,
                                    onClick = { onFilterChange(filter) },
                                    label = {
                                        Text(
                                            when (filter) {
                                                NoteFilter.ACTIVE -> "Notas (${state.activeCount})"
                                                NoteFilter.ARCHIVED -> "Archivadas (${state.archivedCount})"
                                                NoteFilter.TRASH -> "Papelera (${state.trashedCount})"
                                            }
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                if (state.filter == NoteFilter.TRASH && state.trashedCount > 0) {
                    item(key = "emptyTrash") {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = { confirmEmptyTrash = true },
                                enabled = !state.busy,
                            ) {
                                Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Vaciar papelera")
                            }
                        }
                    }
                }
                state.operationError?.let { message ->
                    item(key = "operationError") {
                        ErrorBanner(message, onDismissError)
                    }
                }
                if (state.loading) {
                    item(key = "loading") {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Cargando tus notas…", style = MaterialTheme.typography.titleMedium)
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                } else if (state.error != null) {
                    item(key = "error") {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("No se pudieron cargar las notas", style = MaterialTheme.typography.titleLarge)
                                Text(state.error)
                                Button(onClick = onRetry) { Text("Reintentar") }
                            }
                        }
                    }
                } else if (state.notes.isEmpty()) {
                    item(key = "empty") {
                        EmptyNotes(
                            filter = state.filter,
                            searching = state.query.isNotBlank(),
                        )
                    }
                } else {
                    items(state.notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            filter = state.filter,
                            enabled = !state.busy,
                            onOpen = { onOpen(note.id) },
                            onTogglePinned = { onTogglePinned(note) },
                            onToggleFavorite = { onToggleFavorite(note) },
                            onToggleArchived = { onToggleArchived(note) },
                            onTrash = { onTrash(note) },
                            onRestore = { onRestore(note) },
                            onDelete = { noteToDelete = note },
                        )
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = onCreate,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).testTag("notes_add"),
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Nueva nota") },
            )
        }
    }
    noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Eliminar definitivamente") },
            text = { Text("“${note.displayTitle}” se borrará para siempre. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePermanently(note)
                    noteToDelete = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) { Text("Cancelar") }
            },
        )
    }
    if (confirmEmptyTrash) {
        AlertDialog(
            onDismissRequest = { confirmEmptyTrash = false },
            title = { Text("Vaciar papelera") },
            text = { Text("Se eliminarán definitivamente las ${state.trashedCount} notas de la papelera.") },
            confirmButton = {
                TextButton(onClick = {
                    onEmptyTrash()
                    confirmEmptyTrash = false
                }) { Text("Vaciar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmptyTrash = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun NoteCard(
    note: Note,
    filter: NoteFilter,
    enabled: Boolean,
    onOpen: () -> Unit,
    onTogglePinned: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleArchived: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    OutlinedCard(
        onClick = onOpen,
        enabled = enabled && !note.trashed,
        modifier = Modifier.fillMaxWidth().testTag("note_card_${note.id}"),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(noteColor(note.color))
            )
            Column(
                Modifier.weight(1f).padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            note.displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${if (note.type == NoteType.CHECKLIST) "Lista" else "Texto"} · ${dateLabel(note, filter)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (note.pinned) {
                        Icon(
                            Icons.Outlined.PushPin,
                            contentDescription = "Fijada",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (note.favorite) {
                        Icon(
                            Icons.Outlined.Star,
                            contentDescription = "Favorita",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                if (note.preview.isNotBlank()) {
                    Text(
                        note.preview,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (note.totalItems > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { note.checkedItems.toFloat() / note.totalItems },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${note.checkedItems} de ${note.totalItems} completados",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (note.labels.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        note.labels.forEach { label ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    "#$label",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (filter == NoteFilter.TRASH) {
                        IconButton(onClick = onRestore, enabled = enabled) {
                            Icon(Icons.Outlined.RestoreFromTrash, contentDescription = "Restaurar ${note.displayTitle}")
                        }
                        IconButton(onClick = onDelete, enabled = enabled) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Eliminar ${note.displayTitle}")
                        }
                    } else {
                        IconButton(onClick = onTogglePinned, enabled = enabled) {
                            Icon(
                                Icons.Outlined.PushPin,
                                contentDescription = if (note.pinned) "Desfijar ${note.displayTitle}" else "Fijar ${note.displayTitle}",
                                tint = if (note.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onToggleFavorite, enabled = enabled) {
                            Icon(
                                if (note.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (note.favorite) "Quitar de favoritas ${note.displayTitle}" else "Marcar como favorita ${note.displayTitle}",
                                tint = if (note.favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onToggleArchived, enabled = enabled) {
                            Icon(
                                if (note.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                contentDescription = if (note.archived) "Desarchivar ${note.displayTitle}" else "Archivar ${note.displayTitle}",
                            )
                        }
                        IconButton(onClick = onTrash, enabled = enabled) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Mover a papelera ${note.displayTitle}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNotes(filter: NoteFilter, searching: Boolean) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                Icons.AutoMirrored.Outlined.StickyNote2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                when {
                    searching -> "Sin resultados"
                    filter == NoteFilter.TRASH -> "La papelera está vacía"
                    filter == NoteFilter.ARCHIVED -> "No hay notas archivadas"
                    else -> "Tu primera nota empieza aquí"
                },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                when {
                    searching -> "Prueba con otro título, contenido o etiqueta."
                    filter == NoteFilter.TRASH -> "Las notas que elimines aparecerán aquí antes de borrarse para siempre."
                    filter == NoteFilter.ARCHIVED -> "Archiva notas para apartarlas sin perderlas."
                    else -> "Captura una idea o crea una lista con el botón Nueva nota."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    }
}

@Composable
internal fun noteColor(color: NoteColor): Color = when (color) {
    NoteColor.DEFAULT -> MaterialTheme.colorScheme.primary
    NoteColor.RED -> Color(0xFFD05A5A)
    NoteColor.ORANGE -> Color(0xFFD9833F)
    NoteColor.YELLOW -> Color(0xFFC6A13A)
    NoteColor.GREEN -> Color(0xFF4E9A65)
    NoteColor.BLUE -> Color(0xFF4C7FB8)
    NoteColor.PURPLE -> Color(0xFF8866B3)
}

private fun dateLabel(note: Note, filter: NoteFilter): String {
    val timestamp = if (filter == NoteFilter.TRASH) note.trashedAt else note.modifiedAt
    return timestamp?.let {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, spanishLocale).format(Date(it))
    } ?: "Fecha no registrada"
}
