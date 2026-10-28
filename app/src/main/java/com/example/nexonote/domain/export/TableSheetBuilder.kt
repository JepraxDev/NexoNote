package com.example.nexonote.domain.export

import com.example.nexonote.domain.model.ColumnType
import com.example.nexonote.domain.model.CustomColumn
import com.example.nexonote.domain.model.CustomTable
import com.example.nexonote.domain.model.TableRow
import java.math.BigDecimal

/** Converts a custom table into a spreadsheet sheet using the real column names and types. */
object TableSheetBuilder {
    fun sheet(
        table: CustomTable,
        columns: List<CustomColumn>,
        rows: List<TableRow>,
    ): SheetData {
        val ordered = columns.sortedWith(compareBy({ it.position }, { it.id }))
        val header = ordered.map { SheetCell.text(it.displayName) }
        val body = rows.map { row ->
            ordered.map { column -> cell(column, row.cells[column.id]) }
        }
        return SheetData(table.displayName, listOf(header) + body)
    }

    fun cell(column: CustomColumn, raw: String?): SheetCell? {
        if (raw.isNullOrBlank()) return null
        return when (column.type) {
            ColumnType.INTEGER -> SheetCell.number(raw.toLongOrNull()) ?: SheetCell.text(raw)
            ColumnType.DECIMAL, ColumnType.CURRENCY ->
                SheetCell.number(raw.toBigDecimalOrNull()) ?: SheetCell.text(raw)
            ColumnType.CHECKBOX -> SheetCell.boolean(raw == "1")
            ColumnType.DATE -> SheetCell.text(TransferFormats.formatDate(raw.toLongOrNull()))
            ColumnType.DATETIME -> SheetCell.text(TransferFormats.formatDateTime(raw.toLongOrNull()))
            ColumnType.SELECT, ColumnType.STATUS -> SheetCell.text(
                column.options.firstOrNull { it.id.toString() == raw }?.name ?: raw
            )
            ColumnType.MULTISELECT -> SheetCell.text(
                raw.split(',')
                    .mapNotNull { token -> column.options.firstOrNull { it.id.toString() == token.trim() }?.name }
                    .joinToString(", ")
                    .ifBlank { raw }
            )
            else -> SheetCell.text(raw)
        }
    }

    fun csvValue(cell: SheetCell?): String = when (cell?.kind) {
        SheetCellKind.BOOLEAN -> if (cell.value == "1") "Sí" else "No"
        SheetCellKind.NUMBER -> normaliseNumber(cell.value)
        else -> cell?.value.orEmpty()
    }

    private fun normaliseNumber(value: String): String {
        val decimal = value.toBigDecimalOrNull() ?: return value
        return if (decimal.scale() <= 0) decimal.toBigInteger().toString() else decimal.stripTrailingZeros().toPlainString()
    }

    fun numericOrNull(raw: String?): BigDecimal? = raw?.toBigDecimalOrNull()
}
