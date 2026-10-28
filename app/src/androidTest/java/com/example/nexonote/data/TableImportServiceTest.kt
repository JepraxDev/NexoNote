package com.example.nexonote.data

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.nexonote.data.local.NexoDatabase
import com.example.nexonote.data.repository.LocalTableImportRepository
import com.example.nexonote.data.repository.LocalTableRepository
import com.example.nexonote.data.transfer.FileGateway
import com.example.nexonote.data.transfer.TableImportService
import com.example.nexonote.domain.model.ColumnType
import com.example.nexonote.domain.model.DuplicateStrategy
import com.example.nexonote.domain.model.ImportOptions
import com.example.nexonote.domain.model.TableQuery
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TableImportServiceTest {
    private lateinit var database: NexoDatabase
    private lateinit var tables: LocalTableRepository
    private lateinit var service: TableImportService
    private lateinit var files: FileGateway
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val workFiles = mutableListOf<File>()

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, NexoDatabase::class.java).build()
        tables = LocalTableRepository(database)
        files = FileGateway(context)
        service = TableImportService(files, LocalTableImportRepository(database))
    }

    @After fun teardown() {
        database.close()
        workFiles.forEach { it.delete() }
    }

    @Test fun importsCsvIntoNewTableWithDetectedTypes() = runBlocking {
        val parsed = parse(
            "inventario.csv",
            "Nombre;Precio;Comprado\r\n" +
                "Café ☕;3,50;sí\r\n" +
                "Té;2,25;no\r\n",
        )
        assertEquals(listOf("Nombre", "Precio", "Comprado"), parsed.headers)
        val preview = service.preview(parsed)
        assertEquals(2, preview.totalRows)
        assertEquals(ColumnType.DECIMAL, preview.detectedTypes[1])
        assertEquals(ColumnType.CHECKBOX, preview.detectedTypes[2])

        val result = service.import(
            parsed = parsed,
            mappings = service.mappingForNewTable(parsed),
            options = ImportOptions(skipInvalidRows = false),
            targetTableId = null,
            newTableName = "Inventario",
        )
        assertEquals(2, result.imported)
        assertEquals(0, result.skipped)
        assertNotNull(result.createdTableId)

        val tableId = result.createdTableId!!
        val table = tables.findTable(tableId)!!
        assertEquals("Inventario", table.name)
        val columns = tables.observeColumns(tableId).first()
        assertEquals(3, columns.size)
        assertTrue(columns.first().primary)
        assertEquals(ColumnType.DECIMAL, columns[1].type)
        assertEquals(ColumnType.CHECKBOX, columns[2].type)

        val page = tables.loadPage(tableId, TableQuery(), 50, 0)
        assertEquals(2, page.rows.size)
        val coffee = page.rows.first { it.title == "Café ☕" }
        assertEquals("3.5", coffee.cells[columns[1].id])
        assertEquals("1", coffee.cells[columns[2].id])
        val tea = page.rows.first { it.title == "Té" }
        assertEquals("2.25", tea.cells[columns[1].id])
        assertEquals("0", tea.cells[columns[2].id])
    }

    @Test fun duplicateStrategiesSkipUpdateOrAllow() = runBlocking {
        val csv = "Producto;Unidades\r\nCafé;2\r\nTé;1\r\n"
        val first = parse("stock.csv", csv)
        val created = service.import(
            first,
            service.mappingForNewTable(first),
            ImportOptions(skipInvalidRows = false),
            targetTableId = null,
            newTableName = "Stock",
        ).createdTableId!!
        val targetColumns = LocalTableImportRepository(database).columns(created)

        val second = parse("stock.csv", "Producto;Unidades\r\ncafé;5\r\nAzúcar;3\r\n")
        val mapped = service.autoMapping(second, targetColumns)
        val skip = service.import(
            second,
            mapped,
            ImportOptions(duplicateStrategy = DuplicateStrategy.SKIP, skipInvalidRows = false),
            targetTableId = created,
        )
        assertEquals(1, skip.imported)
        assertEquals(1, skip.skipped)
        assertEquals(3, tables.loadPage(created, TableQuery(), 50, 0).rows.size)

        val update = service.import(
            second,
            mapped,
            ImportOptions(duplicateStrategy = DuplicateStrategy.UPDATE, skipInvalidRows = false),
            targetTableId = created,
        )
        assertEquals(2, update.updated)
        val updated = tables.loadPage(created, TableQuery(), 50, 0).rows
            .first { it.title.equals("Café", ignoreCase = true) }
        assertEquals("5", updated.cells[targetColumns[1].id])

        val allow = service.import(
            parse("stock.csv", "Producto;Unidades\r\nCafé;9\r\n"),
            service.autoMapping(parse("stock.csv", "Producto;Unidades\r\nCafé;9\r\n"), targetColumns),
            ImportOptions(duplicateStrategy = DuplicateStrategy.ALLOW, skipInvalidRows = false),
            targetTableId = created,
        )
        assertEquals(1, allow.imported)
        assertEquals(4, tables.loadPage(created, TableQuery(), 50, 0).rows.size)
    }

    @Test fun repeatedPrimaryKeysInOneFileRespectSkipAndUpdate() = runBlocking {
        val first = parse("stock.csv", "Producto;Unidades\r\nTé;1\r\n")
        val created = service.import(
            first,
            service.mappingForNewTable(first),
            ImportOptions(skipInvalidRows = false),
            targetTableId = null,
            newTableName = "Repetidos",
        ).createdTableId!!
        val targetColumns = LocalTableImportRepository(database).columns(created)

        val repeated = parse("stock.csv", "Producto;Unidades\r\nCafé;2\r\nCafé;9\r\n")
        val mapped = service.autoMapping(repeated, targetColumns)
        val skip = service.import(
            repeated,
            mapped,
            ImportOptions(duplicateStrategy = DuplicateStrategy.SKIP, skipInvalidRows = false),
            targetTableId = created,
        )
        assertEquals(1, skip.imported)
        assertEquals(1, skip.skipped)
        val rowsAfterSkip = tables.loadPage(created, TableQuery(), 50, 0).rows
        assertEquals(2, rowsAfterSkip.size)
        assertEquals(1, rowsAfterSkip.count { it.title.equals("Café", ignoreCase = true) })

        val update = service.import(
            parse("stock.csv", "Producto;Unidades\r\nCafé;7\r\n"),
            mapped,
            ImportOptions(duplicateStrategy = DuplicateStrategy.UPDATE, skipInvalidRows = false),
            targetTableId = created,
        )
        assertEquals(1, update.updated)
        val updated = tables.loadPage(created, TableQuery(), 50, 0).rows
            .first { it.title.equals("Café", ignoreCase = true) }
        assertEquals("7", updated.cells[targetColumns[1].id])
        assertEquals(2, tables.loadPage(created, TableQuery(), 50, 0).rows.size)
    }

    @Test fun updateDoesNotEraseColumnsWithoutSourceMapping() = runBlocking {
        val initial = parse("notas.csv", "Producto;Unidades;Nota\r\nCafé;2;Reservado\r\n")
        val created = service.import(
            initial,
            service.mappingForNewTable(initial),
            ImportOptions(skipInvalidRows = false),
            targetTableId = null,
            newTableName = "Parcial",
        ).createdTableId!!
        val targetColumns = LocalTableImportRepository(database).columns(created)

        val partial = parse("notas.csv", "Producto;Unidades\r\nCafé;8\r\n")
        val result = service.import(
            partial,
            service.autoMapping(partial, targetColumns),
            ImportOptions(duplicateStrategy = DuplicateStrategy.UPDATE, skipInvalidRows = false),
            targetTableId = created,
        )
        assertEquals(1, result.updated)
        val row = tables.loadPage(created, TableQuery(), 50, 0).rows
            .first { it.title.equals("Café", ignoreCase = true) }
        assertEquals("8", row.cells[targetColumns[1].id])
        assertEquals("Reservado", row.cells[targetColumns[2].id])
    }

    @Test fun invalidRowsAbortTheWholeImportWhenSkippingIsDisabled() = runBlocking {
        val parsed = parse(
            "numeros.csv",
            "Nombre;Cantidad\r\nVálida;10\r\nRota;muchas\r\n",
        )
        val mappings = service.mappingForNewTable(parsed).mapIndexed { index, mapping ->
            if (index == 1) mapping.copy(targetType = ColumnType.INTEGER) else mapping
        }
        val result = service.import(
            parsed,
            mappings,
            ImportOptions(skipInvalidRows = false),
            targetTableId = null,
            newTableName = "Números",
        )
        assertEquals(0, result.imported)
        assertEquals(1, result.errors.size)
        assertNull(result.createdTableId)
        // No table and no rows may be left behind.
        assertTrue(tables.tables.first().isEmpty())
    }

    @Test fun skipInvalidRowsImportsTheValidSubsetAndReportsErrors() = runBlocking {
        val parsed = parse(
            "numeros.csv",
            "Nombre;Cantidad\r\nVálida;10\r\nRota;muchas\r\nOtra;7\r\n",
        )
        val mappings = service.mappingForNewTable(parsed).mapIndexed { index, mapping ->
            if (index == 1) mapping.copy(targetType = ColumnType.INTEGER) else mapping
        }
        val result = service.import(
            parsed,
            mappings,
            ImportOptions(skipInvalidRows = true),
            targetTableId = null,
            newTableName = "Parcial",
        )
        assertEquals(2, result.imported)
        assertEquals(1, result.errors.size)
        assertEquals(3, result.errors.single().rowNumber)
        val tableId = result.createdTableId!!
        assertEquals(2, tables.loadPage(tableId, TableQuery(), 50, 0).rows.size)
    }

    @Test fun formulaPrefixesFromExportAreRemovedAgain() = runBlocking {
        val parsed = parse("formulas.csv", "Nombre\r\n'=2+2\r\n'+34 600 000 000\r\n")
        val result = service.import(
            parsed,
            service.mappingForNewTable(parsed),
            ImportOptions(skipInvalidRows = false),
            targetTableId = null,
            newTableName = "Fórmulas",
        )
        val rows = tables.loadPage(result.createdTableId!!, TableQuery(), 50, 0).rows
        assertEquals(
            setOf("=2+2", "+34 600 000 000"),
            rows.map { it.title }.toSet(),
        )
    }

    @Test fun missingFileIsReportedWithoutSideEffects() = runBlocking {
        try {
            service.parse(Uri.fromFile(File(context.cacheDir, "no-existe.csv")))
            fail("Expected a read error")
        } catch (error: Exception) {
            assertTrue(error.message!!.isNotBlank())
        }
        assertTrue(tables.tables.first().isEmpty())
    }

    private fun parse(name: String, content: String): com.example.nexonote.domain.model.ParsedImport {
        val file = File(context.cacheDir, "import-${System.nanoTime()}-$name")
            .apply { writeText(content, Charsets.UTF_8) }
        workFiles += file
        return runBlocking { service.parse(Uri.fromFile(file), fileName = name) }
    }
}
