package com.example.nexonote.ui.organization

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.nexonote.domain.model.Category
import com.example.nexonote.domain.model.ContentType
import com.example.nexonote.domain.model.Folder
import com.example.nexonote.domain.model.FolderIcon
import com.example.nexonote.domain.model.ORGANIZATION_COLOR_PALETTE
import com.example.nexonote.domain.model.Tag
import com.example.nexonote.domain.model.folderPath
import com.example.nexonote.navigation.OrganizationRoutes
import com.example.nexonote.ui.tables.colorFromHex

fun folderIconVector(icon: FolderIcon): ImageVector = when (icon) {
    FolderIcon.FOLDER -> Icons.Outlined.Folder
    FolderIcon.WORK -> Icons.Outlined.Work
    FolderIcon.HOME -> Icons.Outlined.Home
    FolderIcon.STAR -> Icons.Outlined.Star
    FolderIcon.LIGHTBULB -> Icons.Outlined.Lightbulb
    FolderIcon.BOOK -> Icons.AutoMirrored.Outlined.MenuBook
    FolderIcon.CALENDAR -> Icons.Outlined.CalendarMonth
    FolderIcon.FLAG -> Icons.Outlined.Flag
    FolderIcon.LIST -> Icons.AutoMirrored.Outlined.List
    FolderIcon.ARCHIVE -> Icons.Outlined.Archive
}

fun contentTypeIcon(type: ContentType): ImageVector = when (type) {
    ContentType.NOTE -> Icons.AutoMirrored.Outlined.StickyNote2
    ContentType.TASK -> Icons.Outlined.CheckCircle
    ContentType.TABLE -> Icons.Outlined.TableChart
    ContentType.EVENT -> Icons.Outlined.CalendarMonth
}

fun contentTypeColor(type: ContentType): Color = when (type) {
    ContentType.NOTE -> Color(0xFF6A5AA8)
    ContentType.TASK -> Color(0xFF2E7D32)
    ContentType.TABLE -> Color(0xFF0061A4)
    ContentType.EVENT -> Color(0xFFB3261E)
}

fun folderDepth(folder: Folder, folders: List<Folder>): Int {
    val byId = folders.associateBy { it.id }
    var depth = 0
    var cursor = folder.parentId
    val visited = mutableSetOf<Long>()
    while (cursor != null && visited.add(cursor)) {
        depth++
        cursor = byId[cursor]?.parentId
    }
    return depth.coerceAtMost(8)
}

/** Flattens the folder tree in display order so indentation matches the hierarchy. */
fun orderedFolders(folders: List<Folder>): List<Folder> {
    val byParent = folders.groupBy { it.parentId }
    val result = mutableListOf<Folder>()
    val visited = mutableSetOf<Long>()
    fun visit(parent: Long?) {
        byParent[parent].orEmpty()
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .forEach { folder ->
                if (visited.add(folder.id)) {
                    result += folder
                    visit(folder.id)
                }
            }
    }
    visit(null)
    // Orphaned folders (missing parent) must still be reachable for repair.
    folders.filterNot { it.id in visited }.sortedBy { it.name }.forEach { result += it }
    return result
}

