package com.example.nexonote.data.transfer

import com.example.nexonote.domain.export.NoteTextFormatter
import com.example.nexonote.domain.export.SheetData
import com.example.nexonote.domain.export.TableSheetBuilder
import com.example.nexonote.domain.export.TaskSheetBuilder
import com.example.nexonote.domain.export.TransferFormats
import com.example.nexonote.domain.model.ExportOutcome
import com.example.nexonote.domain.model.ExportSection
import com.example.nexonote.domain.model.NoteExportFormat
import com.example.nexonote.domain.model.SpreadsheetFormat
import com.example.nexonote.domain.repository.ExportDataRepository
import java.io.BufferedWriter
import java.io.FilterOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coordinates safe exports. Files are always written to a stream supplied by the caller (SAF or
 * a cache file), never to a hard-coded private path, and CSV cells are formula-guarded.
 */
class ExportService(
    private val data: ExportDataRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val pdf = NotePdfRenderer()

    suspend fun exportNotes(
        ids: Set<Long>,
        format: NoteExportFormat,
        out: OutputStream,
    ): ExportOutcome = withContext(Dispatchers.IO) {
        val all = data.notes()
        val selected = all.filter { ids.isEmpty() || it.id in ids }
        require(selected.isNotEmpty()) { "Selecciona al menos una nota." }
        val folders = data.folders().associate { it.id to it.displayName }
        val categories = data.categories().associate { it.id to it.displayName }
        val counter = CountingOutputStream(out)
        when (format) {
            NoteExportFormat.PDF -> pdf.render(selected, folders, categories, counter)
            NoteExportFormat.TXT -> {
                val writer = BufferedWriter(OutputStreamWriter(counter, Charsets.UTF_8))
                selected.forEachIndexed { index, note ->
                    if (index > 0) writer.write("\n\n" + "—".repeat(24) + "\n\n")
                    writer.write(NoteTextFormatter.plainText(note, note.folderId?.let(folders::get), note.categoryId?.let(categories::get)))
                }
                writer.flush()
            }
            NoteExportFormat.MARKDOWN -> {
                val writer = BufferedWriter(OutputStreamWriter(counter, Charsets.UTF_8))
                selected.forEachIndexed { index, note ->
                    if (index > 0) writer.write("\n\n---\n\n")
                    writer.write(NoteTextFormatter.markdown(note, note.folderId?.let(folders::get), note.categoryId?.let(categories::get)))
                }
                writer.flush()
            }
        }
        ExportOutcome(
            fileName = suggestedFileName(ExportSection.NOTES, format.extension, selected.singleOrNull()?.title),
            mimeType = format.mimeType,
            bytes = counter.count,
        )
    }

    suspend fun exportTasks(
        ids: Set<Long>,
        format: SpreadsheetFormat,
        out: OutputStream,
    ): ExportOutcome = withContext(Dispatchers.IO) {
        val all = data.tasks()
        val selected = all.filter { ids.isEmpty() || it.id in ids }
        require(selected.isNotEmpty()) { "Selecciona al menos una tarea." }
        val categories = data.categories().associate { it.id to it.displayName }
        val folders = data.folders().associate { it.id to it.displayName }
        val sheet = SheetData("Tareas", TaskSheetBuilder.rows(selected, categories, folders))
        val counter = CountingOutputStream(out)
        when (format) {
            SpreadsheetFormat.CSV -> writeCsv(sheet, counter)
            SpreadsheetFormat.XLSX -> writeXlsx(listOf(sheet), counter)
        }
        ExportOutcome(
            fileName = suggestedFileName(ExportSection.TASKS, format.extension),
            mimeType = format.mimeType,
            bytes = counter.count,
        )
    }

    suspend fun exportTables(
        ids: Set<Long>,
        format: SpreadsheetFormat,
        out: OutputStream,
    ): ExportOutcome = withContext(Dispatchers.IO) {
        val all = data.tables()
        val selected = all.filter { ids.isEmpty() || it.id in ids }
        require(selected.isNotEmpty()) { "Selecciona al menos una tabla." }
        require(format == SpreadsheetFormat.XLSX || selected.size == 1) {
            "CSV solo admite una tabla por archivo. Elige Excel (XLSX) para exportar varias."
        }
        val counter = CountingOutputStream(out)
        if (format == SpreadsheetFormat.XLSX) {
            val sheets = selected.map { table ->
                TableSheetBuilder.sheet(table, data.columns(table.id), data.rows(table.id))
            }
            writeXlsx(sheets, counter)
        } else {
            val table = selected.single()
            val sheet = TableSheetBuilder.sheet(table, data.columns(table.id), data.rows(table.id))
            writeCsv(sheet, counter)
        }
        ExportOutcome(
            fileName = suggestedFileName(
                ExportSection.TABLES,
                format.extension,
                selected.singleOrNull()?.displayName,
            ),
            mimeType = format.mimeType,
            bytes = counter.count,
        )
    }

    fun suggestedFileName(section: ExportSection, extension: String, itemName: String? = null): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date(clock()))
        val base = when (section) {
            ExportSection.NOTES -> itemName?.takeIf { it.isNotBlank() }?.let { "nota-$it" } ?: "notas"
            ExportSection.TASKS -> "tareas"
            ExportSection.TABLES -> itemName?.takeIf { it.isNotBlank() } ?: "tablas"
        }
        return TransferFormats.uniqueFileName("$base-$stamp", extension, emptySet())
    }

    private fun writeCsv(sheet: SheetData, out: OutputStream) {
        val writer = BufferedWriter(OutputStreamWriter(out, Charsets.UTF_8))
        writer.write("\uFEFF")
        val rows = sheet.rows.map { row ->
            row.map { cell -> FormulaGuard.escape(TableSheetBuilder.csvValue(cell)) }
        }
        CsvCodec.writeTo(writer, rows)
    }

    private fun writeXlsx(sheets: List<SheetData>, out: OutputStream) {
        XlsxCodec.write(out, sheets)
    }

    /** The stream passed to a SAF output stream cannot be queried, so count while writing. */

    private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
        var count: Long = 0
            private set

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            out.write(buffer, offset, length)
            count += length
        }

        override fun write(byte: Int) {
            out.write(byte)
            count++
        }

        override fun close() {
            // Let the caller own the SAF stream; flushing is enough.
            flush()
        }
    }
}
