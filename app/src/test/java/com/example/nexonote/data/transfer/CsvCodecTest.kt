package com.example.nexonote.data.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvCodecTest {
    @Test fun roundTripsQuotesCommasNewlinesAndUnicode() {
        val rows = listOf(
            listOf("Título", "Descripción", "Notas"),
            listOf("Café ☕", "Dice \"hola\"", "Línea 1\nLínea 2"),
            listOf("", "emoji 🎉", "comma, inside"),
        )
        val encoded = CsvCodec.write(rows)
        assertEquals(rows, CsvCodec.parse(encoded))
    }

    @Test fun detectsSemicolonDelimiterAndBom() {
        val text = "\uFEFFNombre;Edad;Ciudad\r\nAna;34;Madrid\r\nLuis;7;Sevilla\r\n"
        val rows = CsvCodec.parse(text)
        assertEquals(listOf("Nombre", "Edad", "Ciudad"), rows.first())
        assertEquals(listOf("Luis", "7", "Sevilla"), rows.last())
    }

    @Test fun keepsEmptyTrailingValues() {
        val rows = CsvCodec.parse("a,b,c\r\n1,,3\r\n")
        assertEquals(listOf("1", "", "3"), rows[1])
    }

    @Test fun formulaGuardNeutralisesDangerousLeadingCharacters() {
        assertEquals("'=SUM(A1:A2)", FormulaGuard.escape("=SUM(A1:A2)"))
        assertEquals("'+1", FormulaGuard.escape("+1"))
        assertEquals("'-1", FormulaGuard.escape("-1"))
        assertEquals("'@user", FormulaGuard.escape("@user"))
        assertEquals("'  =cmd", FormulaGuard.escape("  =cmd"))
        assertEquals("safe", FormulaGuard.escape("safe"))
        assertEquals("", FormulaGuard.escape(""))
    }

    @Test fun formulaGuardRoundTripsThroughCsv() {
        val original = "=2+2"
        val escaped = FormulaGuard.escape(original)
        val csv = CsvCodec.write(listOf(listOf(escaped)))
        val parsed = CsvCodec.parse(csv).single().single()
        assertEquals(original, FormulaGuard.unescape(parsed))
        assertTrue(escaped.startsWith("'"))
        assertFalse(escaped == original)
    }

    @Test fun maxRowsStopsAtHeaderPlusRequestedRecords() {
        val rows = CsvCodec.parse("h\n1\n2\n3\n4\n", maxRows = 3)
        assertEquals(3, rows.size)
        assertEquals(listOf("h"), rows[0])
        assertEquals(listOf("2"), rows[2])
    }
}
