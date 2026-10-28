package com.example.nexonote.domain.export

import com.example.nexonote.domain.model.ColumnOption
import com.example.nexonote.domain.model.ColumnType
import com.example.nexonote.domain.model.CustomColumn
import com.example.nexonote.domain.model.CustomTable
import com.example.nexonote.domain.model.Note
import com.example.nexonote.domain.model.NoteType
import com.example.nexonote.domain.model.TableRow
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferFormatsTest {
    private fun millis(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long {
        val calendar = Calendar.getInstance()
        calendar.clear()
        calendar.set(year, month - 1, day, hour, minute)
        return calendar.timeInMillis
    }

    @Test fun formatsDatesIndependentlyOfLocale() {
        val value = millis(2026, 10, 28, 9, 5)
        assertEquals("2026-10-28", TransferFormats.formatDate(value))
        assertEquals("2026-10-28 09:05", TransferFormats.formatDateTime(value))
        assertEquals("", TransferFormats.formatDate(null))
    }

    @Test fun parsesIsoAndSpanishDates() {
        assertEquals(millis(2026, 10, 28), TransferFormats.parseDate("2026-10-28"))
        assertEquals(millis(2026, 10, 28), TransferFormats.parseDate("28/10/2026"))
        assertEquals(millis(2026, 10, 28, 14, 30), TransferFormats.parseDate("2026-10-28 14:30"))
        assertNull(TransferFormats.parseDate("no es fecha"))
        assertNull(TransferFormats.parseDate(""))
    }

    @Test fun detectsNumericDateCheckboxAndEmailColumns() {
        assertEquals(ColumnType.INTEGER, TransferFormats.detectType(listOf("1", "-2", "300")))
        assertEquals(ColumnType.DECIMAL, TransferFormats.detectType(listOf("1,5", "2.25")))
        assertEquals(ColumnType.DATE, TransferFormats.detectType(listOf("2026-01-01", "2026-12-31")))
        assertEquals(ColumnType.DATETIME, TransferFormats.detectType(listOf("2026-01-01 10:00")))
        assertEquals(ColumnType.CHECKBOX, TransferFormats.detectType(listOf("sí", "no", "sí")))
        assertEquals(ColumnType.EMAIL, TransferFormats.detectType(listOf("a@b.com", "c@d.es")))
        assertEquals(ColumnType.URL, TransferFormats.detectType(listOf("https://nexonote.app")))
    }

    @Test fun emptyValuesAreTextAndCheckboxParsingIsLenient() {
        assertEquals(ColumnType.SHORT_TEXT, TransferFormats.detectType(listOf("", "  ")))
        assertEquals(true, TransferFormats.parseCheckbox("Sí"))
        assertEquals(true, TransferFormats.parseCheckbox("x"))
        assertEquals(false, TransferFormats.parseCheckbox("0"))
        assertNull(TransferFormats.parseCheckbox("quizá"))
    }

    @Test fun sizeFormattingUsesReadableUnits() {
        assertEquals("0 B", TransferFormats.formatSize(0))
        assertEquals("512 B", TransferFormats.formatSize(512))
        assertEquals("1.5 KB", TransferFormats.formatSize(1536))
        assertTrue(TransferFormats.formatSize(5L * 1024 * 1024).endsWith("MB"))
    }

    @Test fun fileNamesAreSanitizedAndUnique() {
        assertEquals("Mi_nota", TransferFormats.sanitizeFileName("Mi/nota"))
        assertEquals("NexoNote", TransferFormats.sanitizeFileName("..."))
        assertEquals(
            "Nota (2).md",
            TransferFormats.uniqueFileName("Nota", "md", setOf("nota.md")),
        )
    }
}

class NoteTextFormatterTest {
    private val note = Note(
        id = 7,
        title = "Café ☕",
        content = "Línea uno\nLínea dos",
        type = NoteType.CHECKLIST,
        createdAt = 1_700_000_000_000,
        modifiedAt = 1_700_100_000_000,
        labels = listOf("trabajo", "ideas"),
        checklist = listOf(
            com.example.nexonote.domain.model.ChecklistItem(text = "Comprar leche", checked = true),
            com.example.nexonote.domain.model.ChecklistItem(text = "Llamar a Ana", checked = false),
        ),
    )

    @Test fun plainTextKeepsUnicodeChecklistAndMetadata() {
        val text = NoteTextFormatter.plainText(note, folderName = "Personal", categoryName = "Casa")
        assertTrue(text.contains("Café ☕"))
        assertTrue(text.contains("Línea uno"))
        assertTrue(text.contains("[x] Comprar leche"))
        assertTrue(text.contains("[ ] Llamar a Ana"))
        assertTrue(text.contains("Carpeta: Personal"))
        assertTrue(text.contains("Categoría: Casa"))
        assertTrue(text.contains("trabajo"))
    }

    @Test fun markdownUsesHeadingAndTaskSyntax() {
        val markdown = NoteTextFormatter.markdown(note)
        assertTrue(markdown.startsWith("# Café ☕"))
        assertTrue(markdown.contains("- [x] Comprar leche"))
        assertTrue(markdown.contains("- [ ] Llamar a Ana"))
        assertTrue(markdown.contains("#trabajo"))
        assertFalse(markdown.contains("[x] Comprar leche\n[x]"))
    }
}

class TableSheetBuilderTest {
    private val email = CustomColumn(id = 1, tableId = 1, name = "Correo", type = ColumnType.EMAIL, position = 0)
    private val start = CustomColumn(id = 2, tableId = 1, name = "Fecha", type = ColumnType.DATE, position = 1)
    private val done = CustomColumn(id = 3, tableId = 1, name = "Hecha", type = ColumnType.CHECKBOX, position = 2)
    private val tags = CustomColumn(
        id = 4,
        tableId = 1,
        name = "Etiquetas",
        type = ColumnType.MULTISELECT,
        position = 3,
        options = listOf(
            ColumnOption(id = 10, columnId = 4, name = "Urgente", color = "#f00"),
            ColumnOption(id = 11, columnId = 4, name = "Casa", color = "#0f0"),
        ),
    )
    private val table = CustomTable(id = 1, name = "Contactos")

    @Test fun exportsRealColumnNamesAndTypedValues() {
        val row = TableRow(
            id = 100,
            tableId = 1,
            title = "Ana",
            cells = mapOf(
                1L to "ana@example.com",
                2L to "1700000000000",
                3L to "1",
                4L to "10,11",
            ),
        )
        val sheet = TableSheetBuilder.sheet(table, listOf(tags, done, start, email), listOf(row))
        assertEquals(listOf("Correo", "Fecha", "Hecha", "Etiquetas"), sheet.textRows().first())
        val values = sheet.textRows()[1]
        assertEquals("ana@example.com", values[0])
        assertTrue(values[1].matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        assertEquals("1", values[2])
        assertEquals("Urgente, Casa", values[3])
    }

    @Test fun emptyCellsStayEmptyAndNumbersAreNormalisedForCsv() {
        val row = TableRow(id = 100, tableId = 1, title = "Sin datos", cells = emptyMap())
        val sheet = TableSheetBuilder.sheet(table, listOf(email, done), listOf(row))
        assertNull(sheet.rows[1][0])
        assertNull(sheet.rows[1][1])
        assertEquals(
            "3.5",
            TableSheetBuilder.csvValue(SheetCell.number(java.math.BigDecimal("3.50"))),
        )
        assertEquals("Sí", TableSheetBuilder.csvValue(SheetCell.boolean(true)))
        assertEquals("No", TableSheetBuilder.csvValue(SheetCell.boolean(false)))
    }
}