@Composable
fun ColorPickerRow(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ORGANIZATION_COLOR_PALETTE.forEach { hex ->
            val isSelected = hex.equals(selected, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(colorFromHex(hex), CircleShape)
                    .then(
                        if (isSelected) {
                            Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable(role = Role.RadioButton) { onSelect(hex) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = "Color seleccionado",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun OrganizationFields(
    folderId: Long?,
    categoryId: Long?,
    folders: List<Folder>,
    categories: List<Category>,
    onFolderChange: (Long?) -> Unit,
    onCategoryChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var folderDialog by remember { mutableStateOf(false) }
    var categoryDialog by remember { mutableStateOf(false) }
    val folder = folders.firstOrNull { it.id == folderId }
    val category = categories.firstOrNull { it.id == categoryId }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Organización", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { folderDialog = true },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    if (folder == null) Icons.Outlined.Folder else folderIconVector(folder.icon),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    folder?.displayName ?: "Sin carpeta",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = { categoryDialog = true },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.Category, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    category?.displayName ?: "Sin categoría",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (folder != null) {
            Text(
                folderPath(folder, folders),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (folderDialog) {
        FolderPickerDialog(
            title = "Mover a carpeta",
            folders = folders,
            selectedId = folderId,
            onSelect = {
                folderDialog = false
                onFolderChange(it)
            },
            onDismiss = { folderDialog = false },
        )
    }
    if (categoryDialog) {
        CategoryPickerDialog(
            categories = categories,
            selectedId = categoryId,
            onSelect = {
                categoryDialog = false
                onCategoryChange(it)
            },
            onDismiss = { categoryDialog = false },
        )
    }
}

@Composable
fun FolderPickerDialog(
    title: String,
    folders: List<Folder>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
    excludeIds: Set<Long> = emptySet(),
) {
    val ordered = orderedFolders(folders).filterNot { it.id in excludeIds }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                item {
                    PickerRow(
                        label = "Sin carpeta (raíz)",
                        selected = selectedId == null,
                        icon = Icons.Outlined.FolderOpen,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { onSelect(null) },
                    )
                }
                items(ordered, key = { it.id }) { folder ->
                    val depth = folderDepth(folder, folders)
                    PickerRow(
                        label = folder.displayName,
                        selected = selectedId == folder.id,
                        icon = folderIconVector(folder.icon),
                        color = colorFromHex(folder.color),
                        indent = depth,
                        supporting = "${folder.itemCount} elemento(s)",
                        onClick = { onSelect(folder.id) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
fun CategoryPickerDialog(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categoría") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                item {
                    PickerRow(
                        label = "Sin categoría",
                        selected = selectedId == null,
                        icon = Icons.Outlined.Category,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { onSelect(null) },
                    )
                }
                items(categories, key = { it.id }) { category ->
                    PickerRow(
                        label = category.displayName,
                        selected = selectedId == category.id,
                        icon = Icons.Outlined.Category,
                        color = colorFromHex(category.color),
                        supporting = "${category.itemCount} elemento(s)",
                        onClick = { onSelect(category.id) },
                    )
                }
                if (categories.isEmpty()) {
                    item {
                        Text(
                            "Aún no hay categorías. Puedes crearlas en Carpetas → Categorías.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun PickerRow(
    label: String,
    selected: Boolean,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    supporting: String? = null,
    indent: Int = 0,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(start = (indent * 20).dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            supporting?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Icon(Icons.Outlined.Check, contentDescription = "Seleccionado", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagSelectionSection(
    selected: List<String>,
    available: List<Tag>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Etiquetas",
) {
    var dialog by remember { mutableStateOf(false) }
    val byName = available.associateBy { it.name.lowercase() }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (selected.isEmpty()) {
            Text(
                "Añade etiquetas para agrupar y filtrar este elemento.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                selected.forEach { name ->
                    val tag = byName[name.lowercase()]
                    InputChip(
                        selected = true,
                        onClick = { onRemove(name) },
                        label = { Text("#$name") },
                        leadingIcon = {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .background(colorFromHex(tag?.color ?: "#6750A4"), CircleShape)
                            )
                        },
                        trailingIcon = {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Quitar etiqueta $name",
                                modifier = Modifier.size(14.dp),
                            )
                        },
                    )
                }
            }
        }
        OutlinedButton(onClick = { dialog = true }) {
            Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Añadir etiqueta")
        }
    }
    if (dialog) {
        TagPickerDialog(
            selected = selected,
            available = available,
            onToggle = { name, checked -> if (checked) onAdd(name) else onRemove(name) },
            onDismiss = { dialog = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPickerDialog(
    selected: List<String>,
    available: List<Tag>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val normalizedQuery = query.trim().lowercase()
    val filtered = available.filter { it.name.contains(normalizedQuery) }
    val canCreate = normalizedQuery.isNotBlank() &&
        available.none { it.name.equals(normalizedQuery, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Etiquetas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar o crear etiqueta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (canCreate) {
                    AssistChip(
                        onClick = {
                            onToggle(normalizedQuery, true)
                            query = ""
                        },
                        label = { Text("Crear «$normalizedQuery»") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null) },
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    if (filtered.isEmpty() && !canCreate) {
                        item {
                            Text(
                                "No hay etiquetas que coincidan.",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(filtered, key = { it.id }) { tag ->
                        val checked = selected.any { it.equals(tag.name, ignoreCase = true) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(tag.name, !checked) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .background(colorFromHex(tag.color), CircleShape)
                            )
                            Text("#${tag.name}", Modifier.weight(1f))
                            Text(
                                "${tag.itemCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (checked) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = "Seleccionada",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
    )
}

@Composable
fun OrganizationLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun OrganizationEmpty(title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun OrganizationError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}

@Composable
fun OrganizationShortcuts(onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Organización", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShortcutButton("Carpetas", Icons.Outlined.Folder, Modifier.weight(1f)) { onOpen(OrganizationRoutes.FOLDERS) }
            ShortcutButton("Favoritos", Icons.Outlined.Star, Modifier.weight(1f)) { onOpen(OrganizationRoutes.FAVORITES) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShortcutButton("Buscar", Icons.Outlined.Search, Modifier.weight(1f)) { onOpen(OrganizationRoutes.SEARCH) }
            ShortcutButton("Estadísticas", Icons.Outlined.BarChart, Modifier.weight(1f)) { onOpen(OrganizationRoutes.STATISTICS) }
        }
    }
}

@Composable
private fun ShortcutButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SectionDivider() {
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
}

@Composable
fun FilterChipRow(
    selectedTypes: Set<ContentType>,
    onToggle: (ContentType) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ContentType.entries.forEach { type ->
            FilterChip(
                selected = type in selectedTypes,
                onClick = { onToggle(type) },
                label = { Text(type.label) },
                leadingIcon = {
                    Icon(
                        contentTypeIcon(type),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
    }
}

@Composable
fun ClearFiltersButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("Limpiar")
    }
}

@Composable
fun OrganizationIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = contentDescription) }
}
