package com.example.nexonote.ui.organization

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexonote.domain.model.Category
import com.example.nexonote.domain.model.ContentType
import com.example.nexonote.domain.model.Folder
import com.example.nexonote.domain.model.FolderIcon
import com.example.nexonote.domain.model.ORGANIZATION_COLOR_PALETTE
import com.example.nexonote.domain.model.OrganizationItem
import com.example.nexonote.domain.model.Tag
import com.example.nexonote.domain.model.descendantsOf
import com.example.nexonote.domain.model.folderPath
import com.example.nexonote.domain.model.validateCategoryName
import com.example.nexonote.domain.model.validateFolderName
import com.example.nexonote.domain.model.validateTagName
import com.example.nexonote.ui.tables.colorFromHex

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersRoute(model: FoldersViewModel, onBack: () -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showCreate by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<Folder?>(null) }
    var parentForNew by remember { mutableStateOf<Long?>(null) }
    var deletingFolder by remember { mutableStateOf<Folder?>(null) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }
    var editingTag by remember { mutableStateOf<Tag?>(null) }
    var deletingTag by remember { mutableStateOf<Tag?>(null) }
    var movingItem by remember { mutableStateOf<OrganizationItem?>(null) }

    LaunchedEffect(state.operationError) {
        state.operationError?.let {
            snackbar.showSnackbar(it)
            model.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Organización") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    when (state.tab) {
                        OrganizationTab.FOLDERS -> {
                            parentForNew = null
                            editingFolder = null
                            showCreate = true
                        }
                        OrganizationTab.CATEGORIES -> editingCategory = Category(0, "")
                        OrganizationTab.TAGS -> editingTag = Tag(0, "")
                    }
                },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = {
                    Text(
                        when (state.tab) {
                            OrganizationTab.FOLDERS -> "Nueva carpeta"
                            OrganizationTab.CATEGORIES -> "Nueva categoría"
                            OrganizationTab.TAGS -> "Nueva etiqueta"
                        }
                    )
                },
                modifier = Modifier.testTag("organization_new"),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OrganizationTab.entries.forEach { tab ->
                    FilterChip(
                        selected = state.tab == tab,
                        onClick = { model.selectTab(tab) },
                        label = { Text(tab.label) },
                        modifier = Modifier.testTag("organization_tab_${tab.name}"),
                    )
                }
            }
            when {
                state.loading -> OrganizationLoading()
                state.error != null -> OrganizationError(state.error.orEmpty(), model::retry)
                state.tab == OrganizationTab.FOLDERS -> FolderList(
                    state = state,
                    onEdit = { editingFolder = it },
                    onAddChild = { folder ->
                        parentForNew = folder.id
                        editingFolder = null
                        showCreate = true
                    },
                    onDelete = { deletingFolder = it },
                    onToggle = model::toggleExpanded,
                    onMoveItem = { movingItem = it },
                )
                state.tab == OrganizationTab.CATEGORIES -> CategoryList(
                    categories = state.categories,
                    onEdit = { editingCategory = it },
                    onDelete = { deletingCategory = it },
                )
                else -> TagList(
                    tags = state.tags,
                    onEdit = { editingTag = it },
                    onDelete = { deletingTag = it },
                )
            }
        }
    }

    if (showCreate) {
        FolderFormDialog(
            initial = null,
            folders = state.folders,
            defaultParentId = parentForNew,
            busy = state.busy,
            onDismiss = { showCreate = false },
            onConfirm = { name, description, icon, color, parentId, done ->
                model.saveFolder(
                    name = name,
                    description = description,
                    icon = icon,
                    color = color,
                    parentId = parentId,
                    onDone = { showCreate = false; done() },
                )
            },
        )
    }
    editingFolder?.let { folder ->
        FolderFormDialog(
            initial = folder,
            folders = state.folders,
            defaultParentId = folder.parentId,
            busy = state.busy,
            onDismiss = { editingFolder = null },
            onConfirm = { name, description, icon, color, parentId, done ->
                model.saveFolder(
                    id = folder.id,
                    name = name,
                    description = description,
                    icon = icon,
                    color = color,
                    parentId = parentId,
                    onDone = { editingFolder = null; done() },
                )
            },
        )
    }
    deletingFolder?.let { folder ->
        DeleteFolderDialog(
            folder = folder,
            folders = state.folders,
            busy = state.busy,
            onDismiss = { deletingFolder = null },
            onConfirm = { model.deleteFolder(folder.id) { deletingFolder = null } },
        )
    }
    editingCategory?.let { category ->
        CategoryFormDialog(
            initial = category.takeIf { it.id != 0L },
            busy = state.busy,
            onDismiss = { editingCategory = null },
            onConfirm = { name, color ->
                model.saveCategory(category.takeIf { it.id != 0L }?.id, name, color) {
                    editingCategory = null
                }
            },
        )
    }
    deletingCategory?.let { category ->
        ConfirmDeleteDialog(
            title = "Eliminar categoría",
            message = "«${category.displayName}» se quitará de ${category.itemCount} elemento(s). " +
                "El contenido no se elimina, solo queda sin categoría.",
            busy = state.busy,
            onDismiss = { deletingCategory = null },
            onConfirm = { model.deleteCategory(category.id) { deletingCategory = null } },
        )
    }
    editingTag?.let { tag ->
        TagFormDialog(
            initial = tag.takeIf { it.id != 0L },
            busy = state.busy,
            onDismiss = { editingTag = null },
            onConfirm = { name, color ->
                model.saveTag(tag.takeIf { it.id != 0L }?.id, name, color) { editingTag = null }
            },
        )
    }
    deletingTag?.let { tag ->
        ConfirmDeleteDialog(
            title = "Eliminar etiqueta",
            message = "«#${tag.displayName}» se quitará de ${tag.itemCount} elemento(s). " +
                "Los elementos no se eliminan.",
            busy = state.busy,
            onDismiss = { deletingTag = null },
            onConfirm = { model.deleteTag(tag.id) { deletingTag = null } },
        )
    }
    movingItem?.let { item ->
        MoveItemDialog(
            item = item,
            folders = state.folders,
            busy = state.busy,
            onDismiss = { movingItem = null },
            onConfirm = { target ->
                model.moveItem(item.type, item.id, target) { movingItem = null }
            },
        )
    }
}

