package com.example.nexonote.data.repository

import androidx.room.withTransaction
import com.example.nexonote.data.local.ActivityEntity
import com.example.nexonote.data.local.ColumnOptionEntity
import com.example.nexonote.data.local.CustomCellEntity
import com.example.nexonote.data.local.CustomColumnEntity
import com.example.nexonote.data.local.CustomRowEntity
import com.example.nexonote.data.local.CustomTableEntity
import com.example.nexonote.data.local.EntryEntity
import com.example.nexonote.data.local.NexoDatabase
import com.example.nexonote.domain.export.TransferFormats
import com.example.nexonote.domain.model.ColumnOption
import com.example.nexonote.domain.model.ColumnType
import com.example.nexonote.domain.model.CustomColumn
import com.example.nexonote.domain.model.DEFAULT_TABLE_COLOR
import com.example.nexonote.domain.model.DuplicateStrategy
import com.example.nexonote.domain.model.ImportColumnSpec
import com.example.nexonote.domain.model.ImportResult
import com.example.nexonote.domain.model.TableIcon
import com.example.nexonote.domain.model.tableRowTitle
import com.example.nexonote.domain.repository.ImportRowValues
import com.example.nexonote.domain.repository.TableImportRepository
import java.math.BigDecimal
import java.util.Locale

/**
 * Table import writer. Everything (table creation, row inserts and updates) runs inside one Room
 * transaction; a crash or validation failure leaves the database untouched.
 */
