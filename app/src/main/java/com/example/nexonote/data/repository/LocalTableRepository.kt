package com.example.nexonote.data.repository

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.nexonote.data.local.ActivityEntity
import com.example.nexonote.data.local.ColumnOptionEntity
import com.example.nexonote.data.local.CustomCellEntity
import com.example.nexonote.data.local.CustomColumnEntity
import com.example.nexonote.data.local.CustomRowEntity
import com.example.nexonote.data.local.CustomTableEntity
import com.example.nexonote.data.local.EntryEntity
import com.example.nexonote.data.local.NexoDatabase
import com.example.nexonote.data.local.RecentTableRowEntity
import com.example.nexonote.data.local.TableSummaryEntity
import com.example.nexonote.domain.model.ColumnInput
import com.example.nexonote.domain.model.ColumnOption
import com.example.nexonote.domain.model.ColumnType
import com.example.nexonote.domain.model.CustomColumn
import com.example.nexonote.domain.model.CustomTable
import com.example.nexonote.domain.model.DEFAULT_COLUMN_NAME
import com.example.nexonote.domain.model.DEFAULT_TABLE_COLOR
import com.example.nexonote.domain.model.OptionInput
import com.example.nexonote.domain.model.RecentTableRow
import com.example.nexonote.domain.model.TableFilter
import com.example.nexonote.domain.model.TableFilterOperator
import com.example.nexonote.domain.model.TableIcon
import com.example.nexonote.domain.model.TableInput
import com.example.nexonote.domain.model.TablePage
import com.example.nexonote.domain.model.TableQuery
import com.example.nexonote.domain.model.TableRow
import com.example.nexonote.domain.model.TableSort
import com.example.nexonote.domain.model.displayCellValue
import com.example.nexonote.domain.model.normalizeCellValue
import com.example.nexonote.domain.model.primaryColumn
import com.example.nexonote.domain.model.startOfDay
import com.example.nexonote.domain.model.tableRowTitle
import com.example.nexonote.domain.model.validateColumnInput
import com.example.nexonote.domain.model.validateOptionInput
import com.example.nexonote.domain.model.validateTableInput
import com.example.nexonote.domain.repository.TableRepository
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class LocalTableRepository(
    private val database: NexoDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : TableRepository {
    private val tableDao = database.tableDao()
    private val entryDao = database.entryDao()

    override val tables: Flow<List<CustomTable>> = tableDao.observeTables().map { tables ->
        tables.map(TableSummaryEntity::toDomain)
    }

    override val recentRows: Flow<List<RecentTableRow>> = tableDao.observeRecentRows(5).map { rows ->
        rows.map(RecentTableRowEntity::toDomain)
    }

    override fun observeTable(id: Long): Flow<CustomTable?> =
        tableDao.observeTable(id).map { it?.toDomain() }

    override fun observeColumns(tableId: Long): Flow<List<CustomColumn>> =
        combine(tableDao.observeColumns(tableId), tableDao.observeOptions(tableId)) { columns, options ->
            val grouped = options.groupBy { it.columnId }
            columns.map { column -> column.toDomain(grouped[column.id].orEmpty()) }
        }

    override fun observeOptions(tableId: Long): Flow<List<ColumnOption>> =
        tableDao.observeOptions(tableId).map { options -> options.map(ColumnOptionEntity::toDomain) }

    override suspend fun findTable(id: Long): CustomTable? = tableDao.findTable(id)?.toDomain()

    override suspend fun findRow(rowId: Long): TableRow? {
        val row = tableDao.findRow(rowId) ?: return null
        return row.toDomain(tableDao.findCells(rowId))
    }

    override suspend fun createTable(input: TableInput): Long {
        validateTableInput(input)?.let { throw IllegalArgumentException(it) }
        val name = input.name.trim()
        val now = clock()
        var tableId = 0L
        database.withTransaction {
            tableId = entryDao.insert(
                EntryEntity(
                    title = name,
                    type = "TABLE",
                    modifiedAt = now,
                )
            )
            tableDao.insertTable(
                CustomTableEntity(
                    id = tableId,
                    name = name,
                    description = input.description.trim(),
                    icon = input.icon.name,
                    color = input.color.lowercase(Locale.ROOT),
                    createdAt = now,
                    modifiedAt = now,
                )
            )
            tableDao.insertColumn(
                CustomColumnEntity(
                    tableId = tableId,
                    name = DEFAULT_COLUMN_NAME,
                    type = ColumnType.SHORT_TEXT.name,
                    required = true,
                    isPrimary = true,
                    defaultValue = null,
                    position = 0,
                )
            )
            entryDao.insertActivity(
                ActivityEntity(entryId = tableId, kind = "TABLE_CREATED", occurredAt = now)
            )
        }
        return tableId
    }

    override suspend fun updateTable(id: Long, input: TableInput) {
        validateTableInput(input)?.let { throw IllegalArgumentException(it) }
        val name = input.name.trim()
        val now = clock()
        database.withTransaction {
            val table = tableDao.findTable(id) ?: throw IllegalArgumentException("La tabla ya no existe.")
            tableDao.updateTable(
                table.copy(
                    name = name,
                    description = input.description.trim(),
                    icon = input.icon.name,
                    color = input.color.lowercase(Locale.ROOT),
                    modifiedAt = now,
                )
            )
            entryDao.updateTableSummary(id, name, now)
        }
    }

    override suspend fun duplicateTable(id: Long): Long {
        val now = clock()
        var newId = 0L
        database.withTransaction {
            val source = tableDao.findTable(id) ?: throw IllegalArgumentException("La tabla ya no existe.")
            val columns = tableDao.findColumns(id)
            val options = tableDao.findOptions(id).groupBy { it.columnId }
            val rows = tableDao.findRows(id)
            val cells = tableDao.findCellsForTable(id)
            val name = "${source.name} (copia)".take(80)
            newId = entryDao.insert(EntryEntity(title = name, type = "TABLE", modifiedAt = now))
            tableDao.insertTable(
                source.copy(
                    id = newId,
                    name = name,
                    createdAt = now,
                    modifiedAt = now,
                )
            )
            val columnMap = mutableMapOf<Long, Long>()
            val optionMap = mutableMapOf<Long, Long>()
            columns.forEach { column ->
                val newColumnId = tableDao.insertColumn(
                    column.copy(id = 0, tableId = newId)
                )
                columnMap[column.id] = newColumnId
                options[column.id].orEmpty().forEach { option ->
                    val newOptionId = tableDao.insertOption(option.copy(id = 0, columnId = newColumnId))
                    optionMap[option.id] = newOptionId
                }
            }
            val columnTypes = columns.associate { column ->
                column.id to runCatching { ColumnType.valueOf(column.type) }.getOrNull()
            }
            val rowMap = mutableMapOf<Long, Long>()
            rows.forEach { row ->
                val newRowId = tableDao.insertRow(
                    row.copy(
                        id = 0,
                        tableId = newId,
                        createdAt = now,
                        modifiedAt = now,
                    )
                )
                rowMap[row.id] = newRowId
            }
            val copiedCells = cells.mapNotNull { cell ->
                val newRowId = rowMap[cell.rowId] ?: return@mapNotNull null
                val newColumnId = columnMap[cell.columnId] ?: return@mapNotNull null
                val value = if (columnTypes[cell.columnId]?.usesOptions == true) {
                    cell.value?.split(',')?.joinToString(",") { raw ->
                        val optionId = raw.trim().toLongOrNull()
                        optionId?.let { optionMap[it]?.toString() } ?: raw
                    }
                } else {
                    cell.value
                }
                cell.copy(
                    rowId = newRowId,
                    columnId = newColumnId,
                    value = value,
                    updatedAt = now,
                )
            }
            if (copiedCells.isNotEmpty()) tableDao.insertCells(copiedCells)
            entryDao.insertActivity(
                ActivityEntity(entryId = newId, kind = "TABLE_CREATED", occurredAt = now)
            )
        }
        return newId
    }

    override suspend fun deleteTable(id: Long) {
        database.withTransaction {
            val table = tableDao.findTable(id) ?: return@withTransaction
            entryDao.deleteEntry(table.id)
        }
    }

    override suspend fun addColumn(tableId: Long, input: ColumnInput): Long {
        validateColumnInput(input)?.let { throw IllegalArgumentException(it) }
        val now = clock()
        var columnId = 0L
        database.withTransaction {
            val table = tableDao.findTable(tableId) ?: throw IllegalArgumentException("La tabla ya no existe.")
            val options = emptyList<ColumnOption>()
            if (input.type.usesOptions && !input.defaultValue.isNullOrBlank()) {
                throw IllegalArgumentException("Añade opciones después de crear la columna para usar un valor predeterminado.")
            }
            val defaultValue = input.defaultValue?.takeIf { it.isNotBlank() }?.let { raw ->
                normalizeCellValue(input.type, raw, options)
            }
            columnId = tableDao.insertColumn(
                CustomColumnEntity(
                    tableId = tableId,
                    name = input.name.trim(),
                    type = input.type.name,
                    required = input.required,
                    isPrimary = false,
                    defaultValue = defaultValue,
                    position = tableDao.maxColumnPosition(tableId) + 1,
                )
            )
            if (defaultValue != null) {
                val rows = tableDao.findRows(tableId)
                if (rows.isNotEmpty()) {
                    tableDao.insertCells(
                        rows.map { row ->
                            CustomCellEntity(
                                rowId = row.id,
                                columnId = columnId,
                                value = defaultValue,
                                updatedAt = now,
                            )
                        }
                    )
                }
            }
            touch(table.id, now)
        }
        return columnId
    }

    override suspend fun updateColumn(columnId: Long, input: ColumnInput) {
        validateColumnInput(input)?.let { throw IllegalArgumentException(it) }
        val now = clock()
        database.withTransaction {
            val column = tableDao.findColumn(columnId)
                ?: throw IllegalArgumentException("La columna ya no existe.")
            val oldType = column.type.toColumnType()
            val newType = input.type
            val oldOptions = tableDao.findOptionsForColumn(columnId)
            val targetOptions = if (newType.usesOptions) oldOptions else emptyList()
            val oldDomainOptions = oldOptions.map(ColumnOptionEntity::toDomain)
            val newDomainOptions = targetOptions.map(ColumnOptionEntity::toDomain)
            val cells = tableDao.findCellsForColumn(columnId)
            val converted = if (oldType == newType) {
                cells
            } else {
                cells.map { cell ->
                    val convertedValue = convertValue(
                        oldType = oldType,
                        newType = newType,
                        raw = cell.value,
                        oldOptions = oldDomainOptions,
                        newOptions = newDomainOptions,
                    )
                    cell.copy(value = convertedValue, updatedAt = now)
                }
            }
            val convertedDefault = when {
                input.defaultValue.isNullOrBlank() -> null
                oldType == newType -> normalizeCellValue(newType, input.defaultValue, newDomainOptions, input.required)
                else -> convertValue(
                    oldType = oldType,
                    newType = newType,
                    raw = column.defaultValue,
                    oldOptions = oldDomainOptions,
                    newOptions = newDomainOptions,
                )
            }
            if (!newType.usesOptions && oldType.usesOptions) tableDao.deleteOptions(columnId)
            converted.forEach { cell ->
                if (cell.value.isNullOrBlank()) {
                    tableDao.deleteCell(cell.rowId, cell.columnId)
                } else {
                    tableDao.insertCell(cell)
                }
            }
            tableDao.updateColumn(
                column.copy(
                    name = input.name.trim(),
                    type = newType.name,
                    required = input.required,
                    defaultValue = convertedDefault,
                )
            )
            touch(column.tableId, now)
            refreshTitles(column.tableId, now)
        }
    }

    override suspend fun changeColumnType(columnId: Long, type: ColumnType) {
        val column = tableDao.findColumn(columnId)
            ?: throw IllegalArgumentException("La columna ya no existe.")
        updateColumn(
            columnId,
            ColumnInput(
                name = column.name,
                type = type,
                required = column.required,
                defaultValue = column.defaultValue,
            ),
        )
    }

    override suspend fun moveColumn(columnId: Long, offset: Int) {
        if (offset == 0) return
        val now = clock()
        database.withTransaction {
            val column = tableDao.findColumn(columnId)
                ?: throw IllegalArgumentException("La columna ya no existe.")
            val columns = tableDao.findColumns(column.tableId).toMutableList()
            val from = columns.indexOfFirst { it.id == columnId }
            val to = from + offset
            if (from < 0 || to !in columns.indices) return@withTransaction
            val moved = columns.removeAt(from)
            columns.add(to, moved)
            columns.forEachIndexed { index, item ->
                if (item.position != index) tableDao.updateColumnPosition(item.id, index)
            }
            touch(column.tableId, now)
        }
    }

    override suspend fun deleteColumn(columnId: Long) {
        val now = clock()
        database.withTransaction {
            val column = tableDao.findColumn(columnId)
                ?: throw IllegalArgumentException("La columna ya no existe.")
            if (tableDao.countColumns(column.tableId) <= 1) {
                throw IllegalArgumentException("Una tabla debe conservar al menos una columna principal.")
            }
            tableDao.deleteColumn(columnId)
            if (column.isPrimary) {
                tableDao.findColumns(column.tableId).firstOrNull()?.let {
                    tableDao.updateColumnPrimary(it.id, true)
                }
            }
            val columns = tableDao.findColumns(column.tableId)
            columns.forEachIndexed { index, item ->
                if (item.position != index) tableDao.updateColumnPosition(item.id, index)
            }
            touch(column.tableId, now)
            refreshTitles(column.tableId, now)
        }
    }

    override suspend fun addOption(columnId: Long, input: OptionInput): Long {
        validateOptionInput(input)?.let { throw IllegalArgumentException(it) }
        val name = input.name.trim()
        val now = clock()
        var optionId = 0L
        database.withTransaction {
            val column = tableDao.findColumn(columnId)
                ?: throw IllegalArgumentException("La columna ya no existe.")
            if (!column.type.toColumnType().usesOptions) {
                throw IllegalArgumentException("Esta columna no admite opciones.")
            }
            val existing = tableDao.findOptionsForColumn(columnId)
            if (existing.any { it.name.equals(name, ignoreCase = true) }) {
                throw IllegalArgumentException("Ya existe una opción con ese nombre.")
            }
            optionId = tableDao.insertOption(
                ColumnOptionEntity(
                    columnId = columnId,
                    name = name,
                    color = input.color.lowercase(Locale.ROOT),
                    position = (existing.maxOfOrNull { it.position } ?: -1) + 1,
                )
            )
            touch(column.tableId, now)
        }
        return optionId
    }

    override suspend fun updateOption(optionId: Long, input: OptionInput) {
        validateOptionInput(input)?.let { throw IllegalArgumentException(it) }
        val name = input.name.trim()
        val now = clock()
        database.withTransaction {
            val option = tableDao.findOption(optionId)
                ?: throw IllegalArgumentException("La opción ya no existe.")
            val column = tableDao.findColumn(option.columnId)
                ?: throw IllegalArgumentException("La columna ya no existe.")
            val duplicate = tableDao.findOptionsForColumn(option.columnId)
                .any { it.id != optionId && it.name.equals(name, ignoreCase = true) }
            if (duplicate) throw IllegalArgumentException("Ya existe una opción con ese nombre.")
            tableDao.updateOption(
                option.copy(
                    name = name,
                    color = input.color.lowercase(Locale.ROOT),
                )
            )
            touch(column.tableId, now)
            if (column.isPrimary) refreshTitles(column.tableId, now)
        }
    }

    override suspend fun deleteOption(optionId: Long) {
        val now = clock()
        database.withTransaction {
            val option = tableDao.findOption(optionId)
                ?: throw IllegalArgumentException("La opción ya no existe.")
            val column = tableDao.findColumn(option.columnId)
                ?: throw IllegalArgumentException("La columna ya no existe.")
            val type = column.type.toColumnType()
            tableDao.findCellsForColumn(column.id).forEach { cell ->
                val value = cell.value ?: return@forEach
                val next = when (type) {
                    ColumnType.SELECT, ColumnType.STATUS ->
                        if (value == option.id.toString()) null else value
                    ColumnType.MULTISELECT -> value.split(',')
                        .filter { it.isNotBlank() && it != option.id.toString() }
                        .joinToString(",")
                        .ifBlank { null }
                    else -> value
                }
                if (next.isNullOrBlank()) {
                    tableDao.deleteCell(cell.rowId, cell.columnId)
                } else if (next != value) {
                    tableDao.insertCell(cell.copy(value = next, updatedAt = now))
                }
            }
            tableDao.deleteOption(optionId)
            touch(column.tableId, now)
            if (column.isPrimary) refreshTitles(column.tableId, now)
        }
    }

    override suspend fun createRow(tableId: Long): Long {
        val now = clock()
        var rowId = 0L
        database.withTransaction {
            val table = tableDao.findTable(tableId) ?: throw IllegalArgumentException("La tabla ya no existe.")
            val columns = tableDao.findColumns(tableId)
            rowId = tableDao.insertRow(
                CustomRowEntity(
                    tableId = tableId,
                    title = "Sin título",
                    createdAt = now,
                    modifiedAt = now,
                )
            )
            val defaults = columns.mapNotNull { column ->
                column.defaultValue?.takeIf { it.isNotBlank() }?.let { value ->
                    CustomCellEntity(
                        rowId = rowId,
                        columnId = column.id,
                        value = value,
                        updatedAt = now,
                    )
                }
            }
            if (defaults.isNotEmpty()) tableDao.insertCells(defaults)
            refreshRowTitle(rowId, tableId, now)
            touch(table.id, now)
        }
        return rowId
    }

    override suspend fun duplicateRow(rowId: Long): Long {
        val now = clock()
        var newRowId = 0L
        database.withTransaction {
            val source = tableDao.findRow(rowId) ?: throw IllegalArgumentException("El registro ya no existe.")
            val columns = tableDao.findColumns(source.tableId)
            val primary = primaryColumn(columns.map { it.toDomain() })
            val cells = tableDao.findCells(rowId)
            newRowId = tableDao.insertRow(
                CustomRowEntity(
                    tableId = source.tableId,
                    title = "${source.title} (copia)".take(120),
                    createdAt = now,
                    modifiedAt = now,
                )
            )
            val textualPrimary = primary?.takeIf { isTextual(it.type) }
            val primaryOptions = textualPrimary?.let {
                tableDao.findOptionsForColumn(it.id).map(ColumnOptionEntity::toDomain)
            }.orEmpty()
            val copied = cells.map { cell ->
                val value = if (textualPrimary != null && cell.columnId == textualPrimary.id) {
                    val base = cell.value?.takeIf { it.isNotBlank() } ?: "Copia"
                    normalizeCellValue(
                        textualPrimary.type,
                        "$base (copia)".take(500),
                        primaryOptions,
                        required = false,
                    )
                } else {
                    cell.value
                }
                cell.copy(rowId = newRowId, value = value, updatedAt = now)
            }.toMutableList()
            if (textualPrimary != null && copied.none { it.columnId == textualPrimary.id }) {
                copied.add(
                    CustomCellEntity(
                        rowId = newRowId,
                        columnId = textualPrimary.id,
                        value = normalizeCellValue(textualPrimary.type, "Copia", primaryOptions),
                        updatedAt = now,
                    )
                )
            }
            if (copied.isNotEmpty()) tableDao.insertCells(copied)
            if (textualPrimary != null) refreshRowTitle(newRowId, source.tableId, now)
            touch(source.tableId, now)
        }
        return newRowId
    }

    override suspend fun deleteRow(rowId: Long) {
        val now = clock()
        database.withTransaction {
            val row = tableDao.findRow(rowId) ?: return@withTransaction
            tableDao.deleteRow(rowId)
            touch(row.tableId, now)
        }
    }

    override suspend fun updateCell(rowId: Long, columnId: Long, value: String?) {
        val now = clock()
        database.withTransaction {
            val row = tableDao.findRow(rowId) ?: throw IllegalArgumentException("El registro ya no existe.")
            val column = tableDao.findColumn(columnId)
                ?: throw IllegalArgumentException("La columna ya no existe.")
            if (column.tableId != row.tableId) throw IllegalArgumentException("La columna no pertenece a esta tabla.")
            val type = column.type.toColumnType()
            val options = tableDao.findOptionsForColumn(columnId).map(ColumnOptionEntity::toDomain)
            val normalized = normalizeCellValue(type, value, options, column.required)
            if (normalized.isNullOrBlank()) {
                tableDao.deleteCell(rowId, columnId)
            } else {
                tableDao.insertCell(
                    CustomCellEntity(
                        rowId = rowId,
                        columnId = columnId,
                        value = normalized,
                        updatedAt = now,
                    )
                )
            }
            if (column.isPrimary) refreshRowTitle(rowId, row.tableId, now)
            else tableDao.updateRowTitle(row.id, row.title, now)
            touch(row.tableId, now)
        }
    }

    override suspend fun loadPage(
        tableId: Long,
        query: TableQuery,
        limit: Int,
        offset: Int,
    ): TablePage {
        val columns = tableDao.findColumns(tableId)
        val byId = columns.associateBy { it.id }
        val safeLimit = limit.coerceIn(1, 200)
        val safeOffset = offset.coerceAtLeast(0)
        val args = mutableListOf<Any?>()
        val where = StringBuilder("r.tableId = ?")
        args.add(tableId)

        val search = query.search.trim()
        if (search.isNotEmpty()) {
            val pattern = likePattern(search)
            where.append(
                " AND (r.title LIKE ? ESCAPE '\\' OR EXISTS (" +
                    "SELECT 1 FROM custom_cells sc WHERE sc.rowId = r.id " +
                    "AND COALESCE(sc.value, '') LIKE ? ESCAPE '\\') OR EXISTS (" +
                    "SELECT 1 FROM custom_cells oc JOIN column_options o ON o.columnId = oc.columnId " +
                    "WHERE oc.rowId = r.id AND (" +
                    "oc.value = CAST(o.id AS TEXT) OR " +
                    "(',' || COALESCE(oc.value, '') || ',') LIKE ('%,' || o.id || ',%')) " +
                    "AND o.name LIKE ? ESCAPE '\\'))"
            )
            args.add(pattern)
            args.add(pattern)
            args.add(pattern)
        }

        query.filters.forEach { filter ->
            val column = byId[filter.columnId] ?: return@forEach
            val expression = "(SELECT value FROM custom_cells fc WHERE fc.rowId = r.id AND fc.columnId = ?)"
            val type = column.type.toColumnType()
            when (filter.operator) {
                TableFilterOperator.CONTAINS -> {
                    val raw = filter.value.trim()
                    if (raw.isNotEmpty()) {
                        if (type == ColumnType.MULTISELECT) {
                            where.append(" AND (',' || COALESCE($expression, '') || ',') LIKE ? ESCAPE '\\'")
                            args.add(column.id)
                            args.add("%${escapeLike(raw)}%")
                        } else {
                            where.append(" AND COALESCE($expression, '') LIKE ? ESCAPE '\\'")
                            args.add(column.id)
                            args.add(likePattern(raw))
                        }
                    }
                }
                TableFilterOperator.EQUALS -> {
                    where.append(" AND $expression = ?")
                    args.add(column.id)
                    args.add(filter.value)
                }
                TableFilterOperator.NOT_EMPTY -> {
                    where.append(" AND TRIM(COALESCE($expression, '')) != ''")
                    args.add(column.id)
                }
                TableFilterOperator.GREATER -> {
                    where.append(" AND CAST(COALESCE($expression, '') AS REAL) > CAST(? AS REAL)")
                    args.add(column.id)
                    args.add(filter.value)
                }
                TableFilterOperator.LESS -> {
                    where.append(" AND CAST(COALESCE($expression, '') AS REAL) < CAST(? AS REAL)")
                    args.add(column.id)
                    args.add(filter.value)
                }
                TableFilterOperator.BEFORE -> {
                    where.append(" AND CAST(COALESCE($expression, '') AS INTEGER) < CAST(? AS INTEGER)")
                    args.add(column.id)
                    args.add(filter.value)
                }
                TableFilterOperator.AFTER -> {
                    where.append(" AND CAST(COALESCE($expression, '') AS INTEGER) > CAST(? AS INTEGER)")
                    args.add(column.id)
                    args.add(filter.value)
                }
                TableFilterOperator.ON -> {
                    val start = filter.value.toLongOrNull() ?: return@forEach
                    where.append(" AND CAST(COALESCE($expression, '') AS INTEGER) BETWEEN ? AND ?")
                    args.add(column.id)
                    args.add(start)
                    args.add(start + 86_399_999L)
                }
                TableFilterOperator.IS_TRUE -> {
                    where.append(" AND $expression = '1'")
                    args.add(column.id)
                }
                TableFilterOperator.IS_FALSE -> {
                    where.append(" AND COALESCE($expression, '0') != '1'")
                    args.add(column.id)
                }
            }
        }

        val order = StringBuilder()
        val sortColumn = query.sort?.columnId?.let(byId::get)
        if (sortColumn != null) {
            val base = "(SELECT NULLIF(value, '') FROM custom_cells sc WHERE sc.rowId = r.id AND sc.columnId = ?)"
            val type = sortColumn.type.toColumnType()
            val expression = if (type.isNumeric || type == ColumnType.DATE ||
                type == ColumnType.DATETIME || type == ColumnType.CHECKBOX
            ) {
                "CAST($base AS REAL)"
            } else {
                "$base COLLATE NOCASE"
            }
            order.append("($base IS NULL) ASC, $expression ")
            order.append(if (query.sort.ascending) "ASC" else "DESC")
            args.add(sortColumn.id)
            args.add(sortColumn.id)
            order.append(", r.id ASC")
        } else {
            order.append("r.modifiedAt DESC, r.id DESC")
        }

        val sql = buildString {
            append("SELECT r.id, r.title, r.createdAt, r.modifiedAt FROM custom_rows r WHERE ")
            append(where)
            append(" ORDER BY ")
            append(order)
            append(" LIMIT ? OFFSET ?")
        }
        args.add(safeLimit + 1)
        args.add(safeOffset)

        val pageRows = mutableListOf<PageRow>()
        var hasMore = false
        database.query(SimpleSQLiteQuery(sql, args.toTypedArray())).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val titleIndex = cursor.getColumnIndexOrThrow("title")
            val createdIndex = cursor.getColumnIndexOrThrow("createdAt")
            val modifiedIndex = cursor.getColumnIndexOrThrow("modifiedAt")
            while (cursor.moveToNext()) {
                if (pageRows.size >= safeLimit) {
                    hasMore = true
                    break
                }
                pageRows.add(
                    PageRow(
                        id = cursor.getLong(idIndex),
                        title = cursor.getString(titleIndex),
                        createdAt = if (cursor.isNull(createdIndex)) null else cursor.getLong(createdIndex),
                        modifiedAt = if (cursor.isNull(modifiedIndex)) null else cursor.getLong(modifiedIndex),
                    )
                )
            }
        }
        if (pageRows.isEmpty()) return TablePage(emptyList(), false)
        val cells = tableDao.findCellsForRows(pageRows.map { it.id }).groupBy { it.rowId }
        val rows = pageRows.map { row ->
            TableRow(
                id = row.id,
                tableId = tableId,
                title = row.title,
                cells = cells[row.id].orEmpty()
                    .mapNotNull { cell -> cell.value?.takeIf { it.isNotBlank() }?.let { cell.columnId to it } }
                    .toMap(),
                createdAt = row.createdAt,
                modifiedAt = row.modifiedAt,
            )
        }
        return TablePage(rows, hasMore)
    }

    private suspend fun touch(tableId: Long, now: Long) {
        tableDao.findTable(tableId)?.let { table ->
            tableDao.updateTableName(table.id, table.name, now)
        }
        entryDao.touchTable(tableId, now)
    }

    private suspend fun refreshTitles(tableId: Long, now: Long) {
        tableDao.findRows(tableId).forEach { row -> refreshRowTitle(row.id, tableId, now) }
    }

    private suspend fun refreshRowTitle(rowId: Long, tableId: Long, now: Long) {
        val columns = tableDao.findColumns(tableId)
        val primary = tableDao.findPrimaryColumn(tableId) ?: columns.firstOrNull() ?: return
        val optionEntities = tableDao.findOptionsForColumn(primary.id)
        val options = optionEntities.map(ColumnOptionEntity::toDomain)
        val cell = tableDao.findCell(rowId, primary.id)
        val title = tableRowTitle(primary.toDomain(optionEntities), cell?.value, options)
        tableDao.updateRowTitle(rowId, title, now)
    }

    private fun convertValue(
        oldType: ColumnType,
        newType: ColumnType,
        raw: String?,
        oldOptions: List<ColumnOption>,
        newOptions: List<ColumnOption>,
    ): String? {
        if (raw.isNullOrBlank()) return null
        if (!canConvert(oldType, newType)) {
            throw IllegalArgumentException(
                "No se puede convertir ${oldType.label.lowercase()} a ${newType.label.lowercase()} sin perder datos."
            )
        }
        if (oldType == ColumnType.DATETIME && newType == ColumnType.DATE) {
            val timestamp = raw.toLongOrNull()
            if (timestamp == null || startOfDay(timestamp) != timestamp) {
                throw IllegalArgumentException("La columna contiene horas que se perderían al cambiar a fecha.")
            }
        }
        val candidate = if (isTextual(newType) && !isTextual(oldType)) {
            displayCellValue(oldType, raw, oldOptions)
        } else {
            raw
        }
        return normalizeCellValue(newType, candidate, newOptions)
    }

    private fun canConvert(oldType: ColumnType, newType: ColumnType): Boolean = when {
        oldType == newType -> true
        isTextual(oldType) || isTextual(newType) -> true
        oldType.isNumeric && newType.isNumeric -> true
        oldType.isNumeric && newType == ColumnType.CHECKBOX -> true
        oldType == ColumnType.CHECKBOX && newType.isNumeric -> true
        oldType == ColumnType.DATE && newType == ColumnType.DATETIME -> true
        oldType == ColumnType.DATETIME && newType == ColumnType.DATE -> true
        oldType in optionTypes && newType in optionTypes -> true
        else -> false
    }

    private fun isTextual(type: ColumnType): Boolean = type in setOf(
        ColumnType.SHORT_TEXT,
        ColumnType.LONG_TEXT,
        ColumnType.URL,
        ColumnType.EMAIL,
        ColumnType.PHONE,
    )

    private fun likePattern(value: String): String = "%${escapeLike(value)}%"

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private companion object {
        val optionTypes = setOf(ColumnType.SELECT, ColumnType.MULTISELECT, ColumnType.STATUS)
    }
}

