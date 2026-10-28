package com.example.nexonote.domain.export

import java.math.BigDecimal

enum class SheetCellKind { TEXT, NUMBER, BOOLEAN }

data class SheetCell(val value: String, val kind: SheetCellKind = SheetCellKind.TEXT) {
    companion object {
        fun text(value: String?): SheetCell? = value?.let { SheetCell(it) }
        fun number(value: BigDecimal?): SheetCell? = value?.let { SheetCell(it.toPlainString(), SheetCellKind.NUMBER) }
        fun number(value: Long?): SheetCell? = value?.let { SheetCell(it.toString(), SheetCellKind.NUMBER) }
        fun boolean(value: Boolean): SheetCell = SheetCell(if (value) "1" else "0", SheetCellKind.BOOLEAN)
    }
}

data class SheetData(val name: String, val rows: List<List<SheetCell?>>) {
    fun textRows(): List<List<String>> = rows.map { row -> row.map { it?.value.orEmpty() } }
}
