package com.example.nexonote.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexonote.domain.model.NoteColor
import com.example.nexonote.ui.organization.OrganizationFields
import com.example.nexonote.domain.model.NoteType
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val editorLocale = Locale.forLanguageTag("es")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorRoute(model: NoteEditorViewModel, onBack: () -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            model.dismissError()
        }
    }
    DisposableEffect(lifecycleOwner, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) model.saveNow()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler {
        if (state.missing) onBack() else model.saveAndExit(onBack)
    }
    NoteEditorScreen(
        state = state,
        snackbar = snackbar,
        onBack = { model.saveAndExit(onBack) },
        onTitleChange = model::setTitle,
        onContentChange = model::setContent,
        onTypeChange = model::setType,
        onColorChange = model::setColor,
        onTogglePinned = model::togglePinned,
        onToggleFavorite = model::toggleFavorite,
        onAddChecklistItem = model::addChecklistItem,
        onChecklistTextChange = model::setChecklistText,
        onChecklistCheckedChange = model::setChecklistChecked,
        onRemoveChecklistItem = model::removeChecklistItem,
        onAddLabel = model::addLabel,
        onRemoveLabel = model::removeLabel,
        onFolderChange = model::setFolder,
        onCategoryChange = model::setCategory,
        onArchive = { model.archiveAndExit(onBack) },
        onTrash = { model.trashAndExit(onBack) },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(
    state: NoteEditorUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onTypeChange: (NoteType) -> Unit,
    onColorChange: (NoteColor) -> Unit,
    onTogglePinned: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddChecklistItem: () -> Unit,
    onChecklistTextChange: (Long, String) -> Unit,
    onChecklistCheckedChange: (Long, Boolean) -> Unit,
    onRemoveChecklistItem: (Long) -> Unit,
    onAddLabel: (String) -> Unit,
    onRemoveLabel: (String) -> Unit,
    onFolderChange: (Long?) -> Unit,
    onCategoryChange: (Long?) -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var labelDialog by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when {
                                state.loading -> "Cargando nota"
                                state.noteId == null -> "Nueva nota"
                                else -> "Editar nota"
                            }
                        )
                        Text(
                            when {
                                state.loading -> ""
                                state.saving -> "Guardando…"
                                state.savedAt != null -> "Guardado ${timeLabel(state.savedAt)}"
                                else -> "Se guarda automáticamente"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.saving) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (!state.loading && !state.missing) {
                        IconButton(onClick = onTogglePinned, enabled = !state.saving) {
                            Icon(
                                Icons.Outlined.PushPin,
                                contentDescription = if (state.pinned) "Desfijar nota" else "Fijar nota",
                                tint = if (state.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onToggleFavorite, enabled = !state.saving) {
                            Icon(
                                if (state.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (state.favorite) "Quitar de favoritas" else "Marcar como favorita",
                                tint = if (state.favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }, enabled = !state.saving) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "Más opciones")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (state.noteId != null) {
                                    DropdownMenuItem(
                                        text = { Text(if (state.archived) "Mover a notas" else "Archivar") },
                                        onClick = { menuOpen = false; onArchive() },
                                        leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Mover a papelera") },
                                    onClick = { menuOpen = false; onTrash() },
                                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (state.missing) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Esta nota ya no existe", style = MaterialTheme.typography.titleLarge)
                    Text("Es posible que se haya eliminado desde la papelera.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onBack) { Text("Volver a las notas") }
                }
            }
            return@Scaffold
        }
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val width = maxWidth.value.coerceAtMost(860f).dp
            LazyColumn(
                modifier = Modifier.width(width).fillMaxSize().testTag("note_editor"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 60.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "kind") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Tipo de nota", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.type == NoteType.TEXT,
                                onClick = { onTypeChange(NoteType.TEXT) },
                                label = { Text("Texto") },
                            )
                            FilterChip(
                                selected = state.type == NoteType.CHECKLIST,
                                onClick = { onTypeChange(NoteType.CHECKLIST) },
                                label = { Text("Lista") },
                            )
                        }
                    }
                }
                item(key = "title") {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = onTitleChange,
                        modifier = Modifier.fillMaxWidth().testTag("note_title"),
                        label = { Text("Título") },
                        placeholder = { Text("Dale un nombre a tu idea") },
                        singleLine = true,
                        supportingText = { Text("${state.title.length}/120") },
                    )
                }
                if (state.type == NoteType.TEXT) {
                    item(key = "content") {
                        OutlinedTextField(
                            value = state.content,
                            onValueChange = onContentChange,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp).testTag("note_content"),
                            label = { Text("Contenido") },
                            placeholder = { Text("Escribe lo que quieras recordar…") },
                            minLines = 8,
                        )
                    }
                } else {
                    item(key = "checklistHeader") {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Elementos", style = MaterialTheme.typography.titleMedium)
                            if (state.checklist.any { it.text.isNotBlank() }) {
                                val done = state.checklist.count { it.checked && it.text.isNotBlank() }
                                val total = state.checklist.count { it.text.isNotBlank() }
                                Text(
                                    "$done de $total",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    items(state.checklist, key = { it.key }) { row ->
                        ChecklistRowEditor(
                            row = row,
                            onTextChange = { onChecklistTextChange(row.key, it) },
                            onCheckedChange = { onChecklistCheckedChange(row.key, it) },
                            onRemove = { onRemoveChecklistItem(row.key) },
                        )
                    }
                    item(key = "addChecklist") {
                        OutlinedButton(
                            onClick = onAddChecklistItem,
                            modifier = Modifier.testTag("add_checklist"),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Añadir elemento")
                        }
                    }
                }
                item(key = "organization") {
                    OrganizationFields(
                        folderId = state.folderId,
                        categoryId = state.categoryId,
                        folders = state.folders,
                        categories = state.categories,
                        onFolderChange = onFolderChange,
                        onCategoryChange = onCategoryChange,
                    )
                }
                item(key = "labels") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Etiquetas", style = MaterialTheme.typography.titleMedium)
                        if (state.labels.isEmpty()) {
                            Text(
                                "Organiza tus notas con etiquetas como trabajo, ideas o personal.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.labels.forEach { label ->
                                    InputChip(
                                        selected = true,
                                        onClick = { onRemoveLabel(label) },
                                        label = { Text("#$label") },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Outlined.Close,
                                                contentDescription = "Quitar etiqueta $label",
                                                modifier = Modifier.size(16.dp),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { labelDialog = true },
                            modifier = Modifier.testTag("add_label"),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Añadir etiqueta")
                        }
                    }
                }
                item(key = "color") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Color", style = MaterialTheme.typography.titleMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            NoteColor.entries.forEach { color ->
                                ColorChoice(
                                    color = color,
                                    selected = state.color == color,
                                    onClick = { onColorChange(color) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (labelDialog) {
        LabelDialog(
            selected = state.labels,
            available = state.availableLabels,
            onAdd = onAddLabel,
            onRemove = onRemoveLabel,
            onDismiss = { labelDialog = false },
        )
    }
}

@Composable
private fun ChecklistRowEditor(
    row: ChecklistRow,
    onTextChange: (String) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Checkbox(checked = row.checked, onCheckedChange = onCheckedChange)
        OutlinedTextField(
            value = row.text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f).testTag("checklist_item_${row.key}"),
            placeholder = { Text("Nuevo elemento") },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if (row.checked) TextDecoration.LineThrough else TextDecoration.None,
            ),
            singleLine = true,
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.Close, contentDescription = "Eliminar elemento")
        }
    }
}

@Composable
private fun ColorChoice(color: NoteColor, selected: Boolean, onClick: () -> Unit) {
    val label = when (color) {
        NoteColor.DEFAULT -> "Predeterminado"
        NoteColor.RED -> "Rojo"
        NoteColor.ORANGE -> "Naranja"
        NoteColor.YELLOW -> "Amarillo"
        NoteColor.GREEN -> "Verde"
        NoteColor.BLUE -> "Azul"
        NoteColor.PURPLE -> "Morado"
    }
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "Color $label" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (selected) 28.dp else 24.dp)
                .clip(CircleShape)
                .background(noteColor(color))
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelDialog(
    selected: List<String>,
    available: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val suggestions = (available + selected).distinct().sorted()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Etiquetas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.length <= 30) input = it },
                    modifier = Modifier.fillMaxWidth().testTag("label_input"),
                    label = { Text("Nueva etiqueta") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            enabled = input.isNotBlank() && selected.size < 20,
                            onClick = { onAdd(input); input = "" },
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "Añadir etiqueta")
                        }
                    },
                )
                if (suggestions.isNotEmpty()) {
                    Text("Existentes", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        suggestions.forEach { label ->
                            FilterChip(
                                selected = label in selected,
                                onClick = {
                                    if (label in selected) onRemove(label) else onAdd(label)
                                },
                                label = { Text("#$label") },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
    )
}

private fun timeLabel(timestamp: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT, editorLocale).format(Date(timestamp))