@Composable
private fun FolderList(
    state: FoldersUiState,
    onEdit: (Folder) -> Unit,
    onAddChild: (Folder) -> Unit,
    onDelete: (Folder) -> Unit,
    onToggle: (Long) -> Unit,
    onMoveItem: (OrganizationItem) -> Unit,
) {
    if (state.folders.isEmpty()) {
        OrganizationEmpty(
            title = "Sin carpetas todavía",
            message = "Crea carpetas y subcarpetas para organizar notas, tareas, tablas y eventos.",
        )
        return
    }
    val visible = visibleFolders(state.folders, state.expanded)
    val hasChildren = state.folders.groupBy { it.parentId }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag("folder_list"),
    ) {
        items(visible, key = { it.id }) { folder ->
            val expandedRow = state.expanded.isEmpty() || folder.id in state.expanded
            FolderRow(
                folder = folder,
                depth = folderDepth(folder, state.folders),
                path = folderPath(folder, state.folders),
                expandable = hasChildren[folder.id].orEmpty().isNotEmpty(),
                expanded = expandedRow,
                contents = if (expandedRow) state.itemsByFolder[folder.id].orEmpty() else emptyList(),
                onToggle = { onToggle(folder.id) },
                onEdit = { onEdit(folder) },
                onAddChild = { onAddChild(folder) },
                onDelete = { onDelete(folder) },
                onMoveItem = onMoveItem,
            )
        }
    }
}

@Composable
private fun FolderRow(
    folder: Folder,
    depth: Int,
    path: String,
    expandable: Boolean,
    expanded: Boolean,
    contents: List<OrganizationItem>,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onAddChild: () -> Unit,
    onDelete: () -> Unit,
    onMoveItem: (OrganizationItem) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = (depth * 16).dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (expandable) {
                    IconButton(onClick = onToggle, modifier = Modifier.size(40.dp)) {
                        Icon(
                            if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                            contentDescription = if (expanded) "Contraer ${folder.displayName}" else "Expandir ${folder.displayName}",
                        )
                    }
                } else {
                    Spacer(Modifier.width(40.dp))
                }
                Box(
                    Modifier
                        .size(40.dp)
                        .background(colorFromHex(folder.color).copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        folderIconVector(folder.icon),
                        contentDescription = null,
                        tint = colorFromHex(folder.color),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(folder.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${folder.itemCount} elemento(s) · $path",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (folder.description.isNotBlank()) {
                        Text(
                            folder.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Acciones de ${folder.displayName}")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Editar") },
                            onClick = { menu = false; onEdit() },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Añadir subcarpeta") },
                            onClick = { menu = false; onAddChild() },
                            leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onDelete() },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                        )
                    }
                }
            }
            if (contents.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(start = (depth * 16).dp + 56.dp, end = 12.dp))
                contents.forEach { item ->
                    FolderContentRow(item = item, onMove = { onMoveItem(item) })
                }
            }
        }
    }
}

