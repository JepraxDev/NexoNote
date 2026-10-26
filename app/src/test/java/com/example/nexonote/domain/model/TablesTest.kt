package com.example.nexonote.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TablesTest {
    private val options = listOf(
        ColumnOption(id = 10, columnId = 5, name = "Pendiente", color = "#B3261E"),
        ColumnOption(id = 20, columnId = 5, name = "Hecho", color = "#4F6D3A"),
    )

    @Test fun integerAcceptsNegativeAndRejectsDecimals() {
        assertEquals("-42", normalizeCellValue(ColumnType.INTEGER, "-42", emptyList()))
        assertThrows(IllegalArgumentException::class.java) {
            normalizeCellValue(ColumnType.INTEGER, "1.5", emptyList())
        }
    }

    @Test fun currencyRoundsToTwoDecimals() {
        assertEquals("12.35", normalizeCellValue(ColumnType.CURRENCY, "12,345", emptyList()))
        assertEquals("10.00", normalizeCellValue(ColumnType.CURRENCY, "10", emptyList()))
    }

    @Test fun dateNormalizesToStartOfDay() {
        val timestamp = 1_700_000_000_000L
        val expected = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(expected.toString(), normalizeCellValue(ColumnType.DATE, timestamp.toString(), emptyList()))
    }

    @Test fun requiredFieldsRejectBlanksExceptCheckbox() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeCellValue(ColumnType.SHORT_TEXT, "  ", emptyList(), required = true)
        }
        assertNull(normalizeCellValue(ColumnType.CHECKBOX, "", emptyList(), required = true))
    }

    @Test fun selectAcceptsOptionIdOrNameAndRejectsUnknownValues() {
        assertEquals("20", normalizeCellValue(ColumnType.SELECT, "20", options))
        assertEquals("10", normalizeCellValue(ColumnType.STATUS, "Pendiente", options))
        assertThrows(IllegalArgumentException::class.java) {
            normalizeCellValue(ColumnType.SELECT, "99", options)
        }
    }

    @Test fun multiselectDeduplicatesAndKeepsValidIds() {
        assertEquals("20,10", normalizeCellValue(ColumnType.MULTISELECT, "20, 10, 20", options))
        assertNull(normalizeCellValue(ColumnType.MULTISELECT, "", options))
    }

    @Test fun urlEmailAndPhoneAreValidated() {
        assertEquals("https://example.com", normalizeCellValue(ColumnType.URL, "https://example.com", emptyList()))
        assertEquals("ana@example.com", normalizeCellValue(ColumnType.EMAIL, "ana@example.com", emptyList()))
        assertEquals("+34 600 123 456", normalizeCellValue(ColumnType.PHONE, "+34 600 123 456", emptyList()))
        assertThrows(IllegalArgumentException::class.java) {
            normalizeCellValue(ColumnType.URL, "example.com", emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeCellValue(ColumnType.EMAIL, "correo", emptyList())
        }
    }

    @Test fun filterOperatorsMatchColumnType() {
        assertEquals(
            listOf(TableFilterOperator.CONTAINS, TableFilterOperator.EQUALS, TableFilterOperator.NOT_EMPTY),
            filterOperatorsFor(ColumnType.SHORT_TEXT),
        )
        assertEquals(
            listOf(TableFilterOperator.IS_TRUE, TableFilterOperator.IS_FALSE),
            filterOperatorsFor(ColumnType.CHECKBOX),
        )
        assertTrue(TableFilterOperator.BEFORE in filterOperatorsFor(ColumnType.DATE))
    }

    @Test fun inputValidationReportsUsefulErrors() {
        assertEquals("El nombre de la tabla es obligatorio.", validateTableInput(TableInput(name = " ")))
        assertEquals(null, validateTableInput(TableInput(name = "Inventario")))
        assertEquals("El nombre de la columna es obligatorio.", validateColumnInput(ColumnInput("", ColumnType.SHORT_TEXT)))
        assertEquals("El nombre de la opción es obligatorio.", validateOptionInput(OptionInput("", "#112233")))
    }

    @Test fun displayValueFormatsCommonTypes() {
        assertEquals("Sí", displayCellValue(ColumnType.CHECKBOX, "1", emptyList()))
        assertEquals("Hecho", displayCellValue(ColumnType.SELECT, "20", options))
        assertEquals("Pendiente, Hecho", displayCellValue(ColumnType.MULTISELECT, "10,20", options))
    }
}