private fun TableSummaryEntity.toDomain(): CustomTable = CustomTable(
    id = id,
    name = name,
    description = description,
    icon = icon.toTableIcon(),
    color = color,
    columnCount = columnCount,
    rowCount = rowCount,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
)

private fun CustomTableEntity.toDomain(): CustomTable = CustomTable(
    id = id,
    name = name,
    description = description,
    icon = icon.toTableIcon(),
    color = color,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
)

private fun CustomColumnEntity.toDomain(options: List<ColumnOptionEntity> = emptyList()): CustomColumn =
    CustomColumn(
        id = id,
        tableId = tableId,
        name = name,
        type = type.toColumnType(),
        required = required,
        primary = isPrimary,
        defaultValue = defaultValue,
        position = position,
        options = options.map(ColumnOptionEntity::toDomain),
    )

private fun ColumnOptionEntity.toDomain(): ColumnOption = ColumnOption(
    id = id,
    columnId = columnId,
    name = name,
    color = color,
    position = position,
)

private fun CustomRowEntity.toDomain(cells: List<CustomCellEntity>): TableRow = TableRow(
    id = id,
    tableId = tableId,
    title = title,
    cells = cells
        .mapNotNull { cell -> cell.value?.takeIf { it.isNotBlank() }?.let { cell.columnId to it } }
        .toMap(),
    createdAt = createdAt,
    modifiedAt = modifiedAt,
)

private fun RecentTableRowEntity.toDomain(): RecentTableRow = RecentTableRow(
    rowId = rowId,
    tableId = tableId,
    tableName = tableName,
    title = title.ifBlank { "Registro sin título" },
    modifiedAt = modifiedAt,
)

private fun String.toColumnType(): ColumnType =
    ColumnType.entries.firstOrNull { it.name == this } ?: ColumnType.SHORT_TEXT

private fun String.toTableIcon(): TableIcon =
    TableIcon.entries.firstOrNull { it.name == this } ?: TableIcon.GRID

private data class PageRow(
    val id: Long,
    val title: String,
    val createdAt: Long?,
    val modifiedAt: Long?,
)