@Composable
private fun FolderContentRow(item: OrganizationItem, onMove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            contentTypeIcon(item.type),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            item.displayTitle,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onMove, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.AutoMirrored.Outlined.DriveFileMove,
                contentDescription = "Mover ${item.displayTitle} a otra carpeta",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun MoveItemDialog(
    item: OrganizationItem,
    folders: List<Folder>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit,
) {
    val targets = listOf<Folder?>(null) + folders.filter { it.id != item.folderId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mover «${item.displayTitle}»") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                targets.forEach { target ->
                    TextButton(
                        onClick = { onConfirm(target?.id) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            target?.let { folderIconVector(it.icon) } ?: Icons.Outlined.FolderOff,
                            contentDescription = null,
                            tint = target?.let { colorFromHex(it.color) }
                                ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            target?.let { folderPath(it, folders) } ?: "Sin carpeta",
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") }
        },
    )
}

@Composable
private fun CategoryList(
    categories: List<Category>,
    onEdit: (Category) -> Unit,
    onDelete: (Category) -> Unit,
) {
    if (categories.isEmpty()) {
        OrganizationEmpty(
            title = "Sin categorías",
            message = "Las categorías agrupan contenido por tema: trabajo, personal, estudios…",
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag("category_list"),
    ) {
        items(categories, key = { it.id }) { category ->
            SimpleOrganizationRow(
                name = category.displayName,
                count = category.itemCount,
                color = category.color,
                icon = Icons.Outlined.Category,
                onEdit = { onEdit(category) },
                onDelete = { onDelete(category) },
            )
        }
    }
}

@Composable
private fun TagList(
    tags: List<Tag>,
    onEdit: (Tag) -> Unit,
    onDelete: (Tag) -> Unit,
) {
    if (tags.isEmpty()) {
        OrganizationEmpty(
            title = "Sin etiquetas",
            message = "Las etiquetas se comparten con las notas de la Etapa 3 y pueden combinarse.",
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag("tag_list"),
    ) {
        items(tags, key = { it.id }) { tag ->
            SimpleOrganizationRow(
                name = "#${tag.displayName}",
                count = tag.itemCount,
                color = tag.color,
                icon = Icons.AutoMirrored.Outlined.Label,
                onEdit = { onEdit(tag) },
                onDelete = { onDelete(tag) },
            )
        }
    }
}

@Composable
private fun SimpleOrganizationRow(
    name: String,
    count: Int,
    color: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).background(colorFromHex(color).copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = colorFromHex(color))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "$count elemento(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Acciones de $name")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Editar") },
                        onClick = { menu = false; onEdit() },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderFormDialog(
    initial: Folder?,
    folders: List<Folder>,
    defaultParentId: Long?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, FolderIcon, String, Long?, () -> Unit) -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var icon by remember(initial?.id) { mutableStateOf(initial?.icon ?: FolderIcon.FOLDER) }
    var color by remember(initial?.id) { mutableStateOf(initial?.color ?: ORGANIZATION_COLOR_PALETTE.first()) }
    var parentId by remember(initial?.id) { mutableStateOf(defaultParentId) }
    var parentDialog by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val excluded = remember(initial?.id, folders) {
        initial?.id?.let { id -> setOf(id) + descendantsOf(id, folders) }.orEmpty()
    }
    val parent = folders.firstOrNull { it.id == parentId }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (initial == null) "Nueva carpeta" else "Editar carpeta") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; localError = null },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("folder_name"),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(500) },
                    label = { Text("Descripción (opcional)") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Icono", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FolderIcon.entries.forEach { option ->
                        val selected = option == icon
                        IconButton(
                            onClick = { icon = option },
                            modifier = Modifier
                                .size(42.dp)
                                .background(
                                    if (selected) colorFromHex(color).copy(alpha = 0.16f)
                                    else Color.Transparent,
                                    CircleShape,
                                ),
                        ) {
                            Icon(
                                folderIconVector(option),
                                contentDescription = option.label,
                                tint = if (selected) colorFromHex(color)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text("Color", style = MaterialTheme.typography.labelLarge)
                ColorPickerRow(selected = color, onSelect = { color = it })
                Text("Carpeta superior", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { parentDialog = true }, enabled = !busy) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(parent?.displayName ?: "Sin carpeta (raíz)")
                }
                if (initial != null) {
                    Text(
                        "Al cambiar de nivel no se permite mover una carpeta dentro de sí misma o de una subcarpeta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                localError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    val error = validateFolderName(name)
                    if (error != null) {
                        localError = error
                    } else {
                        onConfirm(name.trim(), description.trim(), icon, color, parentId) {
                            localError = null
                        }
                    }
                },
                modifier = Modifier.testTag("folder_confirm"),
            ) { Text(if (initial == null) "Crear" else "Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
    if (parentDialog) {
        FolderPickerDialog(
            title = "Carpeta superior",
            folders = folders,
            selectedId = parentId,
            excludeIds = excluded,
            onSelect = { parentId = it; parentDialog = false },
            onDismiss = { parentDialog = false },
        )
    }
}

@Composable
private fun CategoryFormDialog(
    initial: Category?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember(initial?.id) { mutableStateOf(initial?.color ?: ORGANIZATION_COLOR_PALETTE.first()) }
    var localError by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (initial == null) "Nueva categoría" else "Editar categoría") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; localError = null },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("category_name"),
                )
                Text("Color", style = MaterialTheme.typography.labelLarge)
                ColorPickerRow(selected = color, onSelect = { color = it })
                Text(
                    "Los nombres no distinguen mayúsculas de minúsculas para evitar duplicados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                localError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    val error = validateCategoryName(name)
                    if (error != null) localError = error else onConfirm(name.trim(), color)
                },
                modifier = Modifier.testTag("category_confirm"),
            ) { Text(if (initial == null) "Crear" else "Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}

@Composable
private fun TagFormDialog(
    initial: Tag?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember(initial?.id) { mutableStateOf(initial?.color ?: ORGANIZATION_COLOR_PALETTE.first()) }
    var localError by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (initial == null) "Nueva etiqueta" else "Editar etiqueta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; localError = null },
                    label = { Text("Nombre") },
                    singleLine = true,
                    prefix = { Text("#") },
                    modifier = Modifier.fillMaxWidth().testTag("tag_name"),
                )
                Text("Color", style = MaterialTheme.typography.labelLarge)
                ColorPickerRow(selected = color, onSelect = { color = it })
                Text(
                    "La etiqueta se reutiliza en notas, tareas, tablas y eventos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                localError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    val error = validateTagName(name)
                    if (error != null) localError = error else onConfirm(name.trim(), color)
                },
                modifier = Modifier.testTag("tag_confirm"),
            ) { Text(if (initial == null) "Crear" else "Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}

@Composable
private fun DeleteFolderDialog(
    folder: Folder,
    folders: List<Folder>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val parent = folders.firstOrNull { it.id == folder.parentId }
    val childCount = folders.count { it.parentId == folder.id }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Eliminar carpeta") },
        text = {
            Text(
                "Se eliminará «${folder.displayName}». Sus $childCount subcarpeta(s) y sus " +
                    "${folder.itemCount} elemento(s) se moverán a " +
                    (parent?.let { "«${it.displayName}»" } ?: "la raíz") +
                    ". El contenido no se borra."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    message: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}

private fun visibleFolders(folders: List<Folder>, expanded: Set<Long>): List<Folder> {
    if (expanded.isEmpty()) return orderedFolders(folders)
    val byId = folders.associateBy { it.id }
    return orderedFolders(folders).filter { folder ->
        var cursor = folder.parentId
        var visible = true
        while (cursor != null) {
            if (cursor !in expanded) {
                visible = false
                break
            }
            cursor = byId[cursor]?.parentId
        }
        visible
    }
}
