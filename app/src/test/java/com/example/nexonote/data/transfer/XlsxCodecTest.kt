package com.example.nexonote.data.transfer

import com.example.nexonote.domain.export.SheetCell
import com.example.nexonote.domain.export.SheetData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XlsxCodecTest {
    @Test fun roundTripsSheetsWithUnicodeNewlinesAndNumbers() {
        val sheets = listOf(
            SheetData(
                name = "Tareas",
                rows = listOf(
                    listOf(
                        SheetCell.text("Título"),
                        SheetCell.text("Horas"),
                        SheetCell.text("Hecha"),
                    ),
                    listOf(
                        SheetCell.text("Revisión café ☕"),
                        SheetCell.number(12L),
                        SheetCell.boolean(true),
                    ),
                    listOf(
                        SheetCell.text("Con \"comillas\" y\nsalto"),
                        SheetCell.number(java.math.BigDecimal("3.50")),
                        SheetCell.boolean(false),
                    ),
                ),
            ),
            SheetData(
                name = "Otra hoja",
                rows = listOf(listOf(SheetCell.text("Vacía"), SheetCell.text(""))),
            ),
        )
        val out = ByteArrayOutputStream()
        XlsxCodec.write(out, sheets)
        val read = XlsxCodec.read(ByteArrayInputStream(out.toByteArray()))

        assertEquals(2, read.size)
        assertEquals("Tareas", read[0].name)
        assertEquals("Otra hoja", read[1].name)
        val rows = read[0].textRows()
        assertEquals("Título", rows[0][0])
        assertEquals("Revisión café ☕", rows[1][0])
        assertEquals("12", rows[1][1])
        assertEquals("1", rows[1][2])
        assertEquals("3.50", rows[2][1])
        assertEquals("0", rows[2][2])
        assertEquals("Con \"comillas\" y\nsalto", rows[2][0])
    }

    @Test fun formulaLikeTextIsStoredAsLiteralText() {
        val out = ByteArrayOutputStream()
        XlsxCodec.write(
            out,
            listOf(SheetData("Hoja1", listOf(listOf(SheetCell.text("=1+1"))))),
        )
        val bytes = out.toByteArray()
        val xml = zipEntry(bytes, "xl/worksheets/sheet1.xml")
        assertNotNull(xml)
        assertTrue(xml!!.contains("=1+1"))
        assertTrue(!xml.contains("<f>"))
        val read = XlsxCodec.read(ByteArrayInputStream(bytes))
        assertEquals("=1+1", read.single().textRows().single().single())
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyWorkbookIsRejected() {
        XlsxCodec.write(ByteArrayOutputStream(), emptyList())
    }

    @Test fun rejectsFilesThatAreNotZipContainers() {
        try {
            XlsxCodec.read(ByteArrayInputStream("no es un xlsx".toByteArray()))
            throw AssertionError("Expected an error")
        } catch (error: Exception) {
            assertTrue(error is IllegalArgumentException || error.message != null)
        }
    }

    @Test fun writesReadableFile() {
        val file = File.createTempFile("nexonote-", ".xlsx")
        try {
            XlsxCodec.write(file, listOf(SheetData("Datos", listOf(listOf(SheetCell.text("Hola"))))))
            assertTrue(file.length() > 0)
            assertEquals("Hola", XlsxCodec.read(file).single().textRows().single().single())
        } finally {
            file.delete()
        }
    }

    private fun zipEntry(bytes: ByteArray, name: String): String? {
        java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == name) return zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return null
    }
}
