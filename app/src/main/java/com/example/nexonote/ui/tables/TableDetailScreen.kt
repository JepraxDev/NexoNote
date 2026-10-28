package com.example.nexonote.ui.tables

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexonote.domain.model.ColumnOption
import com.example.nexonote.domain.model.ColumnType
import com.example.nexonote.domain.model.CustomColumn
import com.example.nexonote.domain.model.CustomTable
import com.example.nexonote.domain.model.TableRow
import com.example.nexonote.domain.model.TableSort

private val PRIMARY_CELL_WIDTH = 220.dp
private val CELL_WIDTH = 160.dp
private val ROW_ACTION_WIDTH = 52.dp
private val TABLE_ROW_HEIGHT = 60.dp

private data class OptionFormTarget(
    val columnId: Long,
    val option: ColumnOption?,
)

private data class CellEditTarget(
    val rowId: Long,
    val columnId: Long,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableDetailRoute(
    model: TableDetailViewModel,
    onBack: () -> Unit,
    onOpenTable: (Long) -> Unit,
) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var showFilters by remember { mutableStateOf(false) }
    var showAddFilter by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showColumns by remember { mutableStateOf(false) }
    var showColumnForm by remember { mutableStateOf(false) }
    var editingColumn by remember { mutableStateOf<CustomColumn?>(null) }
    var deletingColumn by remember { mutableStateOf<CustomColumn?>(null) }
    var optionsColumnId by remember { mutableStateOf<Long?>(null) }
    var optionFormTarget by remember { mutableStateOf<OptionFormTarget?>(null) }
    var deletingOption by remember { mutableStateOf<ColumnOption?>(null) }
    var editingRowId by remember { mutableStateOf<Long?>(null) }
    var cellTarget by remember { mutableStateOf<CellEditTarget?>(null) }
    var showEditTable by remember { mutableStateOf(false) }
    var showDeleteTable by remember { mutableStateOf(false) }
    var tableMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.operationError) {
        state.operationError?.let {
            snackbar.showSnackbar(it)
            model.dismissError()
        }
    }
    val editingRow = state.rows.firstOrNull { it.id == editingRowId }
    val editingCellRow = state.rows.firstOrNull { it.id == cellTarget?.rowId }
    val editingCellColumn = state.columns.firstOrNull { it.id == cellTarget?.columnId }
    val optionsColumn = state.columns.firstOrNull { it.id == optionsColumnId }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        state.table?.let {
                            TableIconBadge(
                                icon = it.icon,
                                color = it.color,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(
                                text = state.table?.displayName ?: "Tabla",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            state.table?.let { table ->
                                Text(
                                    text = "${table.columnCount} columnas",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            model.setDisplayMode(
                                if (state.displayMode == TableDisplayMode.GRID)
                                    TableDisplayMode.CARDS else TableDisplayMode.GRID
                            )
                        },
                        modifier = Modifier.testTag("table_view_toggle"),
                    ) {
                        Icon(
                            imageVector = if (state.displayMode == TableDisplayMode.GRID)
                                Icons.Outlined.ViewAgenda else Icons.Outlined.GridOn,
                            contentDescription = if (state.displayMode == TableDisplayMode.GRID)
                                "Ver como tarjetas" else "Ver como tabla",
                        )
                    }
                    IconButton(onClick = { showColumns = true }) {
                        Icon(Icons.Outlined.ViewColumn, contentDescription = "Gestionar columnas")
                    }
                    Box {
                        IconButton(onClick = { tableMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Acciones de tabla")
                        }
                        DropdownMenu(expanded = tableMenu, onDismissRequest = { tableMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Editar tabla") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                onClick = {
                                    tableMenu = false
                                    showEditTable = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicar tabla") },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                                onClick = {
                                    tableMenu = false
                                    model.duplicateTable { id -> onOpenTable(id) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar tabla") },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                onClick = {
                                    tableMenu = false
                                    showDeleteTable = true
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state.table != null && !state.missing) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (state.hasActiveQuery) {
                            model.resetQuery()
                        }
                        model.createRow { id -> editingRowId = id }
                    },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Nuevo registro") },
                    modifier = Modifier.testTag("new_row"),
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
            when {
                state.loading && state.table == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.missing -> {
                    MissingTable(onBack)
                }
                state.error != null && state.table == null -> {
                    DetailError(state.error.orEmpty(), model::retry)
                }
                state.table != null -> {
                    TableDetailContent(
                        state = state,
                        onSearchChange = model::setSearch,
                        onOpenFilters = { showFilters = true },
                        onRemoveFilter = model::removeFilterAt,
                        onClearSort = { model.setSort(null) },
                        onResetQuery = model::resetQuery,
                        onEditRow = { editingRowId = it.id },
                        onEditCell = { row, column -> cellTarget = CellEditTarget(row.id, column.id) },
                        onToggleCheckbox = { row, column, checked ->
                            model.updateCell(row.id, column.id, if (checked) "1" else "0")
                        },
                        onDuplicateRow = { row -> model.duplicateRow(row.id) },
                        onDeleteRow = { row -> model.deleteRow(row.id) },
                        onLoadMore = model::loadMore,
                        onCreateRow = {
                            if (state.hasActiveQuery) model.resetQuery()
                            model.createRow { id -> editingRowId = id }
                        },
                    )
                }
            }
            if (state.error != null && state.table != null) {
                TextButton(
                    onClick = model::retry,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                ) { Text("Reintentar") }
            }
        }
    }

    editingRow?.let { row ->
        RowEditorSheet(
            row = row,
            columns = state.columns,
            busy = state.busy,
            onDismiss = { editingRowId = null },
            onEditCell = { column -> cellTarget = CellEditTarget(row.id, column.id) },
            onToggleCheckbox = { column, checked ->
                model.updateCell(row.id, column.id, if (checked) "1" else "0")
            },
        )
    }

    if (editingCellRow != null && editingCellColumn != null) {
        CellEditorDialog(
            column = editingCellColumn,
            row = editingCellRow,
            onDismiss = { cellTarget = null },
            onCommit = { value ->
                model.updateCell(editingCellRow.id, editingCellColumn.id, value)
                cellTarget = null
            },
        )
    }

    if (showEditTable && state.table != null) {
        TableFormDialog(
            initial = state.table,
            busy = state.busy,
            onDismiss = { showEditTable = false },
            onConfirm = { input ->
                model.updateTable(input) { showEditTable = false }
            },
        )
    }
    if (showDeleteTable && state.table != null) {
        DeleteTableDialog(
            table = state.table!!,
            busy = state.busy,
            onDismiss = { showDeleteTable = false },
            onConfirm = {
                model.deleteTable {
                    showDeleteTable = false
                    onBack()
                }
            },
        )
    }

    if (showFilters) {
        FilterDialog(
            columns = state.columns,
            filters = state.filters,
            onDismiss = { showFilters = false },
            onAddFilter = { showAddFilter = true },
            onRemoveFilter = model::removeFilterAt,
            onOpenSort = {
                showFilters = false
                showSort = true
            },
            onClearAll = {
                model.resetQuery()
                showFilters = false
            },
        )
    }
    if (showAddFilter) {
        AddFilterDialog(
            columns = state.columns,
            onDismiss = { showAddFilter = false },
            onAdd = { filter ->
                model.addFilter(filter)
                showAddFilter = false
            },
        )
    }
    if (showSort) {
        SortDialog(
            columns = state.columns,
            sort = state.sort,
            onDismiss = { showSort = false },
            onApply = { sort ->
                model.setSort(sort)
                showSort = false
            },
        )
    }

    if (showColumns) {
        ColumnManagerDialog(
            columns = state.columns,
            onDismiss = { showColumns = false },
            onAddColumn = {
                editingColumn = null
                showColumnForm = true
            },
            onEditColumn = {
                editingColumn = it
                showColumnForm = true
            },
            onManageOptions = { optionsColumnId = it.id },
            onMoveColumn = { column, offset -> model.moveColumn(column.id, offset) },
            onDeleteColumn = { deletingColumn = it },
        )
    }
    if (showColumnForm) {
        ColumnFormDialog(
            initial = editingColumn,
            busy = state.busy,
            onDismiss = { showColumnForm = false },
            onConfirm = { input ->
                val current = editingColumn
                if (current == null) {
                    model.addColumn(input) { id ->
                        showColumnForm = false
                        if (input.type.usesOptions) optionsColumnId = id
                    }
                } else {
                    model.updateColumn(current.id, input) { showColumnForm = false }
                }
            },
        )
    }
    deletingColumn?.let { column ->
        DeleteColumnDialog(
            column = column,
            busy = state.busy,
            onDismiss = { deletingColumn = null },
            onConfirm = {
                model.deleteColumn(column.id) { deletingColumn = null }
            },
        )
    }

    optionsColumn?.let { column ->
        OptionManagerDialog(
            column = column,
            onDismiss = { optionsColumnId = null },
            onAddOption = { optionFormTarget = OptionFormTarget(column.id, null) },
            onEditOption = { optionFormTarget = OptionFormTarget(column.id, it) },
            onDeleteOption = { deletingOption = it },
        )
    }
    optionFormTarget?.let { target ->
        OptionFormDialog(
            initial = target.option,
            busy = state.busy,
            onDismiss = { optionFormTarget = null },
            onConfirm = { input ->
                if (target.option == null) {
                    model.addOption(target.columnId, input) { optionFormTarget = null }
                } else {
                    model.updateOption(target.option.id, input) { optionFormTarget = null }
                }
            },
        )
    }
    deletingOption?.let { option ->
        DeleteOptionDialog(
            option = option,
            busy = state.busy,
            onDismiss = { deletingOption = null },
            onConfirm = {
                model.deleteOption(option.id)
                deletingOption = null
            },
        )
    }
}

@Composable
private fun TableDetailContent(
    state: TableDetailUiState,
    onSearchChange: (String) -> Unit,
    onOpenFilters: () -> Unit,
    onRemoveFilter: (Int) -> Unit,
    onClearSort: () -> Unit,
    onResetQuery: () -> Unit,
    onEditRow: (TableRow) -> Unit,
    onEditCell: (TableRow, CustomColumn) -> Unit,
    onToggleCheckbox: (TableRow, CustomColumn, Boolean) -> Unit,
    onDuplicateRow: (TableRow) -> Unit,
    onDeleteRow: (TableRow) -> Unit,
    onLoadMore: () -> Unit,
    onCreateRow: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.search,
                onValueChange = onSearchChange,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.search.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Limpiar búsqueda")
                        }
                    }
                },
                placeholder = { Text("Buscar registros") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f).testTag("row_search"),
            )
            BadgedBox(
                badge = {
                    if (state.filters.isNotEmpty()) {
                        Badge { Text(state.filters.size.toString()) }
                    }
                },
            ) {
                IconButton(onClick = onOpenFilters, modifier = Modifier.testTag("open_filters")) {
                    Icon(Icons.Outlined.FilterAlt, contentDescription = "Filtros y orden")
                }
            }
        }
        if (state.filters.isNotEmpty() || state.sort != null) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(state.filters, key = { index, _ -> index }) { index, filter ->
                    val column = state.columns.firstOrNull { it.id == filter.columnId }
                    if (column != null) {
                        InputChip(
                            selected = true,
                            onClick = { onRemoveFilter(index) },
                            label = {
                                Text(
                                    buildFilterLabel(filter, column),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "Quitar filtro",
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
                state.sort?.let { sort ->
                    val column = state.columns.firstOrNull { it.id == sort.columnId }
                    if (column != null) {
                        item {
                            InputChip(
                                selected = false,
                                onClick = onClearSort,
                                label = {
                                    Text("${column.displayName} ${if (sort.ascending) "↑" else "↓"}")
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "Quitar orden",
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }
                }
                item {
                    TextButton(onClick = onResetQuery) { Text("Limpiar") }
                }
            }
        }
        if (state.displayMode == TableDisplayMode.GRID) {
            RecordGrid(
                state = state,
                onEditRow = onEditRow,
                onEditCell = onEditCell,
                onToggleCheckbox = onToggleCheckbox,
                onDuplicateRow = onDuplicateRow,
                onDeleteRow = onDeleteRow,
                onLoadMore = onLoadMore,
                onCreateRow = onCreateRow,
                onResetQuery = onResetQuery,
            )
        } else {
            RecordCards(
                state = state,
                onEditRow = onEditRow,
                onDuplicateRow = onDuplicateRow,
                onDeleteRow = onDeleteRow,
                onLoadMore = onLoadMore,
                onCreateRow = onCreateRow,
                onResetQuery = onResetQuery,
            )
        }
    }
}

@Composable
private fun RecordGrid(
    state: TableDetailUiState,
    onEditRow: (TableRow) -> Unit,
    onEditCell: (TableRow, CustomColumn) -> Unit,
    onToggleCheckbox: (TableRow, CustomColumn, Boolean) -> Unit,
    onDuplicateRow: (TableRow) -> Unit,
    onDeleteRow: (TableRow) -> Unit,
    onLoadMore: () -> Unit,
    onCreateRow: () -> Unit,
    onResetQuery: () -> Unit,
) {
    val hScroll = rememberScrollState()
    val totalWidth = state.columns.sumOf { columnWidth(it).value.toDouble() }.dp + ROW_ACTION_WIDTH
    Row(Modifier.fillMaxSize().horizontalScroll(hScroll)) {
        Column(Modifier.width(totalWidth).fillMaxHeight()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.columns.forEach { column ->
                    HeaderCell(column)
                    VerticalDivider(Modifier.fillMaxHeight())
                }
                Box(Modifier.width(ROW_ACTION_WIDTH))
            }
            HorizontalDivider()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 104.dp),
            ) {
                if (state.rows.isEmpty()) {
                    item {
                        if (state.loading) {
                            Box(
                                Modifier.fillMaxWidth().padding(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(Modifier.size(28.dp))
                            }
                        } else {
                            EmptyRecords(
                                hasQuery = state.hasActiveQuery,
                                onCreateRow = onCreateRow,
                                onResetQuery = onResetQuery,
                            )
                        }
                    }
                } else {
                    items(state.rows, key = { it.id }) { row ->
                        RecordGridRow(
                            row = row,
                            columns = state.columns,
                            onEditRow = { onEditRow(row) },
                            onEditCell = { column -> onEditCell(row, column) },
                            onToggleCheckbox = { column, checked -> onToggleCheckbox(row, column, checked) },
                            onDuplicate = { onDuplicateRow(row) },
                            onDelete = { onDeleteRow(row) },
                        )
                        HorizontalDivider()
                    }
                }
                if (state.hasMore || state.loadingMore) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.loadingMore) {
                                CircularProgressIndicator(Modifier.size(26.dp))
                            } else {
                                TextButton(onClick = onLoadMore) { Text("Cargar más registros") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(column: CustomColumn) {
    Column(
        modifier = Modifier.width(columnWidth(column)).padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = columnIconVector(column.type),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = column.displayName + if (column.required) " *" else "",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 5.dp).weight(1f, fill = false),
            )
            if (column.primary) {
                Icon(
                    Icons.Outlined.ViewColumn,
                    contentDescription = "Columna principal",
                    modifier = Modifier.size(13.dp).padding(start = 3.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = column.type.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun RecordGridRow(
    row: TableRow,
    columns: List<CustomColumn>,
    onEditRow: () -> Unit,
    onEditCell: (CustomColumn) -> Unit,
    onToggleCheckbox: (CustomColumn, Boolean) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = TABLE_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        columns.forEach { column ->
            Box(
                modifier = Modifier
                    .width(columnWidth(column))
                    .heightIn(min = TABLE_ROW_HEIGHT)
                    .clickable {
                        if (column.primary) onEditRow() else onEditCell(column)
                    }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                when {
                    column.type == ColumnType.CHECKBOX -> {
                        Checkbox(
                            checked = row.value(column.id) == "1",
                            onCheckedChange = { onToggleCheckbox(column, it) },
                        )
                    }
                    column.primary -> {
                        Text(
                            text = row.displayTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    else -> {
                        CellValuePreview(
                            column = column,
                            row = row,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            VerticalDivider(Modifier.fillMaxHeight())
        }
        Box(Modifier.width(ROW_ACTION_WIDTH), contentAlignment = Alignment.Center) {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Acciones del registro")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Abrir registro") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    onClick = {
                        menu = false
                        onEditRow()
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

@Composable
private fun RecordCards(
    state: TableDetailUiState,
    onEditRow: (TableRow) -> Unit,
    onDuplicateRow: (TableRow) -> Unit,
    onDeleteRow: (TableRow) -> Unit,
    onLoadMore: () -> Unit,
    onCreateRow: () -> Unit,
    onResetQuery: () -> Unit,
) {
    if (state.rows.isEmpty()) {
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            EmptyRecords(
                hasQuery = state.hasActiveQuery,
                onCreateRow = onCreateRow,
                onResetQuery = onResetQuery,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(280.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 104.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.rows, key = { it.id }) { row ->
            RecordCard(
                row = row,
                columns = state.columns,
                onClick = { onEditRow(row) },
                onDuplicate = { onDuplicateRow(row) },
                onDelete = { onDeleteRow(row) },
            )
        }
        if (state.hasMore || state.loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    if (state.loadingMore) {
                        CircularProgressIndicator(Modifier.size(26.dp))
                    } else {
                        TextButton(onClick = onLoadMore) { Text("Cargar más registros") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordCard(
    row: TableRow,
    columns: List<CustomColumn>,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val details = columns.filterNot { it.primary }
        .mapNotNull { column -> row.value(column.id)?.let { column to it } }
        .take(3)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = row.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Acciones del registro")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
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
            if (details.isEmpty()) {
                Text(
                    "Sin datos adicionales",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                details.forEach { (column, _) ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = column.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CellValuePreview(column = column, row = row, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRecords(
    hasQuery: Boolean,
    onCreateRow: () -> Unit,
    onResetQuery: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = if (hasQuery) "No hay registros que coincidan" else "Todavía no hay registros",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = if (hasQuery)
                "Prueba a cambiar la búsqueda, los filtros o el orden."
            else
                "Añade el primer registro y rellena sus campos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasQuery) {
            TextButton(onClick = onCreateRow) { Text("Crear registro") }
        } else {
            TextButton(onClick = onResetQuery) { Text("Limpiar filtros") }
        }
    }
}

@Composable
private fun MissingTable(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Esta tabla ya no existe.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onBack) { Text("Volver a tablas") }
    }
}

@Composable
private fun DetailError(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onRetry) { Text("Reintentar") }
    }
}

private fun columnWidth(column: CustomColumn): Dp =
    if (column.primary) PRIMARY_CELL_WIDTH else CELL_WIDTH
