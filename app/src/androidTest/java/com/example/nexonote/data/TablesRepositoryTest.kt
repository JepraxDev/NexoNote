package com.example.nexonote.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.nexonote.data.local.NexoDatabase
import com.example.nexonote.data.repository.LocalTableRepository
import com.example.nexonote.domain.model.ColumnInput
import com.example.nexonote.domain.model.ColumnType
import com.example.nexonote.domain.model.OptionInput
import com.example.nexonote.domain.model.TableFilter
import com.example.nexonote.domain.model.TableFilterOperator
import com.example.nexonote.domain.model.TableInput
import com.example.nexonote.domain.model.TableQuery
import com.example.nexonote.domain.model.TableSort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TablesRepositoryTest {
    private lateinit var database: NexoDatabase
    private lateinit var repository: LocalTableRepository
    private var now = 1_000_000L

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NexoDatabase::class.java,
        ).build()
        repository = LocalTableRepository(database) { now }
    }

    @After fun teardown() {
        database.close()
    }

    @Test fun createTableAddsPrimaryColumnAndKeepsEntry() = runBlocking {
        val tableId = repository.createTable(
            TableInput(name = "Inventario", description = "Equipos", color = "#006A6A")
        )
        val table = repository.findTable(tableId)!!
        assertEquals("Inventario", table.name)
        assertEquals("Equipos", table.description)
        assertEquals("TABLE", database.entryDao().observeAll().first().single().type)
        val columns = repository.observeColumns(tableId).first()
        assertEquals(1, columns.size)
        assertTrue(columns.single().primary)
        assertTrue(columns.single().required)
        assertEquals("Nombre", columns.single().name)
        assertEquals(ColumnType.SHORT_TEXT, columns.single().type)
    }

    @Test fun loadPageCanBeCalledFromMainThread() = runBlocking {
        val tableId = repository.createTable(TableInput("Desde interfaz"))
        withContext(Dispatchers.Main) {
            assertTrue(repository.loadPage(tableId, TableQuery(), 50, 0).rows.isEmpty())
        }
        val rowId = repository.createRow(tableId)
        withContext(Dispatchers.Main) {
            assertEquals(rowId, repository.loadPage(tableId, TableQuery(), 50, 0).rows.single().id)
        }
    }

    @Test fun rowsCellsOptionsFiltersSortAndPaginationWorkTogether() = runBlocking {
        val tableId = repository.createTable(TableInput("Activos"))
        val columns = repository.observeColumns(tableId).first()
        val nameColumn = columns.single()
        val quantityId = repository.addColumn(
            tableId,
            ColumnInput("Cantidad", ColumnType.INTEGER),
        )
        val statusId = repository.addColumn(
            tableId,
            ColumnInput("Estado", ColumnType.SELECT),
        )
        val ready = repository.addOption(statusId, OptionInput("Listo", "#4F6D3A"))
        repository.addOption(statusId, OptionInput("Pendiente", "#B3261E"))

        val laptop = repository.createRow(tableId)
        repository.updateCell(laptop, nameColumn.id, "Laptop")
        repository.updateCell(laptop, quantityId, "3")
        repository.updateCell(laptop, statusId, ready.toString())

        now += 1_000L
        val mouse = repository.createRow(tableId)
        repository.updateCell(mouse, nameColumn.id, "Ratón")
        repository.updateCell(mouse, quantityId, "1")

        val row = repository.findRow(laptop)!!
        assertEquals("Laptop", row.title)
        assertEquals("3", row.value(quantityId))
        assertEquals(ready.toString(), row.value(statusId))

        now += 1_000L
        val filtered = repository.loadPage(
            tableId,
            TableQuery(
                search = "listo",
                filters = listOf(TableFilter(quantityId, TableFilterOperator.GREATER, "2")),
                sort = TableSort(nameColumn.id, ascending = true),
            ),
            limit = 50,
            offset = 0,
        )
        assertEquals(listOf("Laptop"), filtered.rows.map { it.title })
        assertFalse(filtered.hasMore)

        val page = repository.loadPage(tableId, TableQuery(), limit = 1, offset = 0)
        assertEquals(1, page.rows.size)
        assertTrue(page.hasMore)
        val next = repository.loadPage(tableId, TableQuery(), limit = 1, offset = 1)
        assertEquals(1, next.rows.size)
        assertFalse(next.hasMore)
    }

    @Test fun columnTypeChangeConvertsCellsAndRejectsDataLoss() = runBlocking {
        val tableId = repository.createTable(TableInput("Conversión"))
        val notesId = repository.addColumn(tableId, ColumnInput("Cantidad", ColumnType.SHORT_TEXT))
        val first = repository.createRow(tableId)
        repository.updateCell(first, notesId, "12")
        val second = repository.createRow(tableId)
        repository.updateCell(second, notesId, "7")

        repository.changeColumnType(notesId, ColumnType.INTEGER)
        assertEquals("12", repository.findRow(first)!!.value(notesId))
        assertEquals("7", repository.findRow(second)!!.value(notesId))

        val timeId = repository.addColumn(tableId, ColumnInput("Momento", ColumnType.DATETIME))
        repository.updateCell(first, timeId, "1000001")
        val error = runCatching {
            repository.changeColumnType(timeId, ColumnType.DATE)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        val unchanged = repository.observeColumns(tableId).first().first { it.id == timeId }
        assertEquals(ColumnType.DATETIME, unchanged.type)
        assertEquals("1000001", repository.findRow(first)!!.value(timeId))
    }

    @Test fun invalidCellValueIsRejectedWithoutSilentlyPersisting() = runBlocking {
        val tableId = repository.createTable(TableInput("Validación"))
        val quantityId = repository.addColumn(tableId, ColumnInput("Cantidad", ColumnType.INTEGER))
        val rowId = repository.createRow(tableId)

        val error = runCatching {
            repository.updateCell(rowId, quantityId, "mucho")
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertNull(repository.findRow(rowId)!!.value(quantityId))
    }

    @Test fun duplicateTableCopiesStructureRowsAndOptionsIndependently() = runBlocking {
        val tableId = repository.createTable(TableInput("Proyectos"))
        val statusId = repository.addColumn(tableId, ColumnInput("Estado", ColumnType.STATUS))
        val optionId = repository.addOption(statusId, OptionInput("En curso", "#2F5D8A"))
        repository.updateColumn(statusId, ColumnInput("Estado", ColumnType.STATUS, defaultValue = optionId.toString()))
        val rowId = repository.createRow(tableId)
        repository.updateCell(rowId, repository.observeColumns(tableId).first().first().id, "NexoNote")
        repository.updateCell(rowId, statusId, optionId.toString())

        val copyId = repository.duplicateTable(tableId)
        assertNotEquals(tableId, copyId)
        val copyColumns = repository.observeColumns(copyId).first()
        assertEquals(2, copyColumns.size)
        val copiedStatus = copyColumns.first { it.name == "Estado" }
        assertEquals(listOf("En curso"), copiedStatus.options.map { it.name })
        val copiedRows = repository.loadPage(copyId, TableQuery(), 10, 0).rows
        assertEquals(1, copiedRows.size)
        assertEquals("NexoNote", copiedRows.single().title)
        assertEquals(copiedStatus.options.single().id.toString(), copiedRows.single().value(copiedStatus.id))
        assertEquals(copiedStatus.options.single().id.toString(), copiedStatus.defaultValue)
        val newCopiedRow = repository.createRow(copyId)
        assertEquals(copiedStatus.defaultValue, repository.findRow(newCopiedRow)!!.value(copiedStatus.id))

        repository.deleteTable(copyId)
        assertNull(repository.findTable(copyId))
        assertNotNull(repository.findTable(tableId))
    }
}