class LocalTableImportRepository(
    private val database: NexoDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : TableImportRepository {
    private val tableDao = database.tableDao()
    private val entryDao = database.entryDao()

    override suspend fun columns(tableId: Long): List<CustomColumn> {
        val columns = tableDao.findColumns(tableId)
        val options = tableDao.findOptions(tableId).groupBy { it.columnId }
        return columns.map { it.toDomain(options[it.id].orEmpty()) }
    }

    override suspend fun existingPrimaryKeys(tableId: Long, primary: CustomColumn): Map<String, Long> {
        val rows = tableDao.findRows(tableId)
        if (rows.isEmpty()) return emptyMap()
        val cells = rows.map { it.id }
            .chunked(SQLITE_CHUNK)
            .flatMap { tableDao.findCellsForRows(it) }
            .filter { it.columnId == primary.id }
        val result = LinkedHashMap<String, Long>()
        cells.forEach { cell ->
            val key = duplicateKey(primary.type, cell.value) ?: return@forEach
            result.putIfAbsent(key, cell.rowId)
        }
        return result
    }

    override suspend fun write(
        targetTableId: Long?,
        newTableName: String?,
        newColumns: List<ImportColumnSpec>,
        rows: List<ImportRowValues>,
        primaryIndex: Int,
        strategy: DuplicateStrategy,
    ): ImportResult {
        if (rows.isEmpty()) return ImportResult(createdTableId = targetTableId)
        val now = clock()
        var createdTableId: Long? = null
        var imported = 0
        var updated = 0
        var skipped = 0

        database.withTransaction {
            val tableId: Long
            val columnEntities: List<CustomColumnEntity>
            if (targetTableId == null) {
                val created = createTable(newTableName, newColumns, now)
                tableId = created.first
                columnEntities = created.second
                createdTableId = tableId
            } else {
                tableId = targetTableId
                columnEntities = tableDao.findColumns(tableId)
                require(columnEntities.isNotEmpty()) { "La tabla de destino no tiene columnas." }
            }
            val primary = columnEntities.getOrNull(primaryIndex)
                ?: throw IllegalArgumentException("La columna principal del destino no es válida.")
            val options = tableDao.findOptions(tableId).groupBy { it.columnId }
            val domainColumns = columnEntities.map { column ->
                column.toDomain(options[column.id].orEmpty())
            }
            val existing = if (strategy == DuplicateStrategy.ALLOW) {
                mutableMapOf<String, Long>()
            } else {
                LinkedHashMap(existingPrimaryKeys(tableId, domainColumns[primaryIndex]))
            }

            rows.forEach { row ->
                require(row.values.size == columnEntities.size) {
                    "La fila ${row.sourceRow} no coincide con las columnas de destino."
                }
                val key = duplicateKey(domainColumns[primaryIndex].type, row.values[primaryIndex])
                val match = key?.let(existing::get)
                if (match != null && strategy == DuplicateStrategy.SKIP) {
                    skipped++
                    return@forEach
                }
                if (match != null && strategy == DuplicateStrategy.UPDATE) {
                    updateRow(match, tableId, PrimaryTitle(domainColumns[primaryIndex], row.values[primaryIndex]), columnEntities, row, now, options)
                    updated++
                    key?.let { existing[it] = match }
                    return@forEach
                }
                val insertedId = insertRow(tableId, domainColumns[primaryIndex], row.values[primaryIndex], columnEntities, row, now, options)
                // Keep the map current so repeated primary keys in the same file honour SKIP/UPDATE.
                if (strategy != DuplicateStrategy.ALLOW) key?.let { existing[it] = insertedId }
                imported++
            }

            tableDao.findTable(tableId)?.let { table ->
                tableDao.updateTableName(tableId, table.name, now)
            }
            entryDao.touchTable(tableId, now)
        }

        return ImportResult(
            imported = imported,
            updated = updated,
            skipped = skipped,
            createdTableId = createdTableId,
        )
    }

    override fun duplicateKey(type: ColumnType, value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return when {
            type in NUMERIC_TYPES -> runCatching {
                BigDecimal(trimmed.replace(" ", "").replace(',', '.')).stripTrailingZeros().toPlainString()
            }.getOrDefault(trimmed)
            type in TEXTUAL_TYPES || type == ColumnType.EMAIL || type == ColumnType.URL ->
                trimmed.lowercase(Locale.ROOT)
            else -> trimmed
        }
    }

    private suspend fun createTable(
        name: String?,
        specs: List<ImportColumnSpec>,
        now: Long,
    ): Pair<Long, List<CustomColumnEntity>> {
        require(specs.isNotEmpty()) { "La tabla nueva necesita al menos una columna." }
        val tableName = (name?.trim()?.takeIf { it.isNotEmpty() } ?: "Importación").take(80)
        val used = mutableSetOf<String>()
        val columns = specs.mapIndexed { index, spec ->
            var base = spec.name.trim().ifBlank { "Columna ${index + 1}" }.take(80)
            if (!used.add(base.lowercase(Locale.ROOT))) {
                var suffix = 2
                while (!used.add("$base ($suffix)".lowercase(Locale.ROOT))) suffix++
                base = "$base ($suffix)"
            }
            require(spec.type !in OPTION_TYPES) {
                "Las columnas de selección necesitan opciones existentes; importa en una tabla ya creada."
            }
            CustomColumnEntity(
                tableId = 0,
                name = base,
                type = spec.type.name,
                required = spec.required,
                isPrimary = index == 0,
                defaultValue = null,
                position = index,
            )
        }
        val tableId = entryDao.insert(
            EntryEntity(title = tableName, type = "TABLE", modifiedAt = now)
        )
        tableDao.insertTable(
            CustomTableEntity(
                id = tableId,
                name = tableName,
                description = "Importada el ${TransferFormats.formatDateTime(now)}",
                icon = TableIcon.GRID.name,
                color = DEFAULT_TABLE_COLOR,
                createdAt = now,
                modifiedAt = now,
            )
        )
        val inserted = columns.map { column ->
            val id = tableDao.insertColumn(column.copy(tableId = tableId))
            column.copy(id = id, tableId = tableId)
        }
        entryDao.insertActivity(ActivityEntity(entryId = tableId, kind = "TABLE_CREATED", occurredAt = now))
        return tableId to inserted
    }

    private suspend fun insertRow(
        tableId: Long,
        primary: CustomColumn,
        primaryValue: String?,
        columns: List<CustomColumnEntity>,
        row: ImportRowValues,
        now: Long,
        options: Map<Long, List<ColumnOptionEntity>>,
    ): Long {
        val primaryOptions = options[primary.id].orEmpty().map(ColumnOptionEntity::toDomain)
        val rowId = tableDao.insertRow(
            CustomRowEntity(
                tableId = tableId,
                title = tableRowTitle(primary, primaryValue, primaryOptions),
                createdAt = now,
                modifiedAt = now,
            )
        )
        val cells = row.values.mapIndexedNotNull { index, value ->
            value?.takeIf { it.isNotBlank() }?.let { normalized ->
                CustomCellEntity(
                    rowId = rowId,
                    columnId = columns[index].id,
                    value = normalized,
                    updatedAt = now,
                )
            }
        }
        if (cells.isNotEmpty()) tableDao.insertCells(cells)
        return rowId
    }

    private suspend fun updateRow(
        rowId: Long,
        tableId: Long,
        primaryTitle: PrimaryTitle,
        columns: List<CustomColumnEntity>,
        row: ImportRowValues,
        now: Long,
        options: Map<Long, List<ColumnOptionEntity>>,
    ) {
        // Only touch columns that are bound to a source column. Unmapped columns keep their
        // current value instead of being erased by the import.
        val mapped = if (row.mapped.size == columns.size) row.mapped else columns.indices.map { true }
        columns.forEachIndexed { index, column ->
            if (mapped[index]) tableDao.deleteCell(rowId, column.id)
        }
        val cells = row.values.mapIndexedNotNull { index, value ->
            if (!mapped[index]) return@mapIndexedNotNull null
            value?.takeIf { it.isNotBlank() }?.let { normalized ->
                CustomCellEntity(
                    rowId = rowId,
                    columnId = columns[index].id,
                    value = normalized,
                    updatedAt = now,
                )
            }
        }
        if (cells.isNotEmpty()) tableDao.insertCells(cells)
        val primaryOptions = options[primaryTitle.column.id].orEmpty().map(ColumnOptionEntity::toDomain)
        val title = tableRowTitle(primaryTitle.column, primaryTitle.value, primaryOptions)
        tableDao.updateRowTitle(rowId, title, now)
    }

    private data class PrimaryTitle(val column: CustomColumn, val value: String?)

    private companion object {
        const val SQLITE_CHUNK = 500
        val NUMERIC_TYPES = setOf(ColumnType.INTEGER, ColumnType.DECIMAL, ColumnType.CURRENCY)
        val TEXTUAL_TYPES = setOf(ColumnType.SHORT_TEXT, ColumnType.LONG_TEXT, ColumnType.PHONE)
        val OPTION_TYPES = setOf(ColumnType.SELECT, ColumnType.MULTISELECT, ColumnType.STATUS)
    }
}

private fun CustomColumnEntity.toDomain(options: List<ColumnOptionEntity>): CustomColumn = CustomColumn(
    id = id,
    tableId = tableId,
    name = name,
    type = ColumnType.entries.firstOrNull { it.name == type } ?: ColumnType.SHORT_TEXT,
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
