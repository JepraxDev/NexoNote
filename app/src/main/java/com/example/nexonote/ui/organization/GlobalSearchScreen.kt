package com.example.nexonote.ui.organization

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexonote.domain.model.ContentType
import com.example.nexonote.domain.model.OrganizationItem
import com.example.nexonote.domain.model.SavedView
import com.example.nexonote.domain.model.SearchDateFilter
import com.example.nexonote.domain.model.SearchFilters
import com.example.nexonote.domain.model.SearchSort
import com.example.nexonote.domain.model.SearchStateFilter
import com.example.nexonote.domain.model.describe
import com.example.nexonote.domain.model.suggestedViews
import com.example.nexonote.domain.model.validateSavedViewName
import com.example.nexonote.ui.tables.colorFromHex

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GlobalSearchRoute(
    model: SearchViewModel,
    onBack: () -> Unit,
    onOpen: (OrganizationItem) -> Unit,
) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showSave by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SavedView?>(null) }

    LaunchedEffect(state.operationError) {
        state.operationError?.let {
            snackbar.showSnackbar(it)
            model.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Búsqueda global") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { showSave = true }) {
                        Icon(Icons.Outlined.BookmarkAdd, contentDescription = "Guardar vista")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.busy) {
                androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            OutlinedTextField(
                value = state.filters.query,
                onValueChange = model::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("global_search_field"),
                placeholder = { Text("Buscar en notas, tareas, tablas y eventos…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.filters.query.isNotEmpty()) {
                        IconButton(onClick = { model.setQuery("") }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Borrar búsqueda")
                        }
                    }
                },
                singleLine = true,
            )
            SavedViewsRow(
                views = state.savedViews,
                onApply = model::applyView,
                onRename = { renaming = it },
                onDelete = { view -> model.deleteView(view.id) },
                onSaveSuggested = { name, filters -> model.saveSuggested(name, filters) },
            )
            FilterControls(
                filters = state.filters,
                onToggleType = model::toggleType,
                onFavorite = model::setFavoriteOnly,
                onDate = model::setDate,
                onState = model::setState,
                onSort = model::setSort,
                onAdvanced = { showAdvanced = true },
                onClear = model::clearFilters,
            )
            if (!state.loading && state.error == null && state.results.isEmpty()) {
                OrganizationEmpty(
                    title = if (state.hasQuery) "Sin resultados" else "Busca en todo NexoNote",
                    message = if (state.hasQuery) {
                        "Prueba con otro texto o quita algunos filtros."
                    } else {
                        "Escribe un término o elige una vista guardada para empezar."
                    },
                )
            } else if (state.error != null) {
                OrganizationError(state.error.orEmpty(), model::retry)
            } else if (state.loading) {
                OrganizationLoading()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("search_results"),
                ) {
                    ContentType.entries.forEach { type ->
                        val group = state.results.filter { it.type == type }
                        if (group.isNotEmpty()) {
                            item(key = "search_header_${type.name}") {
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        contentTypeIcon(type),
                                        contentDescription = null,
                                        tint = contentTypeColor(type),
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("${type.label} · ${group.size}", style = MaterialTheme.typography.titleSmall)
                                }
                            }
                            items(group, key = { it.key }) { item ->
                                SearchResultRow(
                                    item = item,
                                    onOpen = { onOpen(item) },
                                    onFavorite = { model.toggleFavorite(item) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSave) {
        SavedViewNameDialog(
            title = "Guardar vista",
            initial = "",
            onDismiss = { showSave = false },
            onConfirm = { name -> model.saveView(name) { showSave = false } },
        )
    }
    renaming?.let { view ->
        SavedViewNameDialog(
            title = "Renombrar vista",
            initial = view.name,
            onDismiss = { renaming = null },
            onConfirm = { name -> model.updateView(view, name) { renaming = null } },
        )
    }
    if (showAdvanced) {
        AdvancedFiltersDialog(
            state = state,
            onCategory = model::setCategory,
            onFolder = model::setFolder,
            onToggleTag = model::toggleTag,
            onSort = model::setSort,
            onDismiss = { showAdvanced = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedViewsRow(
    views: List<SavedView>,
    onApply: (SavedView) -> Unit,
    onRename: (SavedView) -> Unit,
    onDelete: (SavedView) -> Unit,
    onSaveSuggested: (String, SearchFilters) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (views.isEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(suggestedViews(), key = { it.first }) { (name, filters) ->
                    AssistChip(
                        onClick = { onSaveSuggested(name, filters) },
                        label = { Text("$name +") },
                        leadingIcon = { Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(views, key = { it.id }) { view ->
                    var menu by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = false,
                            onClick = { onApply(view) },
                            label = {
                                Text(view.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                        )
                        Box {
                            IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Outlined.MoreVert,
                                    contentDescription = "Acciones de la vista ${view.displayName}",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Renombrar") },
                                    onClick = { menu = false; onRename(view) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                                    onClick = { menu = false; onDelete(view) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterControls(
    filters: SearchFilters,
    onToggleType: (ContentType) -> Unit,
    onFavorite: (Boolean) -> Unit,
    onDate: (SearchDateFilter) -> Unit,
    onState: (SearchStateFilter) -> Unit,
    onSort: (SearchSort) -> Unit,
    onAdvanced: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChipRow(filters.types, onToggleType)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filters.favoriteOnly,
                onClick = { onFavorite(!filters.favoriteOnly) },
                label = { Text("Favoritos") },
                leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
            FilterMenuButton(
                label = filters.date.label,
                options = SearchDateFilter.entries.map { it.label },
                onSelect = { label -> SearchDateFilter.entries.firstOrNull { it.label == label }?.let(onDate) },
            )
            FilterMenuButton(
                label = filters.state.label,
                options = SearchStateFilter.entries.map { it.label },
                onSelect = { label -> SearchStateFilter.entries.firstOrNull { it.label == label }?.let(onState) },
            )
            FilterMenuButton(
                label = filters.sort.label,
                options = SearchSort.entries.map { it.label },
                onSelect = { label -> SearchSort.entries.firstOrNull { it.label == label }?.let(onSort) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onAdvanced) {
                Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Filtros")
            }
            ClearFiltersButton(onClear)
        }
    }
}

@Composable
private fun FilterMenuButton(
    label: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, shape = RoundedCornerShape(50)) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedFiltersDialog(
    state: SearchUiState,
    onCategory: (Long?) -> Unit,
    onFolder: (Long?, Boolean) -> Unit,
    onToggleTag: (Long) -> Unit,
    onSort: (SearchSort) -> Unit,
    onDismiss: () -> Unit,
) {
    var categoryDialog by remember { mutableStateOf(false) }
    var folderDialog by remember { mutableStateOf(false) }
    var tagDialog by remember { mutableStateOf(false) }
    val category = state.categories.firstOrNull { it.id == state.filters.categoryId }
    val folder = state.folders.firstOrNull { it.id == state.filters.folderId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filtros avanzados") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Categoría", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { categoryDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Category, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(category?.displayName ?: "Cualquier categoría")
                }
                Text("Carpeta", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { folderDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(folder?.displayName ?: "Cualquier carpeta")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = state.filters.includeSubfolders,
                        onCheckedChange = { onFolder(state.filters.folderId, it) },
                        enabled = state.filters.folderId != null,
                    )
                    Text("Incluir subcarpetas", style = MaterialTheme.typography.bodyMedium)
                }
                Text("Etiquetas (varias)", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { tagDialog = true }) {
                        Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Elegir etiquetas (${state.filters.tagIds.size})")
                    }
                }
                if (state.filters.tagIds.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.filters.tagIds.forEach { id ->
                            val tag = state.tags.firstOrNull { it.id == id } ?: return@forEach
                            AssistChip(
                                onClick = { onToggleTag(id) },
                                label = { Text("#${tag.name}") },
                                leadingIcon = {
                                    Box(
                                        Modifier
                                            .size(10.dp)
                                            .background(colorFromHex(tag.color), androidx.compose.foundation.shape.CircleShape)
                                    )
                                },
                            )
                        }
                    }
                }
                Text("Orden", style = MaterialTheme.typography.labelLarge)
                FilterMenuButton(
                    label = state.filters.sort.label,
                    options = SearchSort.entries.map { it.label },
                    onSelect = { label -> SearchSort.entries.firstOrNull { it.label == label }?.let(onSort) },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
        dismissButton = {
            TextButton(onClick = { onCategory(null); onFolder(null, false) }) { Text("Quitar filtros") }
        },
    )
    if (categoryDialog) {
        CategoryPickerDialog(
            categories = state.categories,
            selectedId = state.filters.categoryId,
            onSelect = { onCategory(it); categoryDialog = false },
            onDismiss = { categoryDialog = false },
        )
    }
    if (folderDialog) {
        FolderPickerDialog(
            title = "Filtrar por carpeta",
            folders = state.folders,
            selectedId = state.filters.folderId,
            onSelect = { onFolder(it, true); folderDialog = false },
            onDismiss = { folderDialog = false },
        )
    }
    if (tagDialog) {
        TagFilterDialog(
            tags = state.tags,
            selected = state.filters.tagIds,
            onToggle = onToggleTag,
            onDismiss = { tagDialog = false },
        )
    }
}

@Composable
private fun TagFilterDialog(
    tags: List<com.example.nexonote.domain.model.Tag>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filtrar por etiquetas") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                if (tags.isEmpty()) {
                    item { Text("Aún no hay etiquetas.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(tags, key = { it.id }) { tag ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = tag.id in selected,
                            onCheckedChange = { onToggle(tag.id) },
                        )
                        Text("#${tag.name}", Modifier.weight(1f))
                        Text("${tag.itemCount}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
    )
}

@Composable
private fun SearchResultRow(
    item: OrganizationItem,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                contentTypeIcon(item.type),
                contentDescription = item.type.singular,
                tint = contentTypeColor(item.type),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MetaLine(item)
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    if (item.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (item.favorite) "Quitar de favoritos" else "Marcar como favorito",
                    tint = if (item.favorite) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SavedViewNameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(if (initial.length <= 60) initial else "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Nombre de la vista") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("saved_view_name"),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val validation = validateSavedViewName(name)
                if (validation != null) error = validation else onConfirm(name.trim())
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
